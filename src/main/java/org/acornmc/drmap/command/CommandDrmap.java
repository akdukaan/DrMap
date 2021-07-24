package org.acornmc.drmap.command;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.Picture;
import org.acornmc.drmap.picture.PictureManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommandDrmap implements TabExecutor {
    private final DrMap plugin;

    public CommandDrmap(DrMap plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ArrayList<String> list = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("drmap.create")) {
                list.add("create");
            }
            if (sender.hasPermission("drmap.reload")) {
                list.add("reload");
            }
            if (sender.hasPermission("drmap.info")) {
                list.add("info");
            }
            if (sender.hasPermission("drmap.erase")) {
                list.add("erase");
            }
            return StringUtil.copyPartialMatches(args[0], list, new ArrayList<>());
        }

        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            return false; // show usage
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("drmap.reload")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            Config.reload(plugin);
            Lang.reload(plugin);

            Lang.send(sender, "&a" + plugin.getName() + " v" + plugin.getDescription().getVersion() + " reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("create") && args.length > 1) {
            if (!sender.hasPermission("drmap.create")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }

            if (!(sender instanceof Player)) {
                Lang.send(sender, Lang.NOT_PLAYER);
                return true;
            }

            // Check if they have a map
            Player player = (Player) sender;
            if (!player.getInventory().contains(Material.MAP)) {
                Lang.send(sender, Lang.MUST_HAVE_MAP);
                return true;
            }

            int width = 0;
            int height = 0;
            if (args.length > 2) {
                if (args[2].equalsIgnoreCase("-s")) {
                    width = 1;
                    height = 1;
                } else {
                    if (args.length < 4) {
                        return false;
                    }
                    try {
                        width = Integer.parseInt(args[2]);
                        height = Integer.parseInt(args[3]);
                    } catch (Exception ignored) {
                        // If they put invalid height/width, assume unstretched
                    }
                }
            }
            // If they put invalid height/width, assume unstretched
            if (width <= 0 || height <= 0) {
                width = 0;
                height = 0;
            }

            int requiredAmount = width * height;
            if (requiredAmount == 0) {
                requiredAmount = 1;
            }
            final int finalRequiredAmount = requiredAmount;

            int playerHas = 0;
            for (ItemStack itemStack : player.getInventory().getContents()) {
                if (itemStack != null && itemStack.getType() == Material.MAP) {
                    playerHas += itemStack.getAmount();
                }
            }
            if (playerHas < requiredAmount) {
                Lang.send(sender, Lang.NOT_ENOUGH_MAPS);
                return true;
            }

            int finalWidth = width;
            int finalHeight = height;

            if (finalWidth == 0 && finalHeight == 0) {
                CompletableFuture.supplyAsync(() -> PictureManager.INSTANCE.downloadProportionalImage(args[1])).whenCompleteAsync((Image image, Throwable exception) -> {
                    if (image == null) {
                        plugin.getLogger().severe("Could not download image: " + args[1]);
                        Lang.send(sender, Lang.ERROR_DOWNLOADING);
                        return;
                    }

                    MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));

                    if (!PictureManager.INSTANCE.saveImage(image, mapView.getId())) {
                        plugin.getLogger().severe("Could not save image to disk: " + args[1] + " -> " + mapView.getId() + ".png");
                        Lang.send(sender, Lang.ERROR_DOWNLOADING);
                        return;
                    }

                    Picture picture = new Picture(image, mapView);
                    PictureManager.INSTANCE.addPicture(picture);

                    ItemStack map = new ItemStack(Material.FILLED_MAP);
                    MapMeta meta = (MapMeta) map.getItemMeta();
                    if (meta == null) {
                        Lang.send(sender, Lang.ERROR_DOWNLOADING);
                        return;
                    }
                    meta.setMapView(picture.getMapView());

                    // Mark the author
                    NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
                    map.setItemMeta(meta);

                    //Remove maps
                    if (!(removeFromInventory(player, Material.MAP, finalRequiredAmount))) {
                        Lang.send(sender, Lang.NOT_ENOUGH_MAPS);
                        return;
                    }

                    // Give map
                    player.getInventory().addItem(map).forEach((index, item) -> {
                        Item drop = player.getWorld().dropItem(player.getLocation(), item);
                        drop.setPickupDelay(0);
                        drop.setOwner(player.getUniqueId());
                    });

                    Lang.send(sender, Lang.IMAGE_CREATED);
                }, plugin.getMainThreadExecutor());
                return true;
            }

            // If stretching the map
            CompletableFuture.supplyAsync(() -> PictureManager.INSTANCE.downloadStretchedImage(args[1], finalWidth, finalHeight)).whenCompleteAsync((Image[][] images, Throwable exception) -> {
                if (images == null) {
                    plugin.getLogger().severe("Could not download image: " + args[1]);
                    Lang.send(sender, Lang.ERROR_DOWNLOADING);
                    return;
                }

                List<ItemStack> maps = new LinkedList<>();
                for (int j = 0; j < images[0].length; j++) {
                    for (int i = 0; i < images.length; i++) {
                        MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
                        if (!PictureManager.INSTANCE.saveImage(images[i][j], mapView.getId())) {
                            plugin.getLogger().warning("Could not save image to disk: " + args[1] + " -> " + mapView.getId() + ".png");
                            Lang.send(sender, Lang.ERROR_DOWNLOADING);
                            return;
                        }

                        Picture picture = new Picture(images[i][j], mapView);
                        PictureManager.INSTANCE.addPicture(picture);

                        ItemStack map = new ItemStack(Material.FILLED_MAP);
                        MapMeta meta = (MapMeta) map.getItemMeta();
                        if (meta == null) {
                            Lang.send(sender, Lang.ERROR_DOWNLOADING);
                            return;
                        }
                        meta.setMapView(picture.getMapView());

                        // Mark the author
                        NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
                        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
                        map.setItemMeta(meta);
                        maps.add(map);
                    }
                }

                //Remove empty maps
                if (!(removeFromInventory(player, Material.MAP, finalRequiredAmount))) {
                    Lang.send(sender, Lang.NOT_ENOUGH_MAPS);
                    return;
                }

                // Give filled maps
                for (ItemStack is : maps) {
                    player.getInventory().addItem(is).forEach((index, item) -> {
                        Item drop = player.getWorld().dropItem(player.getLocation(), item);
                        drop.setPickupDelay(0);
                        drop.setOwner(player.getUniqueId());
                    });
                }

                Lang.send(sender, Lang.IMAGE_CREATED);
            }, plugin.getMainThreadExecutor());
            return true;
        }

        if (args[0].equalsIgnoreCase("erase")) {
            if (!sender.hasPermission("drmap.erase")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            if (!(sender instanceof Player)) {
                Lang.send(sender, Lang.NOT_PLAYER);
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material usingMaterial = hand.getType();
            if (usingMaterial != Material.FILLED_MAP) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            ItemMeta itemMeta = hand.getItemMeta();
            if (itemMeta == null) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            if (!container.has(key, PersistentDataType.STRING)) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            int amount = hand.getAmount();
            ItemStack blankMap = new ItemStack(Material.MAP, amount);
            player.getInventory().setItemInMainHand(blankMap);
            return true;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("author")) {
            if (!sender.hasPermission("drmap.info")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            if (!(sender instanceof Player)) {
                Lang.send(sender, Lang.NOT_PLAYER);
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material usingMaterial = hand.getType();
            if (usingMaterial != Material.FILLED_MAP) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
            ItemMeta itemMeta = hand.getItemMeta();
            if (itemMeta == null) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            if (!container.has(key, PersistentDataType.STRING)) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            String author = container.get(key, PersistentDataType.STRING);
            if (author == null) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            UUID authorUUID = UUID.fromString(author);
            String authorName = Bukkit.getOfflinePlayer(authorUUID).getName();
            if (authorName == null) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            String message = Lang.AUTHOR.replace("{author}", authorName);
            Lang.send(sender, message);
            return true;
        }
        return false;
    }

    public boolean removeFromInventory(Player player, Material material, int removeAmount) {
        final int initialRemoveAmount = removeAmount;
        for (ItemStack is : player.getInventory().getContents()) {
            if (removeAmount <= 0) {
                return true;
            }
            if (is != null) {
                if (is.getType() == material) {
                    if (is.getAmount() >= removeAmount) {
                        is.setAmount(is.getAmount() - removeAmount);
                        removeAmount = 0;
                    } else {
                        is.setAmount(0);
                        removeAmount -= is.getAmount();
                    }
                }
            }
        }
        if (removeAmount == 0) {
            return true;
        } else {
            // Give the removed maps back to the player.
            // This should only happen if the player dropped
            // items immediately after using the create command
            Item drop = player.getWorld().dropItem(player.getLocation(), new ItemStack(material, initialRemoveAmount - removeAmount));
            drop.setPickupDelay(0);
            drop.setOwner(player.getUniqueId());
            return false;
        }
    }
}