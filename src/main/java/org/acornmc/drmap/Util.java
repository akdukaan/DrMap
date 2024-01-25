package org.acornmc.drmap;

import org.acornmc.drmap.configuration.Config;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import sun.net.www.http.HttpClient;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Util {

    public static boolean isDrMap(ItemStack itemStack) {
        Material material = itemStack.getType();
        if (material != Material.FILLED_MAP) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(DrMap.getInstance(), "drmap-author");
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.STRING);
    }

    public static void sendDiscordEmbed(String message, String link) {
        try {
            URL url = new URL(Config.WEBHOOK_URL);
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


}
