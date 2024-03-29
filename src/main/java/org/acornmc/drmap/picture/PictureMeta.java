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
        String author = PictureMeta.getAuthorUsername(container, plugin);
        if (author != null) {
            String message = Lang.INFO_AUTHOR.replace("{author}", author);
            Lang.sendMessage(sender, message);
        }
    }

    public static void sendCreation(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String creation = PictureMeta.getCreationString(container);
        if (creation != null) {
            String message = Lang.INFO_CREATION.replace("{creation}", creation);
            Lang.sendMessage(sender, message);
        }
    }

    public static void sendPart(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        int[] part = PictureMeta.getParts(container);
        if (part != null) {
            String message = Lang.INFO_PART
                    .replace("{this-x}", String.valueOf(part[0]))
                    .replace("{this-y}", String.valueOf(part[1]))
                    .replace("{max-x}", String.valueOf(part[2]))
                    .replace("{max-y}", String.valueOf(part[3]));
            Lang.sendMessage(sender, message);
        }
    }

    public static void sendSource(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String source = PictureMeta.getSource(container);
        if (source != null) {
            String message = Lang.INFO_SOURCE.replace("{source}", source);
            Lang.sendMessage(sender, message);
        }
    }

    public static String getAuthorUsername(PersistentDataContainer container, DrMap plugin) {
        String author = getAuthorUUIDString(container);
        if (author == null) {
            return null;
        }
        UUID authorUUID = UUID.fromString(author);
        return Bukkit.getOfflinePlayer(authorUUID).getName();
    }

    public static String getAuthorUUIDString(PersistentDataContainer container) {
        NamespacedKey keyAuthor = new NamespacedKey(DrMap.getInstance(), "drmap-author");
        return container.get(keyAuthor, PersistentDataType.STRING);
    }

    public static String getCreationString(PersistentDataContainer container) {
        try {
            Long creation = getCreationLong(container);
            if (creation == null) {
                return null;
            }
            Date date = new Date(creation * 1000L);
            SimpleDateFormat dateFormat = new SimpleDateFormat(Config.TIME_FORMAT);
            return dateFormat.format(date);
        } catch (Exception ignored) {}
        return null;
    }

    public static Long getCreationLong(PersistentDataContainer container) {
        NamespacedKey keyCreation = new NamespacedKey(DrMap.getInstance(), "drmap-creation");
        return container.get(keyCreation, PersistentDataType.LONG);
    }

    public static int[] getParts(PersistentDataContainer container) {
        NamespacedKey keyPart = new NamespacedKey(DrMap.getInstance(), "drmap-part");
        return container.get(keyPart, PersistentDataType.INTEGER_ARRAY);
    }

    public static String getSource(PersistentDataContainer container) {
        NamespacedKey keySource = new NamespacedKey(DrMap.getInstance(), "drmap-source");
        return container.get(keySource, PersistentDataType.STRING);
    }
}
