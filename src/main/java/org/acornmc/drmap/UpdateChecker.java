package org.acornmc.drmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.bukkit.Bukkit.getLogger;

public class UpdateChecker {

    public static void checkForUpdates(JavaPlugin plugin) {
        // Get current version
        String usingVersion = plugin.getDescription().getVersion();

        // Get latest version from Modrinth
        final JsonElement json = getJsonAs("gpflags/" + usingVersion);
        if (json == null) return;
        String latestVersion = json.getAsJsonArray().get(0).getAsJsonObject().get("version_number").getAsString();

        // Compare more complicated versions
        if (compareVersions(usingVersion,latestVersion) < 0) {
            getLogger().warning("You are using DrMap version " + usingVersion + " which is outdated. Please update to version " + latestVersion + " at https://modrinth.com/plugin/drmap.");
        }
    }

    private static JsonElement getJsonAs(String userAgent) {
        final HttpURLConnection connection;
        final JsonElement json;
        try {
            connection = (HttpURLConnection) new URL("https://api.modrinth.com/v2/project/MCEWz23F/version").openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", userAgent);
            if (connection.getResponseCode() == 404) return null;
            json = new JsonParser().parse(new InputStreamReader(connection.getInputStream()));
        } catch (final IOException e) {
            e.printStackTrace();
            return null;
        }
        connection.disconnect();
        return json;
    }

    /**
     * Compares two version strings to determine which is newer
     * @param versionA
     * @param versionB
     * @return a positive number if version A is newer
     *         0                 if versions are the same or could not be determined
     *         a negative number if version B is newer
     */
    public static int compareVersions(String versionA, String versionB) {
        // Most common case
        if (versionA.equals(versionB)) return 0;

        // Split the version strings into arrays of integers
        String[] versionAComponents = versionA.split("\\.");
        String[] versionBComponents = versionB.split("\\.");

        // Iterate through the corresponding elements of the arrays
        int max = Math.max(versionAComponents.length, versionBComponents.length);
        for (int i = 0; i < max; i++) {
            int versionANum = 0;
            boolean aIsNumber = true;
            if (i < versionAComponents.length) {
                try {
                    versionANum = Integer.parseInt(versionAComponents[i]);
                } catch (Exception e) {
                    aIsNumber = false;
                    versionANum = Integer.parseInt(versionAComponents[i].replaceAll("[^0-9]", ""));
                }
            }
            int versionBNum = 0;
            boolean bIsNumber = true;
            if (i < versionAComponents.length) {
                try {
                    versionBNum = Integer.parseInt(versionBComponents[i]);
                } catch (Exception e) {
                    bIsNumber = false;
                    versionBNum = Integer.parseInt(versionBComponents[i].replaceAll("[^0-9]", ""));
                }
            }
            int result = Integer.compare(versionANum, versionBNum);
            if (result != 0) return result;
            if (bIsNumber && !aIsNumber) return -1;
            if (aIsNumber && !bIsNumber) return 1;
        }

        // They aren't the exact same but the numeric parts are the same; we can't tell.
        return 0;
    }
}