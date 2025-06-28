package org.acornmc.drmap.configuration;

import com.google.common.base.Throwables;
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

    public static String ACTION_NO_PERMISSION = "<red>You do not have permission for that action.";
    public static String COMMAND_NO_PERMISSION = "<red>You do not have permission for that command.";
    public static String ERROR_DOWNLOADING = "<red>Could not download image.";
    public static String IMAGE_CREATED = "<green>DrMap created.";
    public static String INFO_AUTHOR = "<green>This map was created by {author}.";
    public static String INFO_CREATION = "<green>This map was created on {creation}.";
    public static String INFO_PART = "<green>This map is part ({this-x} {this-y}) of ({max-x} {max-y}).";
    public static String INFO_SOURCE = "<green>This image is from {source}.";
    public static String MUST_HAVE_MAP = "<red>You must have an empty map in your inventory.";
    public static String NOT_ENOUGH_MAPS = "<red>You do not have {required} empty maps for an image of that size.";
    public static String NOT_PLAYER = "<red>This command can only be executed by players.";
    public static String NOT_DRMAP = "<red>You must hold a DrMap to use that command.";
    public static String CARTOGRAPHY_NO_PERMISSION = "<red>You do not have permission to manipulate DrMaps in a cartography table.";
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
        if (message == null) return;
        if (message.isEmpty()) return;

        // Format it into a component and send it
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            MiniMessage mm = MiniMessage.miniMessage();
            Component component = mm.deserialize(message);
            recipient.sendMessage(component);
        }

        // If no minimessage, send with legacy color translation
        catch (ClassNotFoundException e) {
            for (String part : message.split("\n")) {
                part = ChatColor.translateAlternateColorCodes('&', part);
                if (ChatColor.stripColor(part).isEmpty()) return;
                recipient.sendMessage(part);
            }
        }
    }
}