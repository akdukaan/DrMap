package org.acornmc.drmap;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class Util {

    public static boolean isDrMap(ItemStack itemStack) {
        Material material = itemStack.getType();
        if (material != Material.FILLED_MAP) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(DrMap.getInstance(), "drmap-author");
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        if (!container.has(key, PersistentDataType.STRING)) {
            return false;
        }
        return true;
    }
}
