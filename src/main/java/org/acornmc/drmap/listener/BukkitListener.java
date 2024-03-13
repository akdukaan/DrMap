package org.acornmc.drmap.listener;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.Util;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.Picture;
import org.acornmc.drmap.picture.PictureManager;
import org.acornmc.drmap.picture.PictureMeta;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;


import java.util.function.Predicate;

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
        if (!(clicked instanceof ItemFrame)) return;
        Player player = event.getPlayer();
        final ItemFrame frame = (ItemFrame) clicked;
        final ItemStack framedItem = frame.getItem();
        final Material frameContent = framedItem.getType();
        if (frameContent == Material.FILLED_MAP) {
            if (!Util.isDrMap(framedItem)) return;
            if (player.hasPermission("drmap.rotate")) return;
            event.setCancelled(true);
            Lang.send(player, Lang.ACTION_NO_PERMISSION);
            return;
        }
        if (frameContent == Material.AIR) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material usingMaterial = hand.getType();
            if (usingMaterial == Material.AIR) {
                hand = player.getInventory().getItemInOffHand();
            }
            if (!Util.isDrMap(hand)) return;
            if (!player.hasPermission("drmap.place")) {
                event.setCancelled(true);
                Lang.send(player, Lang.ACTION_NO_PERMISSION);
                return;
            }
            if (player.hasPermission("drmap.place.magic")) {
                tryMagicPlace(event, frame, hand);
            }
        }
    }

    public void tryMagicPlace(PlayerInteractEntityEvent event, ItemFrame frame, ItemStack itemUsed) {
        Player player = event.getPlayer();
        ItemMeta itemMeta = itemUsed.getItemMeta();
        if (itemMeta == null) return;
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        int[] part = PictureMeta.getParts(container);
        if (part[2] == 0 && part[3] == 0) return;

        // Check that the player has all parts
        int[][] partsFound = new int[part[2]+1][part[3]+1];
        String source = PictureMeta.getSource(container);
        long creation = PictureMeta.getCreationLong(container);
        String author = PictureMeta.getAuthorUUIDString(container);
        PlayerInventory playerInventory = player.getInventory();
        for (int inventoryIndex = 0; inventoryIndex <= playerInventory.getSize(); inventoryIndex++) {
            ItemStack item = playerInventory.getItem(inventoryIndex);
            partsFound = markIfFound(item, partsFound, inventoryIndex, source, creation, author);
        }
        if (!allFound(partsFound)) return;

        // Check that the wall has all the empty itemframes
        int leftSearch = part[0];
        int upSearch = part[1];
        int rightSearch = part[2]-part[0];
        int downSearch = part[3]-part[1];
        Location origin = frame.getLocation();
        World world = origin.getWorld();
        if (world == null) return;
        int originX = origin.getBlockX();
        int originZ = origin.getBlockZ();
        BlockFace face = frame.getFacing();
        // North: Left is increasing x, Right is decreasing x,
        // South: Left is decreasing x, Right is increasing x,
        // West: Left is decreasing z, Right is increasing z,
        // East Left is increasing z, Right is decreasing z,
        int lowestZ,lowestX,highestZ,highestX;
        int lowestY = origin.getBlockY()-downSearch;
        int highestY = origin.getBlockY()+upSearch;
        if (face == BlockFace.NORTH) {
            lowestZ = originZ;
            highestZ = originZ;
            lowestX = originX - rightSearch;
            highestX = originX + leftSearch;
        } else if (face == BlockFace.SOUTH) {
            lowestZ = originZ;
            highestZ = originZ;
            lowestX = originX - leftSearch;
            highestX = originX + rightSearch;
        } else if (face == BlockFace.WEST) {
            lowestX = originX;
            highestX = originX;
            lowestZ = originZ - leftSearch;
            highestZ = originZ + rightSearch;
        } else { // BlockFace.EAST
            lowestX = originX;
            highestX = originX;
            lowestZ = originZ - rightSearch;
            highestZ = originZ + leftSearch;
        }
        for (int x = lowestX; x <= highestX; x++) {
            for (int y = lowestY; y <= highestY; y++) {
                for (int z = lowestZ; z <= highestZ; z++) {
                    Location location = new Location(world, x, y, z);
                    if (Util.getBlockProtection(player, location, Material.ITEM_FRAME) != null) return;
                    if (getEmptyItemFrame(location, world, face) == null) return;
                }
            }
        }

        if (face == BlockFace.NORTH) {
            for (int x = lowestX; x <= highestX; x++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, x, y, originZ);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[highestX-x][highestY-y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(playerInventory,inventoryIndex);
                }
            }
        } else if (face == BlockFace.SOUTH) {
            for (int x = lowestX; x <= highestX; x++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, x, y, originZ);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[x][y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(playerInventory,inventoryIndex);
                }
            }
        } else if (face == BlockFace.WEST) {
            for (int z = lowestZ; z <= highestZ; z++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, originX, y, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[z][y];
                    ItemStack map = playerInventory.getItem(partsFound[z][y]);
                    itemFrame.setItem(map);
                    removeOne(playerInventory,inventoryIndex);
                }
            }
        } else { // BlockFace.EAST
            for (int z = lowestZ; z <= highestZ; z++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, originX, y, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[highestZ-z][highestY-y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(playerInventory, inventoryIndex);
                }
            }
        }
    }

    public void removeOne(PlayerInventory inv, int index) {
        ItemStack itemStack = inv.getItem(index);
        if (itemStack == null) return;
        itemStack.setAmount(itemStack.getAmount()-1);
    }

    public ItemFrame getEmptyItemFrame(Location location, World world, BlockFace face) {
        Predicate<Entity> isItemFrame = entity -> entity.getFacing() == face && (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME);
        for (Entity frame : world.getNearbyEntities(location,1,1,1,isItemFrame)) {
            ItemFrame itemFrame = (ItemFrame) frame;
            if (itemFrame.getItem().getType() == Material.AIR) return itemFrame;
        }
        return null;
    }

    public static boolean allFound(int[][] array) {
        for (int i = 0; i < array.length; i++) {
            for (int j = 0; j < array[i].length; j++) {
                if (array[i][j] < 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public int[][] markIfFound(ItemStack item, int[][] partsFound, int inventoryIndex, String source, long creation, String author) {
        if (!Util.isDrMap(item)) return partsFound;
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return partsFound;
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        String itemSource = PictureMeta.getSource(container);
        if (!itemSource.equals(source)) return partsFound;
        long itemCreation = PictureMeta.getCreationLong(container);
        if (itemCreation != creation) return partsFound;
        String itemAuthor = PictureMeta.getAuthorUUIDString(container);
        if (!itemAuthor.equals(author)) return partsFound;
        int[] itemParts = PictureMeta.getParts(container);
        partsFound[itemParts[0]][itemParts[1]] = inventoryIndex;
        return partsFound;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof CartographyInventory)) {
            return;
        }
        if (event.getSlot() != 2) {
            return;
        }
        CartographyInventory cartographyInventory = (CartographyInventory) event.getClickedInventory();
        ItemStack itemStack = cartographyInventory.getItem(0);
        if (itemStack == null) {
            return;
        }
        if (!Util.isDrMap(itemStack)) {
            return;
        }
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player)) {
            return;
        }
        Player player = (Player) human;
        if (!player.hasPermission("drmap.cartography")) {
            event.setCancelled(true);
            Lang.send(player, Lang.CARTOGRAPHY_NO_PERMISSION);
        }
    }
}