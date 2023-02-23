package org.acornmc.drmap;

import org.acornmc.drmap.command.CommandDrmap;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.listener.BukkitListener;
import org.acornmc.drmap.picture.PictureManager;
import org.apache.commons.lang.Validate;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;

public final class DrMap extends JavaPlugin {

    private static DrMap instance;

    public DrMap() {
        instance = this;
    }

    public static DrMap getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        Config.reload(this);
        Lang.reload(this);

        PictureManager.INSTANCE.loadPictures();
        getServer().getPluginManager().registerEvents(new BukkitListener(this), this);
        getCommand("drmap").setExecutor(new CommandDrmap(this));

        new Metrics(this, 9840);
    }

    public Executor getMainThreadExecutor() {
        return command -> {
            Validate.notNull(command, "Command cannot be null");
            this.getServer().getScheduler().runTask(this, command);
        };
    }
}
