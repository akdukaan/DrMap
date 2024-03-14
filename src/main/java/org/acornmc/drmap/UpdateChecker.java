package org.acornmc.drmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.bukkit.Bukkit.getLogger;

public class UpdateChecker {

    public static void checkForUpdates(JavaPlugin plugin) {
        // Get current version
        String usingVersionFull = plugin.getDescription().getVersion();
        String usingVersionClipped = usingVersionFull;
        if (usingVersionClipped.contains("-")) {
            usingVersionClipped = usingVersionClipped.split("-")[0];
        }

        // Get latest version from Modrinth
        final JsonElement json = getJsonAs("gpflags/" + usingVersionFull);
        if (json == null) return;
        String latestVersionFull = json.getAsJsonArray().get(0).getAsJsonObject().get("version_number").getAsString();
        String latestVersionClipped = latestVersionFull;
        if (latestVersionClipped.contains("-")) {
            latestVersionClipped = latestVersionClipped.split("-")[0];
        }

        // If on snapshot of same version
        if (usingVersionClipped.equals(latestVersionClipped) && usingVersionFull.contains("SNAPSHOT") && !latestVersionFull.contains("SNAPSHOT")) {
            getLogger().warning("You are using DrMap version " + usingVersionFull + " which is outdated. Please update to version " + latestVersionFull + " at https://modrinth.com/plugin/drmap.");
            return;
        }
        
        // Compare more complicated versions
        DefaultArtifactVersion usingDAV = new DefaultArtifactVersion(usingVersionClipped);
        DefaultArtifactVersion latestDAV = new DefaultArtifactVersion(latestVersionClipped);
        if (usingDAV.compareTo(latestDAV) < 0) {
            getLogger().warning("You are using DrMap version " + usingVersionFull + " which is outdated. Please update to version " + latestVersionFull + " at https://modrinth.com/plugin/drmap.");
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
}