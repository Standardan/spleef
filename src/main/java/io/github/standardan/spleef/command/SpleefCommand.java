package io.github.standardan.spleef.command;

import io.github.standardan.spleef.SpleefPlugin;
import io.github.standardan.spleef.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player commands (join/leave) and admin commands (start/stop + arena setup).
 */
public final class SpleefCommand implements CommandExecutor, TabCompleter {

    private final SpleefPlugin plugin;
    private final Game game;

    public SpleefCommand(SpleefPlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Spleef is for players.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("/spleef <join|leave>", NamedTextColor.GRAY));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "join" -> {
                if (!player.hasPermission("spleef.play")) {
                    deny(player);
                } else {
                    game.join(player);
                }
            }
            case "leave" -> game.leave(player);
            case "start" -> {
                if (admin(player)) {
                    game.forceStart();
                    player.sendMessage(Component.text("Force-started.", NamedTextColor.GREEN));
                }
            }
            case "stop" -> {
                if (admin(player)) {
                    game.reset();
                    player.sendMessage(Component.text("Match reset.", NamedTextColor.GREEN));
                }
            }
            case "setup" -> {
                if (admin(player)) {
                    setup(player, args);
                }
            }
            default -> player.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void setup(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                    "/spleef setup <lobby|addspawn|clearspawns|floor1|floor2|losey>", NamedTextColor.GRAY));
            return;
        }
        var cfg = plugin.getConfig();
        Location loc = player.getLocation();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "lobby" -> {
                cfg.set("arena.lobby", loc);
                confirm(player, "Lobby set.");
            }
            case "addspawn" -> {
                List<Location> spawns = readSpawns(cfg);
                spawns.add(loc);
                cfg.set("arena.spawns", spawns);
                confirm(player, "Added spawn #" + spawns.size() + ".");
            }
            case "clearspawns" -> {
                cfg.set("arena.spawns", new ArrayList<Location>());
                confirm(player, "Cleared all spawns.");
            }
            case "floor1" -> {
                cfg.set("arena.floor.corner1", loc);
                confirm(player, "Floor corner 1 set.");
            }
            case "floor2" -> {
                cfg.set("arena.floor.corner2", loc);
                confirm(player, "Floor corner 2 set.");
            }
            case "losey" -> {
                cfg.set("arena.lose-y", loc.getY());
                confirm(player, "Lose-Y set to " + (int) loc.getY() + ".");
            }
            default -> player.sendMessage(Component.text("Unknown setup option.", NamedTextColor.RED));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Location> readSpawns(org.bukkit.configuration.file.FileConfiguration cfg) {
        List<Location> spawns = new ArrayList<>();
        List<?> raw = cfg.getList("arena.spawns");
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof Location l) {
                    spawns.add(l);
                }
            }
        }
        return spawns;
    }

    private void confirm(Player player, String msg) {
        plugin.saveConfig();
        game.loadFromConfig();
        player.sendMessage(Component.text(msg, NamedTextColor.GREEN)
                .append(Component.text(game.isSetUp() ? "  Arena ready!" : "  (arena not complete yet)",
                        NamedTextColor.GRAY)));
    }

    private boolean admin(Player player) {
        if (!player.hasPermission("spleef.admin")) {
            deny(player);
            return false;
        }
        return true;
    }

    private void deny(Player player) {
        player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "start", "stop", "setup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return List.of("lobby", "addspawn", "clearspawns", "floor1", "floor2", "losey").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
