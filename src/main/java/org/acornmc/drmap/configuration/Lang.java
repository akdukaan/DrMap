package org.acornmc.drmap.configuration;

import com.google.common.base.Throwables;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class Lang {
    private static YamlConfiguration config;

    public static String ACTION_NO_PERMISSION = "&4You do not have permission for that action.";
    public static String COMMAND_NO_PERMISSION = "&4You do not have permission for that command.";
    public static String ERROR_DOWNLOADING = "&4Could not download image.";
    public static String IMAGE_CREATED = "&aDrMap created.";
    public static String INFO_AUTHOR = "&aThis map was created by {author}.";
    public static String INFO_CREATION = "&aThis map was created on {creation}.";
    public static String INFO_PART = "&aThis map is part ({this-x} {this-y}) of ({max-x} {max-y}).";
    public static String INFO_SOURCE = "&aThis image is from {source}.";
    public static String MUST_HAVE_MAP = "&4You must have an empty map in your inventory.";
    public static String NOT_ENOUGH_MAPS = "&4You do not have {required} empty maps for an image of that size.";
    public static String NOT_PLAYER = "&4This command can only be executed by players.";
    public static String NOT_DRMAP = "&4You must hold a DrMap to use that command.";
    public static String CARTOGRAPHY_NO_PERMISSION = "&4You do not have permission to manipulate DrMaps in a cartography table.";
    public static String IMAGE_CREATED_BY_PLAYER ="{player} has created a new DrMap image.";
    private static void init() {
        COMMAND_NO_PERMISSION = getString("command-no-permission", COMMAND_NO_PERMISSION);
        ACTION_NO_PERMISSION = getString("action-no-permission", ACTION_NO_PERMISSION);
        ERROR_DOWNLOADING = getString("error", ERROR_DOWNLOADING);
        IMAGE_CREATED = getString("image-created", IMAGE_CREATED);
        MUST_HAVE_MAP = getString("must-have-map", MUST_HAVE_MAP);
        NOT_PLAYER = getString("not-player", NOT_PLAYER);
        NOT_DRMAP = getString("not-drmap", NOT_DRMAP);
        INFO_AUTHOR = getString("info-author", INFO_AUTHOR);
        INFO_CREATION = getString("info-creation", INFO_CREATION);
        INFO_PART = getString("info-part", INFO_PART);
        INFO_SOURCE = getString("info-source", INFO_SOURCE);
        NOT_ENOUGH_MAPS = getString("not-enough-maps", NOT_ENOUGH_MAPS);
        CARTOGRAPHY_NO_PERMISSION = getString("cartography-no-permission", CARTOGRAPHY_NO_PERMISSION);
    }

    // ############################  DO NOT EDIT BELOW THIS LINE  ############################

    /**
     * Reload the language file
     */
    public static void reload(Plugin plugin) {
        File configFile = new File(plugin.getDataFolder(), Config.LANGUAGE_FILE);
        config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException ignore) {
        } catch (InvalidConfigurationException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not load " + Config.LANGUAGE_FILE + ", please correct your syntax errors", ex);
            throw Throwables.propagate(ex);
        }
        config.options().header("This is the main language file for " + plugin.getName());
        config.options().copyDefaults(true);

        Lang.init();

        try {
            config.save(configFile);
        } catch (IOException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Could not save " + configFile, ex);
        }
    }

    private static String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path, config.getString(path));
    }

    /**
     * Sends a message to a recipient
     *
     * @param recipient Recipient of message
     * @param message   Message to send
     */
    public static void sendMessage(CommandSender recipient, String message) {
        if (recipient == null) return;
        for (String part : message.split("\n")) {
            sendPart(recipient, part);
        }
    }

    public static void sendPart(CommandSender recipient, String message) {
        if (message == null) return;
        message = ChatColor.translateAlternateColorCodes('&', message);
        if (ChatColor.stripColor(message).isEmpty()) return;

        if (message.contains("§")) {
            recipient.sendMessage(message);
            return;
        }
        if (!(recipient instanceof Audience)) {
            recipient.sendMessage(message);
            return;
        }
        MiniMessage mm = MiniMessage.miniMessage();
        Component component = mm.deserialize(message);
        Audience.audience((Audience) recipient).sendMessage(component);
    }
}