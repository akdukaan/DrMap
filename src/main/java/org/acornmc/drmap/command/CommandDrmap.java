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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.acornmc.drmap.Util.sendDiscordEmbed;

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

    public void handleCreateFitContain(Player player, String url, int finalWidth, int finalHeight, Color finalBackground, int finalRequiredAmount) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            if (image == null) {
                plugin.getLogger().severe("Could not download image: " + url);
                Lang.send(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitContain(image, finalWidth, finalHeight, finalBackground);
            List<ItemStack> maps = makeMaps(images, player, url);
            if (maps == null) return;

            //Remove empty maps
            if (playerHas(player, Material.MAP, finalRequiredAmount)) {
                removeFromInventory(player, Material.MAP, finalRequiredAmount);
            } else {
                Lang.send(player, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(finalRequiredAmount)));
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

            Lang.send(player, Lang.IMAGE_CREATED);
            sendDiscordEmbed(player.getName(), url);
        }, plugin.getMainThreadExecutor());
    }

    public void handleCreateFitFill(Player player, String url, int finalWidth, int finalHeight, Color finalBackground, int finalRequiredAmount) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            // Give filled maps
            if (image == null) {
                plugin.getLogger().severe("Could not download image: " + url);
                Lang.send(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitFill(image, finalWidth, finalHeight, finalBackground);
            List<ItemStack> maps = makeMaps(images, player, url);
            if (maps == null) return;

            //Remove empty maps
            if (playerHas(player, Material.MAP, finalRequiredAmount)) {
                removeFromInventory(player, Material.MAP, finalRequiredAmount);
            } else {
                Lang.send(player, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(finalRequiredAmount)));
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

            Lang.send(player, Lang.IMAGE_CREATED);
            sendDiscordEmbed(player.getName(), url);
        }, plugin.getMainThreadExecutor());
    }

    public void handleCreateFitCover(Player player, String url, int finalWidth, int finalHeight, Color finalBackground, int finalRequiredAmount) {
        CompletableFuture.supplyAsync(() -> downloadImage(url)).whenCompleteAsync((BufferedImage image, Throwable exception) -> {
            // Give filled maps
            if (image == null) {
                plugin.getLogger().severe("Could not download image: " + url);
                Lang.send(player, Lang.ERROR_DOWNLOADING);
                return;
            }
            Image[][] images = divideImageFitCover(image, finalWidth, finalHeight, finalBackground);
            List<ItemStack> maps = makeMaps(images, player, url);
            if (maps == null) return;

            //Remove empty maps
            if (playerHas(player, Material.MAP, finalRequiredAmount)) {
                removeFromInventory(player, Material.MAP, finalRequiredAmount);
            } else {
                Lang.send(player, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(finalRequiredAmount)));
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

            Lang.send(player, Lang.IMAGE_CREATED);
            sendDiscordEmbed(player.getName() + " has created a new DrMap image", url);
        }, plugin.getMainThreadExecutor());
    }


    public boolean handleCreateCommand(CommandSender sender, String[] args) {
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
        final Color finalBackground = background;

        if (fit.equalsIgnoreCase("contain")) {
            handleCreateFitContain(player, args[1], finalWidth, finalHeight, finalBackground, finalRequiredAmount);
            return true;
        }
        if (fit.equalsIgnoreCase("fill")) {
            handleCreateFitFill(player, args[1], finalWidth, finalHeight, finalBackground, finalRequiredAmount);
            return true;
        }
        if (fit.equalsIgnoreCase("cover")) {
            handleCreateFitCover(player, args[1], finalWidth, finalHeight, finalBackground, finalRequiredAmount);
            return true;
        }
        return false;
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
            return handleCreateCommand(sender, args);
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

    private List<ItemStack> makeMaps(Image[][] images, Player player, String source) {
        List<ItemStack> maps = new LinkedList<>();
        for (int j = 0; j < images[0].length; j++) {
            for (int i = 0; i < images.length; i++) {
                MapView mapView = Bukkit.createMap(Bukkit.getWorlds().get(0));
                if (!PictureManager.INSTANCE.saveImage(images[i][j], mapView.getId())) {
                    plugin.getLogger().warning("Could not save image to disk: " + mapView.getId() + ".png");
                    return null;
                }

                Picture picture = new Picture(images[i][j], mapView);
                PictureManager.INSTANCE.addPicture(picture);

                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                if (meta == null) {
                    return null;
                }
                meta.setMapView(picture.getMapView());

                // Mark the meta
                NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, player.getUniqueId().toString());
                NamespacedKey keyCreation = new NamespacedKey(plugin, "drmap-creation");
                meta.getPersistentDataContainer().set(keyCreation, PersistentDataType.LONG, System.currentTimeMillis() / 1000L);
                NamespacedKey keyPart = new NamespacedKey(plugin, "drmap-part");
                meta.getPersistentDataContainer().set(keyPart, PersistentDataType.INTEGER_ARRAY, new int[]{i, j, images.length - 1, images[0].length - 1});
                NamespacedKey keySource = new NamespacedKey(plugin, "drmap-source");
                meta.getPersistentDataContainer().set(keySource, PersistentDataType.STRING, source);
                map.setItemMeta(meta);

                maps.add(map);
            }
        }
        return maps;
    }

    public Image[][] divideImageFitFill(BufferedImage image, int width, int height, Color finalBackground) {
        Image[][] images = new Image[width][height];
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                BufferedImage bufferedImage = image.getSubimage(w * image.getWidth() / width, h * image.getHeight() / height, image.getWidth() / width, image.getHeight() / height);
                Image stretchedPart = bufferedImage.getScaledInstance(128, 128, 1);

                BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = newImage.createGraphics();
                // Set background
                if (finalBackground != null) {
                    g2d.setPaint(finalBackground);
                    g2d.fillRect(0, 0, 128, 128);
                }
                g2d.fillRect(0, 0, 128, 128);

                g2d.drawImage(stretchedPart, 0, 0, null);
                g2d.dispose();

                images[w][h] = newImage.getScaledInstance(128, 128, 1);
            }
        }
        return images;
    }

    public Image[][] divideImageFitContain(BufferedImage image, int width, int height, Color finalBackground) {
        Image[][] images = new Image[width][height];

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int frameWidth = 128 * width;
        int frameHeight = 128 * height;
        double imageAspectRatio = (double) imageWidth / imageHeight;
        double frameAspectRatio = (double) frameWidth / frameHeight;
        int containedWidth;
        int containedHeight;
        if (imageAspectRatio >= frameAspectRatio) {
            containedWidth = frameWidth;
            containedHeight = (int) Math.floor(containedWidth / imageAspectRatio);
        } else {
            containedHeight = frameHeight;
            containedWidth = (int) Math.floor(containedHeight * imageAspectRatio);
        }

        Image resizedImage = image.getScaledInstance(containedWidth, containedHeight, Image.SCALE_DEFAULT);  // Use SCALE_SMOOTH for better quality
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

                // Draw the corresponding part of the resized image
                int heightModifier = 0;
                int widthModifier = 0;
                if (frameHeight > containedHeight) {
                    heightModifier = (frameHeight - containedHeight) / 2;
                }
                if (frameWidth > containedWidth) {
                    widthModifier = (frameWidth - containedWidth) / 2;
                }
                int sourceX = 128 * w - widthModifier ;
                int sourceY = 128 * h - heightModifier;

                Graphics2D g2d = newImage.createGraphics();
                // Set background
                if (finalBackground != null) {
                    g2d.setPaint(finalBackground);
                    g2d.fillRect(0, 0, 128, 128);
                }
                g2d.drawImage(resizedImage, 0, 0, 128, 128, sourceX, sourceY, sourceX+128, sourceY+128, null);
                g2d.dispose();

                images[w][h] = newImage;
            }
        }
        return images;
    }

    public Image[][] divideImageFitCover(BufferedImage image, int width, int height, Color finalBackground) {
        Image[][] images = new Image[width][height];

        int imageWidth = image.getWidth(); //500 //Cat750
        int imageHeight = image.getHeight(); //750 //Cat500
        int frameWidth = 128 * width; // 128 //Cat 256
        int frameHeight = 128 * height;// 256 //Cat256
        double imageAspectRatio = (double) imageWidth / imageHeight; //0.7 //Cat 1.5
        double frameAspectRatio = (double) frameWidth / frameHeight; //0.5 // Cat 1

        int scaledHeight; //goal 250 //cat250
        int scaledWidth; //goal 180 //cat300
        if (imageAspectRatio >= frameAspectRatio) { // I want it to cut off the sides
            scaledHeight = frameHeight;
            if (imageAspectRatio > 1) {
                scaledWidth = (int) Math.floor(frameWidth * imageAspectRatio);
            } else { // I dont logically understand  why I have to do this
                scaledWidth = (int) Math.floor(frameWidth / imageAspectRatio);
            }
        } else { // I want it to cut from top and bottom
            scaledWidth = frameWidth;
            if (imageAspectRatio > 1 ) {
                scaledHeight = (int) Math.floor(frameHeight * imageAspectRatio);
            } else {
                scaledHeight = (int) Math.floor(frameHeight / imageAspectRatio);
            }
        }

        Image resizedImage = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT);
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

                // Draw the corresponding part of the resized image
                int sourceX = 128 * w;
                int sourceY = 128 * h;
                if (frameWidth < scaledWidth) { // If we cut off from the sides
                    sourceX += (scaledWidth - frameWidth) / 2;
                }
                if (frameHeight < scaledHeight) {
                    sourceY += (scaledHeight - frameHeight) / 2;
                }

                Graphics2D g2d = newImage.createGraphics();
                // Set background
                if (finalBackground != null) {
                    g2d.setPaint(finalBackground);
                    g2d.fillRect(0, 0, 128, 128);
                }
                g2d.drawImage(resizedImage, 0, 0, 128, 128, sourceX, sourceY, sourceX+128, sourceY+128, null);
                g2d.dispose();

                images[w][h] = newImage;
            }
        }
        return images;
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

    public BufferedImage downloadImage(String link) {
        BufferedImage image;
        URLConnection con = null;
        InputStream in = null;
        try {
            URL url = new URL(link);
            con = url.openConnection();
            con.setConnectTimeout(500);
            con.setReadTimeout(500);
            in = con.getInputStream();
            image = ImageIO.read(in);
            return image;
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if(in != null) {
                try {
                    in.close();
                } catch(IOException ex) {
                    ex.printStackTrace();
                }
            }
            if(con != null) {
                ((HttpURLConnection) con).disconnect();
            }
        }
        return null;
    }
}