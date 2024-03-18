package org.acornmc.drmap.listener;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.Util;
import org.acornmc.drmap.configuration.Lang;
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


import java.util.Arrays;
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
                if (tryMagicPlace(player, frame, hand)) {
                    // tryMagicPlace will already place the map if it succeeds.
                    // If we don't cancel the event, it'll rotate the one map.
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     *
     * @param frame the item frame that the player manually placed. We will try to magic place around it.
     * @param itemUsed the map that the player placed in the item frame. We will use its meta to find other maps like it.
     * @return true if we were able to magic-place everything
     */
    public boolean tryMagicPlace(Player player, ItemFrame frame, ItemStack itemUsed) {
        ItemMeta itemMeta = itemUsed.getItemMeta();
        if (itemMeta == null) return false;
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        int[] part = PictureMeta.getParts(container);
        if (part[2] == 0 && part[3] == 0) return false;

        // Check that the player has all parts
        // partsFound keeps track of which spot in the player's inventory we can find each part of the map.
        // If we don't know or it doesnt exist, it's set to -1.
        int[][] partsFound = new int[part[2]+1][part[3]+1];
        // Initialize the array with all -1s to indicate that each part is not found.
        for (int i = 0; i < partsFound.length; i++) {
            Arrays.fill(partsFound[i], -1);
        }
        // Loop through the player's inventory and indicate which inventory spot we found the map in.
        String source = PictureMeta.getSource(container);
        long creation = PictureMeta.getCreationLong(container);
        String author = PictureMeta.getAuthorUUIDString(container);
        PlayerInventory playerInventory = player.getInventory();
        for (int inventoryIndex = 0; inventoryIndex <= playerInventory.getSize(); inventoryIndex++) {
            ItemStack item = playerInventory.getItem(inventoryIndex);
            partsFound = markIfFound(item, partsFound, inventoryIndex, source, creation, author);
        }
        if (!allFound(partsFound)) return false;

        // Calculate the range of positions that we need to search item frames in
        int leftSearch = part[0]; // The distance to the 'left' we need to check item frames
        int upSearch = part[1]; // The distance up we need to check item frames
        int rightSearch = part[2]-part[0]; // The distance to the 'right' we need to check item frames
        int downSearch = part[3]-part[1]; // The distance down we need to check item frames
        Location origin = frame.getLocation();
        World world = origin.getWorld();
        if (world == null) return false;
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        BlockFace face = frame.getFacing();
        int lowestZ,lowestX,highestZ,highestX;
        int lowestY = originY-downSearch;
        int highestY = originY+upSearch;
        float yaw = player.getLocation().getYaw();

        // North: Left east is increasing x, Right west is decreasing x,
        // South: Left is decreasing x, Right is increasing x,
        // West: Left north is decreasing z, Right south is increasing z,
        // East Left is increasing z, Right is decreasing z,
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
        } else if (face == BlockFace.EAST) {
            lowestX = originX;
            highestX = originX;
            lowestZ = originZ - rightSearch;
            highestZ = originZ + leftSearch;
        } else if (face == BlockFace.UP) {
            lowestY = originY;
            highestY = originY;
            if (yaw >= 45 && yaw < 135) {
                // Player is facing west
                // Left is south (increasing z)
                // Right is north (decreasing z)
                // Up is west (decreasing x)
                // Down is east (increasing x)
                lowestX = originX - upSearch;
                highestX = originX + downSearch;
                lowestZ = originZ - rightSearch;
                highestZ = originZ + leftSearch;
            } else if (yaw >= -135 && yaw < -45){
                // Player is facing east
                // Left is north (decreasing z)
                // Right is south (increasing z)
                // Up is east (increasing x)
                // Down is west (decreasing x)
                lowestX = originX - downSearch;
                highestX = originX + upSearch;
                lowestZ = originZ - leftSearch;
                highestZ = originZ + rightSearch;
            } else if (yaw >= -45 && yaw < 45) {
                // Player is facing south
                // Left is east (increasing x)
                // Right is west (decreasing x)
                // Up is south (increasing z)
                // Down is north (decreasing z)
                lowestX = originX - rightSearch;
                highestX = originX + leftSearch;
                lowestZ = originZ - downSearch;
                highestZ = originZ + upSearch;
            } else {
                // Player is facing north
                // Left is west (decreasing x)
                // Right is east (increasing x)
                // Up is north (decreasing z)
                // Down is south (increasing z)
                lowestX = originX - leftSearch;
                highestX = originX + rightSearch;
                lowestZ = originZ - upSearch;
                highestZ = originZ + downSearch;
            }

        } else if (face == BlockFace.DOWN) {
            lowestY = originY;
            highestY = originY;
            if (yaw >= 45 && yaw < 135) {
                // Player is facing west
                lowestX = originX - downSearch;
                highestX = originX + upSearch;
                lowestZ = originZ - rightSearch;
                highestZ = originZ + leftSearch;
            } else if (yaw >= -135 && yaw < -45){
                // Player is facing east
                lowestX = originX - upSearch;
                highestX = originX + downSearch;
                lowestZ = originZ - leftSearch;
                highestZ = originZ + rightSearch;
            } else if (yaw >= -45 && yaw < 45) {
                // Player is facing south
                lowestX = originX - rightSearch;
                highestX = originX + leftSearch;
                lowestZ = originZ - upSearch;
                highestZ = originZ + downSearch;
            } else {
                // Player is facing north
                lowestX = originX - leftSearch;
                highestX = originX + rightSearch;
                lowestZ = originZ - downSearch;
                highestZ = originZ + upSearch;
            }
        } else {
            // If not one of the 6 directions, we're confused
            return false;
        }
        // Check through the range to make sure all the item frames we need are there and are facing the same way
        for (int x = lowestX; x <= highestX; x++) {
            for (int y = lowestY; y <= highestY; y++) {
                for (int z = lowestZ; z <= highestZ; z++) {
                    Location location = new Location(world, x, y, z);
                    if (Util.getBlockProtection(player, location, Material.ITEM_FRAME) != null) return false;
                    if (getEmptyItemFrame(location, world, face) == null) return false;
                }
            }
        }

        // At this point we've checked everything we need to know that we should magic place.
        // So we will go through each location and place the map we need in the item frame
        if (face == BlockFace.NORTH) {
            for (int x = lowestX; x <= highestX; x++) {
                for (int y = lowestY; y <= highestY; y++) {
                    // Get the item frame we want to place into
                    Location location = new Location(world, x, y, originZ);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    // Lookup where in the inventory we found the map earlier
                    int inventoryIndex = partsFound[highestX-x][highestY-y];
                    // Find the corresponding map from the player's inventory
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    // Set the map to the item frame
                    itemFrame.setItem(map);
                    // Remove the map from the player's inventory
                    removeOne(player,inventoryIndex);
                }
            }
        } else if (face == BlockFace.SOUTH) {
            for (int x = lowestX; x <= highestX; x++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, x, y, originZ);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[x-lowestX][highestY-y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(player,inventoryIndex);
                }
            }
        } else if (face == BlockFace.WEST) {
            for (int z = lowestZ; z <= highestZ; z++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, originX, y, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[z-lowestZ][highestY-y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(player,inventoryIndex);
                }
            }
        } else if (face == BlockFace.EAST){ // BlockFace.EAST
            for (int z = lowestZ; z <= highestZ; z++) {
                for (int y = lowestY; y <= highestY; y++) {
                    Location location = new Location(world, originX, y, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);
                    int inventoryIndex = partsFound[highestZ-z][highestY-y];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(player, inventoryIndex);
                }
            }
        } else if (face == BlockFace.UP) {
            Rotation rotation;
            for (int x = lowestX; x <= highestX; x++) {
                for (int z = lowestZ; z <= highestZ; z++) {
                    Location location = new Location(world, x, originY, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);

                    int leftRightIndex;
                    int upDownIndex;
                    if (yaw >= 45 && yaw < 135) {
                        // Player is facing west
                        // Left is south (increasing z)
                        // Right is north (decreasing z)
                        // Up is west (decreasing x)
                        // Down is east (increasing x)
                        leftRightIndex = highestZ-z;
                        upDownIndex = x-lowestX;
                        rotation = Rotation.COUNTER_CLOCKWISE_45;
                    } else if (yaw >= -135 && yaw < -45) {
                        // Player is facing east
                        // Left is north (decreasing z)
                        // Right is south (increasing z)
                        // Up is east (increasing x)
                        // Down is west (decreasing x)
                        leftRightIndex = z-lowestZ;
                        upDownIndex = highestX-x;
                        rotation = Rotation.FLIPPED_45;
                    } else if (yaw >= -45 && yaw < 45) {
                        // Player is facing south
                        // Left is east (increasing x)
                        // Right is west (decreasing x)
                        // Up is south (increasing z)
                        // Down is north (decreasing z)
                        leftRightIndex = highestX-x;
                        upDownIndex = highestZ-z;
                        rotation = Rotation.COUNTER_CLOCKWISE;
                    } else {
                        // Player is facing north
                        // Left is west (decreasing x)
                        // Right is east (increasing x)
                        // Up is north (decreasing z)
                        // Down is south (increasing z)
                        leftRightIndex = x-lowestX;
                        upDownIndex = z-lowestZ;
                        rotation = Rotation.NONE;
                    }

                    int inventoryIndex = partsFound[leftRightIndex][upDownIndex];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(player, inventoryIndex);
                    itemFrame.setRotation(rotation);
                }
            }
        } else if (face == BlockFace.DOWN) {
            Rotation rotation;
            for (int x = lowestX; x <= highestX; x++) {
                for (int z = lowestZ; z <= highestZ; z++) {
                    Location location = new Location(world, x, originY, z);
                    ItemFrame itemFrame = getEmptyItemFrame(location, world, face);

                    int leftRightIndex;
                    int upDownIndex;
                    if (yaw >= 45 && yaw < 135) {
                        // Player is facing west
                        leftRightIndex = highestZ-z;
                        upDownIndex = highestX-x;
                        rotation = Rotation.FLIPPED_45;
                    } else if (yaw >= -135 && yaw < -45) {
                        // Player is facing east
                        leftRightIndex = z-lowestZ;
                        upDownIndex = x-lowestX;
                        rotation = Rotation.COUNTER_CLOCKWISE_45;
                    } else if (yaw >= -45 && yaw < 45) {
                        // Player is facing south
                        leftRightIndex = highestX-x;
                        upDownIndex = z-lowestZ;
                        rotation = Rotation.COUNTER_CLOCKWISE;
                    } else {
                        // Player is facing north
                        leftRightIndex = x-lowestX;
                        upDownIndex = highestZ-z;
                        rotation = Rotation.NONE;
                    }

                    int inventoryIndex = partsFound[leftRightIndex][upDownIndex];
                    ItemStack map = playerInventory.getItem(inventoryIndex);
                    itemFrame.setItem(map);
                    removeOne(player, inventoryIndex);
                    itemFrame.setRotation(rotation);
                }
            }
        } else {
            // There's no way we could've gotten here but putting in a safety anyway
            return false;
        }
        return true;
    }

    public void removeOne(Player player, int index) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerInventory inv = player.getInventory();
        ItemStack itemStack = inv.getItem(index);
        if (itemStack == null) return;
        itemStack.setAmount(itemStack.getAmount()-1);
    }

    /**
     *
     * @param location The location to check
     * @param world The world to check
     * @param face The blockface we want to find
     * @return the itemFrame at the location with the blockface or null if none exists.
     */
    public ItemFrame getEmptyItemFrame(Location location, World world, BlockFace face) {
        Predicate<Entity> isItemFrame = entity -> entity.getFacing() == face && (entity.getType() == EntityType.ITEM_FRAME || entity.getType() == EntityType.GLOW_ITEM_FRAME);
        for (Entity frame : world.getNearbyEntities(location,1,1,1,isItemFrame)) {
            ItemFrame itemFrame = (ItemFrame) frame;
            if (itemFrame.getItem().getType() == Material.AIR) {
                Location frameLocation = itemFrame.getLocation();
                if (frameLocation.getBlockX() == location.getBlockX() &&
                        frameLocation.getBlockY() == location.getBlockY() &&
                        frameLocation.getBlockZ() == location.getBlockZ()) {
                    return itemFrame;
                }
            }
        }
        return null;
    }

    /**
     *
     * @param array a 2d array to check
     * @return true if all values in the array are non-negative
     */
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

    /**
     *
     * @param item The item to potentially add to partsFound
     * @param partsFound What we know about the found parts so far
     * @param inventoryIndex The location indicator to save into partsFound if the item param is a match
     * @param source The map's source url to match to
     * @param creation The map's creation time to match to
     * @param author The map's author to match to
     * @return the updated version of partsFound after adding in info from the item param
     */
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
        // We update partsFound with the inventory index since we found the part
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