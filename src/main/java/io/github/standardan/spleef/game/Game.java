package io.github.standardan.spleef.game;

import io.github.standardan.spleef.SpleefPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The whole Spleef match lifecycle for a single arena, modelled as an explicit
 * state machine (WAITING -> COUNTDOWN -> PLAYING -> ENDING -> WAITING).
 *
 * Implementation notes:
 *  - Eliminations are detected in a slow repeating task (every 0.5s), NOT in
 *    PlayerMoveEvent, which fires dozens of times per second per player.
 *  - Every player's inventory/location/gamemode is snapshotted on join and
 *    restored on exit, so the minigame never destroys their survival state.
 */
public final class Game {

    private final SpleefPlugin plugin;

    // Arena definition (loaded from config).
    private Location lobby;
    private final List<Location> spawns = new ArrayList<>();
    private Cuboid floor;
    private Material floorMaterial = Material.SNOW_BLOCK;
    private double loseY = -64;
    private int minPlayers = 2;
    private int maxPlayers = 8;
    private int countdownSeconds = 10;

    // Runtime state.
    private GameState state = GameState.WAITING;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final java.util.Map<UUID, PlayerSnapshot> snapshots = new java.util.HashMap<>();
    private final BossBar bossBar = BossBar.bossBar(Component.text("Spleef"), 1f,
            BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
    private int countdown;
    private int countdownTaskId = -1;
    private int gameTaskId = -1;

    public Game(SpleefPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    public void loadFromConfig() {
        var cfg = plugin.getConfig();
        minPlayers = cfg.getInt("min-players", 2);
        maxPlayers = cfg.getInt("max-players", 8);
        countdownSeconds = cfg.getInt("countdown-seconds", 10);
        Material mat = Material.matchMaterial(cfg.getString("floor-material", "SNOW_BLOCK"));
        floorMaterial = mat != null ? mat : Material.SNOW_BLOCK;

        lobby = cfg.getLocation("arena.lobby");
        spawns.clear();
        List<?> raw = cfg.getList("arena.spawns");
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof Location loc) {
                    spawns.add(loc);
                }
            }
        }
        Location c1 = cfg.getLocation("arena.floor.corner1");
        Location c2 = cfg.getLocation("arena.floor.corner2");
        floor = (c1 != null && c2 != null) ? new Cuboid(c1, c2) : null;

        if (cfg.isSet("arena.lose-y")) {
            loseY = cfg.getDouble("arena.lose-y");
        } else if (floor != null) {
            loseY = floor.minY() - 3.0;
        }
    }

    public boolean isSetUp() {
        return lobby != null && !spawns.isEmpty() && floor != null;
    }

    public GameState getState() {
        return state;
    }

    public boolean isInGame(UUID id) {
        return players.contains(id);
    }

    public boolean isFloorBlock(Block block) {
        return state == GameState.PLAYING && floor != null
                && floor.contains(block.getLocation()) && block.getType() == floorMaterial;
    }

    // --- joining / leaving --------------------------------------------------

    public void join(Player player) {
        if (!isSetUp()) {
            player.sendMessage(Component.text("The Spleef arena isn't set up yet.", NamedTextColor.RED));
            return;
        }
        if (state == GameState.PLAYING || state == GameState.ENDING) {
            player.sendMessage(Component.text("A match is already in progress.", NamedTextColor.RED));
            return;
        }
        UUID id = player.getUniqueId();
        if (players.contains(id)) {
            player.sendMessage(Component.text("You're already in the queue.", NamedTextColor.RED));
            return;
        }
        if (players.size() >= maxPlayers) {
            player.sendMessage(Component.text("The arena is full.", NamedTextColor.RED));
            return;
        }

        snapshots.put(id, PlayerSnapshot.capture(player));
        players.add(id);
        preparePlayer(player, GameMode.ADVENTURE);
        player.teleport(lobby);
        player.showBossBar(bossBar);
        broadcast(Component.text(player.getName() + " joined (" + players.size() + "/" + maxPlayers + ")",
                NamedTextColor.YELLOW));

        if (state == GameState.WAITING && players.size() >= minPlayers) {
            startCountdown();
        }
    }

    public void leave(Player player) {
        handleQuit(player.getUniqueId());
        player.sendMessage(Component.text("You left Spleef.", NamedTextColor.GRAY));
    }

    /** Remove a player from the match (also called when they disconnect). */
    public void handleQuit(UUID id) {
        if (!players.remove(id)) {
            return;
        }
        alive.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.hideBossBar(bossBar);
            PlayerSnapshot snap = snapshots.get(id);
            if (snap != null) {
                snap.restore(player);
            }
        }
        snapshots.remove(id);

        if (state == GameState.COUNTDOWN && players.size() < minPlayers) {
            cancelCountdown();
        } else if (state == GameState.PLAYING) {
            checkWin();
        }
    }

    // --- countdown ----------------------------------------------------------

    private void startCountdown() {
        state = GameState.COUNTDOWN;
        countdown = countdownSeconds;
        bossBar.color(BossBar.Color.YELLOW);
        countdownTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (players.size() < minPlayers) {
                cancelCountdown();
                return;
            }
            if (countdown <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
                startGame();
                return;
            }
            bossBar.name(Component.text("Starting in " + countdown + "s", NamedTextColor.YELLOW));
            bossBar.progress(Math.max(0f, Math.min(1f, (float) countdown / countdownSeconds)));
            countdown--;
        }, 0L, 20L).getTaskId();
    }

    private void cancelCountdown() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
        state = GameState.WAITING;
        bossBar.name(Component.text("Waiting for players", NamedTextColor.GREEN));
        bossBar.color(BossBar.Color.GREEN);
        bossBar.progress(1f);
    }

    // --- playing ------------------------------------------------------------

    public void startGame() {
        state = GameState.PLAYING;
        alive.clear();
        alive.addAll(players);

        if (floor.getWorld() != null) {
            floor.fill(floor.getWorld(), floorMaterial);
        }

        int i = 0;
        ItemStack shovel = makeShovel();
        List<UUID> ordered = new ArrayList<>(players);
        for (UUID id : ordered) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            preparePlayer(p, GameMode.SURVIVAL);
            p.teleport(spawns.get(i % spawns.size()));
            p.getInventory().addItem(shovel.clone());
            p.showTitle(Title.title(Component.text("Spleef!", NamedTextColor.AQUA),
                    Component.text("Dig the floor out from under everyone", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
            i++;
        }
        bossBar.color(BossBar.Color.RED);
        bossBar.name(Component.text("Players left: " + alive.size(), NamedTextColor.RED));

        gameTaskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tickGame, 10L, 10L).getTaskId();
    }

    private void tickGame() {
        if (state != GameState.PLAYING) {
            return;
        }
        // Copy to avoid mutating the set while iterating.
        for (UUID id : new ArrayList<>(alive)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                eliminate(id, false);
            } else if (p.getLocation().getY() < loseY) {
                eliminate(id, true);
            }
        }
        bossBar.name(Component.text("Players left: " + alive.size(), NamedTextColor.RED));
        checkWin();
    }

    private void eliminate(UUID id, boolean announce) {
        if (!alive.remove(id)) {
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            p.setGameMode(GameMode.SPECTATOR);
            p.teleport(lobby);
            if (announce) {
                broadcast(Component.text(p.getName() + " was eliminated! (" + alive.size() + " left)",
                        NamedTextColor.GOLD));
            }
        }
    }

    private void checkWin() {
        if (state != GameState.PLAYING || alive.size() > 1) {
            return;
        }
        UUID winnerId = alive.stream().findFirst().orElse(null);
        endGame(winnerId);
    }

    private void endGame(UUID winnerId) {
        state = GameState.ENDING;
        if (gameTaskId != -1) {
            Bukkit.getScheduler().cancelTask(gameTaskId);
            gameTaskId = -1;
        }
        Player winner = winnerId != null ? Bukkit.getPlayer(winnerId) : null;
        Component top = winner != null
                ? Component.text(winner.getName() + " wins!", NamedTextColor.GOLD)
                : Component.text("Draw - no winner", NamedTextColor.GRAY);
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(Title.title(top, Component.empty()));
            }
        }
        // Brief pause on the results, then reset back to a fresh lobby.
        Bukkit.getScheduler().runTaskLater(plugin, this::reset, 100L); // 5s
    }

    /** Restore everyone and return the arena to a clean WAITING state. */
    public void reset() {
        for (UUID id : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bossBar);
                PlayerSnapshot snap = snapshots.get(id);
                if (snap != null) {
                    snap.restore(p);
                }
            }
        }
        players.clear();
        alive.clear();
        snapshots.clear();
        if (floor != null && floor.getWorld() != null) {
            floor.fill(floor.getWorld(), floorMaterial); // rebuild the floor for next time
        }
        state = GameState.WAITING;
        bossBar.name(Component.text("Waiting for players", NamedTextColor.GREEN));
        bossBar.color(BossBar.Color.GREEN);
        bossBar.progress(1f);
    }

    /** Admin force-start. */
    public void forceStart() {
        if ((state == GameState.WAITING || state == GameState.COUNTDOWN) && !players.isEmpty()) {
            if (countdownTaskId != -1) {
                Bukkit.getScheduler().cancelTask(countdownTaskId);
                countdownTaskId = -1;
            }
            startGame();
        }
    }

    /** Called on plugin disable - restore anyone mid-game. */
    public void shutdown() {
        if (countdownTaskId != -1) Bukkit.getScheduler().cancelTask(countdownTaskId);
        if (gameTaskId != -1) Bukkit.getScheduler().cancelTask(gameTaskId);
        reset();
    }

    // --- helpers ------------------------------------------------------------

    private void preparePlayer(Player p, GameMode mode) {
        p.getInventory().clear();
        p.setGameMode(mode);
        p.setFoodLevel(20);
        p.setHealth(Math.min(20.0, p.getMaxHealth()));
        p.setFireTicks(0);
    }

    private ItemStack makeShovel() {
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();
        meta.displayName(Component.text("Spleef Shovel", NamedTextColor.AQUA));
        meta.setUnbreakable(true);
        Enchantment efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        if (efficiency != null) {
            meta.addEnchant(efficiency, 5, true);
        }
        shovel.setItemMeta(meta);
        return shovel;
    }

    private void broadcast(Component message) {
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }
}
