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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import sun.net.www.http.HttpClient;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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

    public static void sendDiscordEmbed(String message, String link) {
        try {
            URL url = new URL(Config.DISCORD_LOGGING_WEBHOOK);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

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
    // Check with protection plugins to see if the player can edit the block
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
}
