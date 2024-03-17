package org.acornmc.drmap;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.acornmc.drmap.configuration.Config;
import org.acornmc.drmap.configuration.Lang;
import org.acornmc.drmap.picture.Picture;
import org.acornmc.drmap.picture.PictureManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

public class Util {

    public static boolean isDrMap(ItemStack item) {
        if (item == null) return false;
        final Material frameContent = item.getType();
        if (frameContent != Material.FILLED_MAP) return false;
        Plugin plugin = DrMap.getInstance();
        NamespacedKey key = new NamespacedKey(plugin, "drmap-author");
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return false;
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.STRING);

    }

    public static void sendDiscordEmbed(String playerName, String link) {
        if (Config.DISCORD_LOGGING_WEBHOOK.equals("")) return;
        try {
            URL url = new URL(Config.DISCORD_LOGGING_WEBHOOK);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            String message = Lang.IMAGE_CREATED_BY_PLAYER.replace("{player}", playerName);

            String jsonPayload = "{\n" +
                    "  \"content\": \"\",\n" +
                    "  \"embeds\": [\n" +
                    "    {\n" +
                    "      \"title\": \"DrMap\",\n" +
                    "      \"description\": \"" + message + "\",\n" +
                    "      \"color\": 16711680,\n" +
                    "      \"image\": {\n" +
                    "        \"url\": \"" + link + "\"\n" +
                    "      }\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                DrMap.getInstance().getLogger().info("Sent Discord embed.");
            } else {
                DrMap.getInstance().getLogger().warning("Failed to send Discord embed. Response code: " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Check with protection plugins to see if the player can edit the block
     * @param player
     * @param location
     * @param material
     * @return null if the player is allowed to edit the block or an error message if they can't
     */
    public static String getBlockProtection(Player player, Location location, Material material) {
        PluginManager pluginManager = Bukkit.getServer().getPluginManager();

        // Check with GriefPrevention
        String griefPreventionProtection = checkGriefPrevention(pluginManager, player, location, material);
        if (griefPreventionProtection != null)
            return griefPreventionProtection;

        // Check with WorldGuard
        String worldGuardProtection = checkWorldGuard(pluginManager, player, location);
        if (worldGuardProtection != null)
            return worldGuardProtection;

        return null;
    }

    private static String checkGriefPrevention(PluginManager pluginManager, Player player, Location location, Material material) {
        Plugin griefPreventionPlugin = pluginManager.getPlugin("GriefPrevention");
        if (griefPreventionPlugin != null && griefPreventionPlugin.isEnabled())
            return GriefPrevention.instance.allowBuild(player, location, material);

        return null;
    }

    private static String checkWorldGuard(PluginManager pluginManager, Player player, Location location) {
        Plugin worldGuardPlugin = pluginManager.getPlugin("WorldGuard");
        if (worldGuardPlugin != null && worldGuardPlugin.isEnabled()) {
            WorldGuard worldGuard = WorldGuard.getInstance();
            WorldGuardPlatform platform = worldGuard.getPlatform();

            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionContainer container = platform.getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location worldEditLocation = BukkitAdapter.adapt(location);

            // Check if the player has bypass permission
            if (platform.getSessionManager().hasBypass(localPlayer, localPlayer.getWorld())) {
                return null;
            }

            // Check if the player has build permission
            if (!query.testBuild(worldEditLocation, localPlayer)) {
                return "You do not have permission to build here!";
            }
        }
        return null;
    }

    public static List<ItemStack> makeMaps(Image[][] images, Player player, String source) {
        List<ItemStack> maps = new LinkedList<>();
        DrMap plugin = DrMap.getInstance();
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

    public static Image[][] divideImageFitFill(BufferedImage image, int width, int height, Color finalBackground) {
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

    public static Image[][] divideImageFitContain(BufferedImage image, int width, int height, Color finalBackground) {
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

    public static Image[][] divideImageFitCover(BufferedImage image, int width, int height, Color finalBackground) {
        Image[][] images = new Image[width][height];

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        int frameWidth = 128 * width;
        int frameHeight = 128 * height;
        double imageAspectRatio = (double) imageWidth / imageHeight;
        double frameAspectRatio = (double) frameWidth / frameHeight;

        int scaledHeight;
        int scaledWidth;
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

    public static void removeFromInventory(Player player, Material material, int removeAmount) {
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

    public static boolean playerHas(Player player, Material material, int count) {
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

    public static BufferedImage downloadImage(String link) {
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

    public static void fillMaps(Player player, Image[][] images, String url) {
        List<ItemStack> maps = makeMaps(images, player, url);
        if (maps == null) return;
        int amount = maps.size();

        //Remove empty maps
        if (playerHas(player, Material.MAP, amount)) {
            removeFromInventory(player, Material.MAP, amount);
        } else {
            Lang.send(player, Lang.NOT_ENOUGH_MAPS.replace("{required}", String.valueOf(amount)));
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
    }

}
