package org.acornmc.drmap.listener;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.PictureManager;
import org.acornmc.drmap.picture.PictureMeta;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class BukkitListener implements Listener {
    private DrMap plugin;

    public BukkitListener(DrMap plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> PictureManager.INSTANCE.sendAllMaps(event.getPlayer()));
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
            int[] parts = PictureMeta.getParts(container, plugin);
            if (parts == null) {
                return;
            }
            if (parts[0] != 0 || parts[1] != 0) {
               return;
            }
            if (parts[2] == 0 && parts[3] == 0) {
                return;
            }
            if (!PictureMeta.playerHasAllParts(container, player, plugin)) {
                return;
            }

            // Check to make sure there are frames for all map parts
            Location frameLocation = frame.getLocation();
            if (!isAllEmptyFrames(frame, parts[2], parts[3], frame.getAttachedFace())) {
                return;
            }

            // place all map parts from player's inv

            for (ItemStack itemStack : player.getInventory().getContents()) {
                if (itemStack.getType().equals(Material.FILLED_MAP)) {
                    ItemMeta meta2 = itemStack.getItemMeta();
                    if (meta2 == null) {
                        return;
                    }
                    PersistentDataContainer container2 = meta2.getPersistentDataContainer();
                    String authorUUID1 = PictureMeta.getAuthorUUIDString(container, plugin);
                    String authorUUID2 = PictureMeta.getAuthorUUIDString(container2, plugin);
                    if (authorUUID1.equals(authorUUID2)) {
                        long time1 = PictureMeta.getCreationLong(container, plugin);
                        long time2 = PictureMeta.getCreationLong(container2, plugin);
                        if (time1 == time2) {

                        }
                    }
                }
            }
            // TODO place all map parts in item frames

        }
    }

    // TODO this and below
    public boolean isAllEmptyFrames(ItemFrame frame, int w, int h, BlockFace blockFace) {
        Location location = frame.getLocation();
        if (blockFace.equals(BlockFace.EAST)) {
            List<Entity> nearby = frame.getNearbyEntities(0, h, w);
            // grow in negative z
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    double x = location.getBlockX();
                    double y = location.getBlockY() + j;
                    double z = location.getBlockZ() - i;
                    Location loc = new Location(location.getWorld(), x, y, z);
                    if (!isEmptyFrame(loc, blockFace, nearby)) {
                        return false;
                    }
                }
            }
        }
        if (blockFace.equals(BlockFace.SOUTH)) {
            List<Entity> nearby = frame.getNearbyEntities(0, h, w);

            //grow in pos x
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    double x = location.getBlockX() + i;
                    double y = location.getBlockY() + j;
                    double z = location.getBlockZ();
                    Location loc = new Location(location.getWorld(), x, y, z);
                    if (!isEmptyFrame(loc, blockFace, nearby)) {
                        return false;
                    }
                }
            }
        }
        if (blockFace.equals(BlockFace.WEST)) {
            List<Entity> nearby = frame.getNearbyEntities(0, h, w);

            //grow in pos z
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    double x = location.getBlockX();
                    double y = location.getBlockY() + j;
                    double z = location.getBlockZ() + i;
                    Location loc = new Location(location.getWorld(), x, y, z);
                    if (!isEmptyFrame(loc, blockFace, nearby)) {
                        return false;
                    }
                }
            }
        }
        if (blockFace.equals(BlockFace.NORTH)) {
            List<Entity> nearby = frame.getNearbyEntities(0, h, w);

            // grow in neg x
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    double x = location.getBlockX() - i;
                    double y = location.getBlockY() + j;
                    double z = location.getBlockZ();
                    Location loc = new Location(location.getWorld(), x, y, z);
                    if (!isEmptyFrame(loc, blockFace, nearby)) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    public boolean isEmptyFrame(Location location, BlockFace blockface, List<Entity> entityList) {
        for (Entity entity : entityList) {
            if (entity instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) entity;
                if (frame.getLocation().equals(location) && frame.getFacing() == blockface) {
                    return frame.isEmpty();
                }
            }
        }
        return false;
    }

    public ItemFrame getEmptyFrameAt(Location location, BlockFace blockface, List<Entity> entityList) {
        for (Entity entity : entityList) {
            if (entity instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) entity;
                if (frame.getLocation().equals(location) && frame.getFacing() == blockface) {
                    return frame;
                }
            }
        }
        return null;
    }

}