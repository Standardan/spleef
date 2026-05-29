package io.github.standardan.spleef.game;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Enforces the rules of the arena: only the floor may be broken (and it drops
 * nothing), no placing, no hunger/fall damage, no dropping the shovel. Players
 * who aren't in the match are unaffected.
 */
public final class GameListener implements Listener {

    private final Game game;

    public GameListener(Game game) {
        this.game = game;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!game.isInGame(player.getUniqueId())) {
            return;
        }
        if (game.getState() != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }
        if (game.isFloorBlock(event.getBlock())) {
            event.setDropItems(false); // no snowballs/clutter
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (game.isInGame(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && game.isInGame(player.getUniqueId())) {
            event.setCancelled(true); // elimination is by falling past the lose-Y, not damage
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && game.isInGame(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.isInGame(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.handleQuit(event.getPlayer().getUniqueId());
    }
}
