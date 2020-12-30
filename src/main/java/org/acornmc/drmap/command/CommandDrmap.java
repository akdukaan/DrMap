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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CommandDrmap implements TabExecutor {
    private final DrMap plugin;

    public CommandDrmap(DrMap plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && (sender.hasPermission("drmap.create") || sender.hasPermission("drmap.reload"))) {
            ArrayList<String> list = new ArrayList<>();
            if (sender.hasPermission("drmap.create")) {
                list.add("create");
            }
            if (sender.hasPermission("drmap.reload")) {
                list.add("reload");
            }
            return list.stream()
                    .filter(arg -> arg.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
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

            Image image = PictureManager.INSTANCE.downloadImage(args[1]);
            if (image == null) {
                plugin.getLogger().severe("Could not download image: " + args[1]);
                Lang.send(sender, Lang.ERROR);
                return true;
            }

            MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));

            if (!PictureManager.INSTANCE.saveImage(image, mapView.getId())) {
                plugin.getLogger().severe("Could not save image to disk: " + args[1] + " -> " + mapView.getId() + ".png");
                Lang.send(sender, Lang.ERROR);
                return true;
            }

            Picture picture = new Picture(image, mapView);
            PictureManager.INSTANCE.addPicture(picture);

            ItemStack map = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) map.getItemMeta();
            if (meta == null) {
                Lang.send(sender, Lang.ERROR);
                return true;
            }
            meta.setMapView(picture.getMapView());
            NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
            map.setItemMeta(meta);

            // Remove map
            Inventory inventory = player.getInventory();
            int size = inventory.getSize();
            int slot = 0;
            while (slot < size) {
                ItemStack is = inventory.getItem(slot);
                if (is != null) {
                    if (Material.MAP == is.getType()) {
                        if (is.getAmount() == 1) {
                            inventory.clear(slot);
                        } else {
                            is.setAmount(is.getAmount()-1);
                        }
                        slot = size;
                    }
                }
                slot++;
            }

            // Give map
            player.getInventory().addItem(map).forEach((index, item) -> {
                Item drop = player.getWorld().dropItem(player.getLocation(), item);
                drop.setPickupDelay(0);
                drop.setOwner(player.getUniqueId());
            });
            Lang.send(sender, Lang.IMAGE_CREATED);
            return true;
        }
        return false;
    }
}