package io.github.standardan.spleef.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Captures a player's state before they enter a match so we can restore it
 * exactly when they leave - never trash someone's inventory or location.
 */
public final class PlayerSnapshot {

    private final Location location;
    private final ItemStack[] inventory;
    private final GameMode gameMode;
    private final int foodLevel;
    private final double health;

    private PlayerSnapshot(Player player) {
        this.location = player.getLocation();
        this.inventory = player.getInventory().getContents();
        this.gameMode = player.getGameMode();
        this.foodLevel = player.getFoodLevel();
        this.health = player.getHealth();
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    public void restore(Player player) {
        player.getInventory().setContents(inventory);
        player.setGameMode(gameMode);
        player.setFoodLevel(foodLevel);
        player.setHealth(Math.max(1.0, health));
        player.teleport(location);
    }
}
