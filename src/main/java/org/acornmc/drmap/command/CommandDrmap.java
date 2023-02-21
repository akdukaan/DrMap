package org.acornmc.drmap.command;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.Util;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.Picture;
import org.acornmc.drmap.picture.PictureManager;
import org.acornmc.drmap.picture.PictureMeta;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
            if (sender.hasPermission("drmap.command.create")) {
                list.add("create");
            }
            if (sender.hasPermission("drmap.command.reload")) {
                list.add("reload");
            }
            if (sender.hasPermission("drmap.command.info")) {
                list.add("info");
            }
            if (sender.hasPermission("drmap.command.erase")) {
                list.add("erase");
            }
            return StringUtil.copyPartialMatches(args[0], list, new ArrayList<>());
        } else if (args.length >= 3 && sender.hasPermission("drmap.command.create") && args[0].equalsIgnoreCase("create")) {
            boolean includedWidth = false;
            boolean includedHeight = false;
            boolean includedBackground = false;
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].toLowerCase().startsWith("width:")) {
                    includedWidth = true;
                }
                if (args[i].toLowerCase().startsWith("height:")) {
                    includedHeight = true;
                }
                if (args[i].toLowerCase().startsWith("background:")) {
                    includedBackground = true;
                }
            }
            if (!includedWidth) {
                list.add("width:");
            }
            if (!includedHeight) {
                list.add("height:");
            }
            if (!includedBackground) {
                list.add("background:");
            }
            return StringUtil.copyPartialMatches(args[args.length - 1], list, new ArrayList<>());
        }

        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            return false; // show usage
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("drmap.command.reload")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            Config.reload(plugin);
            Lang.reload(plugin);

            Lang.send(sender, "&a" + plugin.getName() + " v" + plugin.getDescription().getVersion() + " reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("create") && args.length > 1) {
            if (!sender.hasPermission("drmap.command.create")) {
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
            Color background = null;

            if (args.length > 2) {
                if (args[2].equalsIgnoreCase("-s")) {
                    width = 1;
                    height = 1;
                } else {
                    for (int i = 2; i < args.length; i++) {
                        if (args[i].toLowerCase().startsWith("width:")) {
                            try {
                                width = Integer.parseInt(args[i].toLowerCase().replaceFirst("width:", ""));
                            } catch (Exception ignored) {}
                        } else if (args[i].toLowerCase().startsWith("height:")) {
                            try {
                                height = Integer.parseInt(args[i].toLowerCase().replaceFirst("height:", ""));
                            } catch (Exception ignored) {}
                        } else if (args[i].toLowerCase().startsWith("background:")) {
                            try {
                                background = Color.decode(args[i].toLowerCase().replaceFirst("background:", ""));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            // If they put invalid height/width, assume 0
            if (width < 0) {
                width = 0;
            }
            if (height < 0) {
                height = 0;
            }

            if (width > 0 && height == 0) {
                height = 1;
            }
            if (height > 0 && width == 0) {
                width = 1;
            }

            // Calculate required amount
            int requiredAmount = width * height;
            if (requiredAmount <= 0) {
                requiredAmount = 1;
            }
            if (player.getGameMode().equals(GameMode.CREATIVE)) {
                requiredAmount = 0;
            }
            final int finalRequiredAmount = requiredAmount;

            // Check that they have enough
            // We will check again after the image is downloaded,
            // but I don't want to download images if they don't even have enough maps
            if (!(playerHas(player, Material.MAP, requiredAmount))) {
                Lang.send(sender, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(requiredAmount)));
                return true;
            }

            final int finalWidth = width;
            final int finalHeight = height;

            long unixtime = System.currentTimeMillis() / 1000L;
            Color finalBackground = background;

            // This section is all for if they will do a proportional image
            if (finalWidth == 0) {
                CompletableFuture.supplyAsync(() -> PictureManager.INSTANCE.downloadProportionalImage(args[1], finalBackground)).whenCompleteAsync((Image image, Throwable exception) -> {
                    if (image == null) {
                        plugin.getLogger().warning("Could not download image: " + args[1]);
                        Lang.send(sender, Lang.ERROR_DOWNLOADING);
                        return;
                    }

                    MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));

                    if (!PictureManager.INSTANCE.saveImage(image, mapView.getId())) {
                        plugin.getLogger().warning("Could not save image to disk: " + args[1] + " -> " + mapView.getId() + ".png");
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

                    // Mark the meta
                    NamespacedKey keyAuthor = new NamespacedKey(plugin, "drmap-author");
                    meta.getPersistentDataContainer().set(keyAuthor, PersistentDataType.STRING, player.getUniqueId().toString());
                    NamespacedKey keyCreation = new NamespacedKey(plugin, "drmap-creation");
                    meta.getPersistentDataContainer().set(keyCreation, PersistentDataType.LONG, unixtime);
                    NamespacedKey keyPart = new NamespacedKey(plugin, "drmap-part");
                    meta.getPersistentDataContainer().set(keyPart, PersistentDataType.INTEGER_ARRAY, new int[]{0,0,0,0});
                    NamespacedKey keySource = new NamespacedKey(plugin, "drmap-source");
                    meta.getPersistentDataContainer().set(keySource, PersistentDataType.STRING, args[1]);

                    // Apply the meta changes
                    map.setItemMeta(meta);

                    //Remove maps
                    if (playerHas(player, Material.MAP, finalRequiredAmount)) {
                        removeFromInventory(player, Material.MAP, finalRequiredAmount);
                    } else {
                        Lang.send(sender, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(finalRequiredAmount)));
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
            CompletableFuture.supplyAsync(() -> PictureManager.INSTANCE.downloadStretchedImage(args[1], finalWidth, finalHeight, finalBackground)).whenCompleteAsync((Image[][] images, Throwable exception) -> {
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

                        // Mark the meta
                        NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
                        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
                        NamespacedKey keyCreation = new NamespacedKey(plugin, "drmap-creation");
                        meta.getPersistentDataContainer().set(keyCreation, PersistentDataType.LONG, unixtime);
                        NamespacedKey keyPart = new NamespacedKey(plugin, "drmap-part");
                        meta.getPersistentDataContainer().set(keyPart, PersistentDataType.INTEGER_ARRAY, new int[]{i, j, images.length - 1, images[0].length - 1});
                        NamespacedKey keySource = new NamespacedKey(plugin, "drmap-source");
                        meta.getPersistentDataContainer().set(keySource, PersistentDataType.STRING, args[1]);
                        map.setItemMeta(meta);

                        maps.add(map);
                    }
                }

                //Remove empty maps
                if (playerHas(player, Material.MAP, finalRequiredAmount)) {
                    removeFromInventory(player, Material.MAP, finalRequiredAmount);
                } else {
                    Lang.send(sender, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(finalRequiredAmount)));
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
            if (!sender.hasPermission("drmap.command.erase")) {
                Lang.send(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            if (!(sender instanceof Player)) {
                Lang.send(sender, Lang.NOT_PLAYER);
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!Util.isDrMap(hand)) {
                Lang.send(sender, Lang.NOT_DRMAP);
                return true;
            }
            int amount = hand.getAmount();
            ItemStack blankMap = new ItemStack(Material.MAP, amount);
            player.getInventory().setItemInMainHand(blankMap);
            return true;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("author")) {
            if (!sender.hasPermission("drmap.command.info")) {
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

            PersistentDataContainer container = itemMeta.getPersistentDataContainer();

            PictureMeta.sendAuthor(sender, container, plugin);
            PictureMeta.sendCreation(sender, container, plugin);
            PictureMeta.sendPart(sender, container, plugin);
            PictureMeta.sendSource(sender, container, plugin);
            return true;
        }
        return false;
    }

    public void removeFromInventory(Player player, Material material, int removeAmount) {
        for (ItemStack is : player.getInventory().getContents()) {
            if (removeAmount <= 0) {
                return;
            }
            if (is != null) {
                if (is.getType() == material) {
                    if (is.getAmount() >= removeAmount) {
                        is.setAmount(is.getAmount() - removeAmount);
                        return;
                    } else {
                        is.setAmount(0);
                        removeAmount -= is.getAmount();
                    }
                }
            }
        }
    }

    public boolean playerHas(Player player, Material material, int count) {
        if (count == 0) {
            return true;
        }
        int playerHas = 0;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (itemStack != null && itemStack.getType() == material) {
                playerHas += itemStack.getAmount();
                if (playerHas >= count) {
                    return true;
                }
            }
        }
        return false;
    }
}