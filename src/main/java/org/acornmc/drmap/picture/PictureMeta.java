package org.acornmc.drmap.picture;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.stream.IntStream;

public class PictureMeta {

    public static void sendAuthor(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String author = PictureMeta.getAuthorUsername(container, plugin);
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

    public static void sendSource(CommandSender sender, PersistentDataContainer container, DrMap plugin) {
        String source = PictureMeta.getSource(container, plugin);
        if (source != null) {
            String message = Lang.INFO_SOURCE.replace("{source}", source);
            Lang.send(sender, message);
        }
    }

    public static String getAuthorUsername(PersistentDataContainer container, DrMap plugin) {
        String author = getAuthorUUIDString(container, plugin);
        if (author == null) {
            return null;
        }
        UUID authorUUID = UUID.fromString(author);
        return Bukkit.getOfflinePlayer(authorUUID).getName();
    }

    public static String getAuthorUUIDString(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyAuthor = new NamespacedKey(plugin, "drmap-author");
        return container.get(keyAuthor, PersistentDataType.STRING);
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

    public static String getSource(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keySource = new NamespacedKey(plugin, "drmap-source");
        return container.get(keySource, PersistentDataType.STRING);
    }

    public static int[] getIds(PersistentDataContainer container, int mapId, DrMap plugin) {
        NamespacedKey keyIds = new NamespacedKey(plugin, "drmap-ids");
        int[] ids = container.get(keyIds, PersistentDataType.INTEGER_ARRAY);
        if (ids != null) {
            return ids;
        }
        int[] parts = getParts(container, plugin);
        int width = parts[2] + 1;
        int start = mapId - (parts[0] + parts[1] * width);
        int end = start + (width * (parts[3] + 1));
        return IntStream.range(start, end).toArray();
    }

    public static boolean isProportion(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyProportion = new NamespacedKey(plugin, "drmap-proportion");
        Byte value = container.get(keyProportion, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public static Color getBackground(PersistentDataContainer container, DrMap plugin) {
        NamespacedKey keyBackground = new NamespacedKey(plugin, "drmap-background");
        int[] value = container.get(keyBackground, PersistentDataType.INTEGER_ARRAY);
        return value == null ? null : new Color(value[0], value[1], value[2], value[3]);
    }

    public static boolean playerHasAllParts(PersistentDataContainer container, Player player, DrMap plugin) {
        int[] parts = PictureMeta.getParts(container, plugin);
        if (parts == null) {
            return false;
        }
        long creation = getCreationLong(container, plugin);
        String authorUUIDString = getAuthorUUIDString(container, plugin);
        for (int i = 0; i < parts[2]; i++) {
            for (int j = 0; j < parts[3]; j++) {
                if (!playerHasPart(player, new int[]{i, j, parts[2], parts[3]}, authorUUIDString, creation, plugin)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean playerHasPart(Player player, int[] parts, String author, long timestamp, DrMap plugin) {
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() == Material.FILLED_MAP) {
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    PersistentDataContainer container = itemMeta.getPersistentDataContainer();
                    if (author.equals(getAuthorUUIDString(container, plugin)) && timestamp == getCreationLong(container, plugin) && Arrays.equals(parts, getParts(container, plugin))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
