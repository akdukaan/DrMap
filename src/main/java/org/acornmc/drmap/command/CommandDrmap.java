package org.acornmc.drmap.command;

import org.acornmc.drmap.DrMap;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.PictureMeta;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.StringUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.acornmc.drmap.Util.*;

public class CommandDrmap implements TabExecutor {
    private final DrMap plugin;

    public CommandDrmap(DrMap plugin) {
        this.plugin = plugin;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            return false; // show usage
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("drmap.command.reload")) {
                Lang.sendMessage(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            Config.reload(plugin);
            Lang.reload(plugin);

            Lang.sendMessage(sender, "&a" + plugin.getName() + " v" + plugin.getDescription().getVersion() + " reloaded");
            return true;
        }

        if (args[0].equalsIgnoreCase("create") && args.length > 1) {
            return handleCreateCommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("erase")) {
            if (!sender.hasPermission("drmap.command.erase")) {
                Lang.sendMessage(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            if (!(sender instanceof Player)) {
                Lang.sendMessage(sender, Lang.NOT_PLAYER);
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!isDrMap(hand)) {
                Lang.sendMessage(sender, Lang.NOT_DRMAP);
                return true;
            }
            int amount = hand.getAmount();
            ItemStack blankMap = new ItemStack(Material.MAP, amount);
            player.getInventory().setItemInMainHand(blankMap);
            return true;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("author")) {
            if (!sender.hasPermission("drmap.command.info")) {
                Lang.sendMessage(sender, Lang.COMMAND_NO_PERMISSION);
                return true;
            }
            if (!(sender instanceof Player)) {
                Lang.sendMessage(sender, Lang.NOT_PLAYER);
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            Material usingMaterial = hand.getType();
            if (usingMaterial != Material.FILLED_MAP) {
                Lang.sendMessage(sender, Lang.NOT_DRMAP);
                return true;
            }

            ItemMeta itemMeta = hand.getItemMeta();
            if (itemMeta == null) {
                Lang.sendMessage(sender, Lang.NOT_DRMAP);
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
            boolean includedFit = false;
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
                if (args[i].toLowerCase().startsWith("fit:")) {
                    includedFit = true;
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
            if (!includedFit) {
                list.add("fit:");
            }
            return StringUtil.copyPartialMatches(args[args.length - 1], list, new ArrayList<>());
        }

        return list;
    }

    public boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("drmap.command.create")) {
            Lang.sendMessage(sender, Lang.COMMAND_NO_PERMISSION);
            return true;
        }

        if (!(sender instanceof Player)) {
            Lang.sendMessage(sender, Lang.NOT_PLAYER);
            return true;
        }

        // Check if they have a map
        Player player = (Player) sender;
        if (!player.getInventory().contains(Material.MAP)) {
            Lang.sendMessage(sender, Lang.MUST_HAVE_MAP);
            return true;
        }

        int width = 1;
        int height = 1;
        Color background = null;
        String fit = "contain";

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                if (args[i].toLowerCase().startsWith("width:")) {
                    try {
                        width = Integer.parseInt(args[i].toLowerCase().replaceFirst("width:", ""));
                        if (width <= 0) {
                            width = 1;
                        }
                    } catch (Exception ignored) {}
                } else if (args[i].toLowerCase().startsWith("height:")) {
                    try {
                        height = Integer.parseInt(args[i].toLowerCase().replaceFirst("height:", ""));
                        if (height <= 0) {
                            height = 1;
                        }
                    } catch (Exception ignored) {}
                } else if (args[i].toLowerCase().startsWith("background:")) {
                    try {
                        background = Color.decode(args[i].toLowerCase().replaceFirst("background:", ""));
                    } catch (Exception ignored) {}
                } else if (args[i].toLowerCase().startsWith("fit:")) {
                    try {
                        fit = args[i].toLowerCase().replaceFirst("fit:", "");
                        if (!fit.matches("(contain|fill|cover)")) {
                            fit="contain";
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Calculate required amount
        int requiredAmount = width * height;
        if (player.getGameMode().equals(GameMode.CREATIVE)) {
            requiredAmount = 0;
        }

        // Check that they have enough
        // We will check again after the image is downloaded,
        // but I don't want to download images if they don't even have enough maps
        if (!(playerHas(player, Material.MAP, requiredAmount))) {
            Lang.sendMessage(sender, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(requiredAmount)));
            return true;
        }

        final int finalWidth = width;
        final int finalHeight = height;
        final Color finalBackground = background;

        if (fit.equalsIgnoreCase("contain")) {
            handleCreateFitContain(player, args[1], finalWidth, finalHeight, finalBackground);
            return true;
        }
        if (fit.equalsIgnoreCase("fill")) {
            handleCreateFitFill(player, args[1], finalWidth, finalHeight, finalBackground);
            return true;
        }
        if (fit.equalsIgnoreCase("cover")) {
            handleCreateFitCover(player, args[1], finalWidth, finalHeight, finalBackground);
            return true;
        }
        return false;
    }

    public void handleCreateFitContain(Player player, String url, int finalWidth, int finalHeight, Color finalBackground) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            if (image == null) {
                Lang.sendMessage(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitContain(image, finalWidth, finalHeight, finalBackground);
            fillMaps(player, images, url);
        }, plugin.getMainThreadExecutor());
    }

    public void handleCreateFitFill(Player player, String url, int finalWidth, int finalHeight, Color finalBackground) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            // Give filled maps
            if (image == null) {
                Lang.sendMessage(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitFill(image, finalWidth, finalHeight, finalBackground);
            fillMaps(player, images, url);
        }, plugin.getMainThreadExecutor());
    }

    public void handleCreateFitCover(Player player, String url, int finalWidth, int finalHeight, Color finalBackground) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            // Give filled maps
            if (image == null) {
                Lang.sendMessage(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitCover(image, finalWidth, finalHeight, finalBackground);
            fillMaps(player, images, url);
        }, plugin.getMainThreadExecutor());
    }

}