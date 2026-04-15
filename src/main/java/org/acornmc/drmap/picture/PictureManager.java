package org.acornmc.drmap.picture;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import org.acornmc.drmap.DrMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PictureManager {
    public static PictureManager INSTANCE = new PictureManager();

    private Set<Picture> pictures = new HashSet<>();

    public void addPicture(Picture picture) {
        pictures.add(picture);
    }

    public void sendAllMaps(Player player) {
        pictures.forEach(picture -> player.sendMap(picture.getMapView()));
    }

    public Image loadImage(File file) {
        try {
            return ImageIO.read(file);
        } catch (Exception e) {
            DrMap.getInstance().getLogger().log(Level.WARNING, "Failed to load image: " + file, e);
        }
        return null;
    }

    public boolean saveImage(Image image, int id) {
        try {
            File dir = new File(DrMap.getInstance().getDataFolder(), "images");
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            BufferedImage bufImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gfx = bufImg.createGraphics();
            gfx.drawImage(image, 0, 0, null);
            gfx.dispose();
            ImageIO.write(bufImg, "png", new File(dir, id + ".png"));
            return true;
        } catch (Exception e) {
            DrMap.getInstance().getLogger().log(Level.WARNING, "Failed to save image: " + id, e);
        }
        return false;
    }

    public void loadPictures() {
        pictures.clear();
        File dir = new File(DrMap.getInstance().getDataFolder(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".png"));
        if (files == null) {
            return;
        }
        int count = 0;
        int highestId = 0;
        for (File file : files) {
            try {
                Image image = loadImage(file);
                if (image != null) {
                    String filename = file.getName().split("\\.png")[0];
                    int mapInt = Integer.parseInt(filename);
                    highestId = Math.max(highestId, mapInt);
                    MapView mapView = Bukkit.getMap(mapInt);
                    if (mapView != null) {
                        addPicture(new Picture(image, mapView));
                        count++;
                    }
                }
            } catch (Exception e) {
                DrMap.getInstance().getLogger().log(Level.WARNING, "Failed to load image: " + file, e);
            }
        }

        bumpMapId(highestId);
        DrMap.getInstance().getLogger().info("Loaded " + count + " images from disk");
    }

    public static boolean bumpMapId(int highestId) {
        if (highestId == 0)
            return true;

        try {
            String bukkitVer = Bukkit.getBukkitVersion();
            Matcher verMatch = Pattern.compile("^(?<major>\\d+)(?:\\.(?<minor>\\d+)(?:\\.(?<patch>\\d+))?)?")
                    .matcher(bukkitVer);
            if (!verMatch.find())
                throw new RuntimeException("Failed to resolve minecraft version: " + bukkitVer);
            int major = Integer.parseInt(verMatch.group("major"));
            int minor = verMatch.group("minor") == null ? 0 : Integer.parseInt(verMatch.group("minor"));
            int patch = verMatch.group("patch") == null ? 0 : Integer.parseInt(verMatch.group("patch"));

            if (major >= 26) {
                return bumpMapIdModern(highestId);
            } else {
                return bumpMapIdLegacy(highestId);
            }
        } catch (Exception e) {
            DrMap.getInstance().getLogger().log(Level.SEVERE, "Failed to bump map id: " + highestId, e);
            return false;
        }
    }

    private static boolean bumpMapIdModern(int highestId) throws Exception {
        Class<?> mcServerClazz = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> savedDataTypeClazz = Class.forName("net.minecraft.world.level.saveddata.SavedDataType");
        Class<?> mapIndexClazz = Class.forName("net.minecraft.world.level.saveddata.maps.MapIndex");

        // mapIndex = MinecraftServer.getServer().getDataStorage().computeIfAbsent(MapIndex.TYPE)
        Object mcServer = mcServerClazz.getMethod("getServer").invoke(null);
        Object savedDataStorage = mcServerClazz.getMethod("getDataStorage").invoke(mcServer);
        Object mapIndexType = mapIndexClazz.getField("TYPE").get(null);
        Object mapIndex = savedDataStorage.getClass().getMethod("computeIfAbsent", savedDataTypeClazz).invoke(savedDataStorage, mapIndexType);

        // get the private lastMapId field of the returned MapIndex representing the current highest map id
        Field f_mapIndex_lastMapId = mapIndexClazz.getDeclaredField("lastMapId");
        f_mapIndex_lastMapId.setAccessible(true);

        int last = f_mapIndex_lastMapId.getInt(mapIndex);
        if (last >= highestId)
            return true;

        // set to value-1 as the following call bumps by 1 and marks it for saving
        // even if getNextMapId fails, the above branch ensures that the set value is at least the previous one.
        f_mapIndex_lastMapId.set(mapIndex, highestId - 1);
        Object mapId = mapIndexClazz.getMethod("getNextMapId").invoke(mapIndex);

        // rather than use lastMapId, check the returned MapId for redundancy
        int current = (int) mapId.getClass().getMethod("id").invoke(mapId);
        if (current != highestId)
            throw new IllegalStateException("Failed to bump map id. expected=" + highestId + " got=" + current + " previous=" + last);
        DrMap.getInstance().getLogger().info("Updated current map ID from " + last + " to " + current + ".");
        return true;
    }

    private static boolean bumpMapIdLegacy(int highestId) throws Exception {
        World mainWorld = Bukkit.getWorlds().getFirst();
        Path mapIdsFile = mainWorld.getWorldFolder().toPath().resolve("data").resolve("idcounts.dat");

        // If the map ids file doesn't exist, creating a map with the API should hopefully create it.
        if (!Files.exists(mapIdsFile)) {
            Bukkit.createMap(mainWorld);
            if (!Files.exists(mapIdsFile)) {
                DrMap.getInstance().getLogger().warning("Could not bump map ID to: " + highestId + ". Could not find idcounts.dat: " + mapIdsFile);
                return false;
            }
        }

        // Step 1: Read the Gzip'd NBT data
        CompoundBinaryTag nbt = BinaryTagIO.reader().read(mapIdsFile, BinaryTagIO.Compression.GZIP);

        // Step 2: Verify required fields
        CompoundBinaryTag nbtData = nbt.getCompound("data");
        if (nbtData == null) {
            DrMap.getInstance().getLogger().severe("Missing data(compound) field in: " + nbt + " for: " + mapIdsFile);
            return false;
        }
        if (!(nbtData.get("map") instanceof IntBinaryTag)) {
            DrMap.getInstance().getLogger().severe("Missing map(int) field in: data." + nbtData + " for: " + mapIdsFile);
            return false;
        }

        int mapId = nbtData.getInt("map");
        if (mapId >= highestId)
            return true;

        // Step 3: Bump map ID
        nbtData.putInt("map", highestId);
        // write to tmp file to prevent corrupting the servers id counter if we fail
        Path idCountsTmp = mapIdsFile.resolveSibling(mapIdsFile.getFileName() + ".drmap.tmp");
        BinaryTagIO.writer().write(nbt, idCountsTmp, BinaryTagIO.Compression.GZIP);
        Files.move(idCountsTmp, mapIdsFile, StandardCopyOption.REPLACE_EXISTING);
        DrMap.getInstance().getLogger().info("Updated " + mapIdsFile.getFileName() + " from " + mapId + " to " + highestId + ".");
        return true;
    }

}
