package org.acornmc.drmap.picture;

import org.acornmc.drmap.DrMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

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
            e.printStackTrace();
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
            e.printStackTrace();
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
        for (File file : files) {
            try {
                Image image = loadImage(file);
                if (image != null) {
                    String filename = file.getName().split("\\.png")[0];
                    int mapInt = Integer.parseInt(filename);
                    MapView mapView = Bukkit.getMap(mapInt);
                    if (mapView != null) {
                        addPicture(new Picture(image, mapView));
                        count++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        DrMap.getInstance().getLogger().info("Loaded " + count + " images from disk");
    }
}