package org.acornmc.drmap.listener;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.PictureManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BukkitListener implements Listener {
    private DrMap plugin;

    public BukkitListener(DrMap plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                PictureManager.INSTANCE.sendAllMaps(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void frameMap(PlayerInteractEntityEvent event) {
        final Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ItemFrame)) {
            return;
        }
        Player player = event.getPlayer();
        final ItemFrame frame = (ItemFrame) clicked;
        final ItemStack item = frame.getItem();
        final Material frameMaterial = item.getType();
        if (frameMaterial == Material.FILLED_MAP) {
            NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            if (!container.has(key, PersistentDataType.STRING)) {
                return;
            }
            if (!player.hasPermission("drmap.rotate")) {
                event.setCancelled(true);
                Lang.send(player, Lang.ACTION_NO_PERMISSION);
            }
        } else if (frameMaterial == Material.AIR) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material usingMaterial = hand.getType();
            if (usingMaterial == Material.AIR) {
                hand = player.getInventory().getItemInOffHand();
                usingMaterial = hand.getType();
            }
            if (usingMaterial != Material.FILLED_MAP) {
                return;
            }
            NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
            ItemMeta itemMeta = hand.getItemMeta();
            if (itemMeta == null) {
                return;
            }
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            if (!container.has(key, PersistentDataType.STRING)) {
                return;
            }
            if (!player.hasPermission("drmap.place")) {
                event.setCancelled(true);
                Lang.send(player, Lang.ACTION_NO_PERMISSION);
            }
        }
    }
}