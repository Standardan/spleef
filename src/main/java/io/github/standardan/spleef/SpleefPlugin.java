package io.github.standardan.spleef;

import io.github.standardan.spleef.command.SpleefCommand;
import io.github.standardan.spleef.game.Game;
import io.github.standardan.spleef.game.GameListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class SpleefPlugin extends JavaPlugin {

    private Game game;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        game = new Game(this);
        getServer().getPluginManager().registerEvents(new GameListener(game), this);

        SpleefCommand handler = new SpleefCommand(this, game);
        PluginCommand command = Objects.requireNonNull(getCommand("spleef"), "spleef missing from plugin.yml");
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getLogger().info("Spleef enabled.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown(); // restore anyone mid-match
        }
    }
}
