package org.acornmc.drmap.picture;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class PictureMeta {

    public static void sendAuthor(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String author = PictureMeta.getAuthor(container, plugin);
        if (author != null) {
            String message = Lang.INFO_AUTHOR.replace("{author}", author);
            Lang.send(sender, message);
        }
    }

    public static void sendCreation(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String creation = PictureMeta.getCreationString(container, plugin);
        if (creation != null) {
            String message = Lang.INFO_CREATION.replace("{creation}", creation);
            Lang.send(sender, message);
        }
    }

    public static void sendPart(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        int[] part = PictureMeta.getParts(container, plugin);
        if (part != null) {
            String message = Lang.INFO_PART
                    .replace("{this-x}", String.valueOf(part[0]))
                    .replace("{this-y}", String.valueOf(part[1]))
                    .replace("{max-x}", String.valueOf(part[2]))
                    .replace("{max-y}", String.valueOf(part[3]));
            Lang.send(sender, message);
        }
    }

    public static String getAuthor(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyAuthor = new NamespacedKey(plugin, "drmap-author");
        String author = container.get(keyAuthor, PersistentDataType.STRING);
        if (author == null) {
            return null;
        }
        UUID authorUUID = UUID.fromString(author);
        return Bukkit.getOfflinePlayer(authorUUID).getName();
    }

    public static String getCreationString(PersistentDataContainer container, DrMap plugin) {
        try {
            Long creation = getCreationLong(container, plugin);
            if (creation == null) {
                return null;
            }
            Date date = new Date(creation * 1000L);
            SimpleDateFormat dateFormat = new SimpleDateFormat(Config.TIME_FORMAT);
            return dateFormat.format(date);
        } catch (Exception ignored) {}
        return null;
    }

    public static Long getCreationLong(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyCreation = new NamespacedKey(plugin, "drmap-creation");
        return container.get(keyCreation, PersistentDataType.LONG);
    }

    public static int[] getParts(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyPart = new NamespacedKey(plugin, "drmap-part");
        return container.get(keyPart, PersistentDataType.INTEGER_ARRAY);
    }
}
