package org.acornmc.drmap.picture;

import org.acornmc.drmap.DrMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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

    public Image[][] downloadStretchedImage(String link, int width, int height) {
        Image[][] images = new Image[width][height];
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
            for (int w = 0; w < width; w++) {
                for (int h = 0; h < height; h++) {
                    BufferedImage bufferedImage = image.getSubimage(w * image.getWidth() / width, h * image.getHeight() / height, image.getWidth() / width, image.getHeight() / height);
                    images[w][h] = bufferedImage.getScaledInstance(128, 128, 1);
                }
            }
            return images;
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

    public Image downloadProportionalImage(String link) {
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
            if (image.getHeight() >= image.getWidth()) {
                Image resizedImage = image.getScaledInstance(128*image.getWidth()/image.getHeight(), 128, 1);
                BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = newImage.createGraphics();
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, 128, 128);
                g2d.setComposite(AlphaComposite.Src);
                g2d.drawImage(resizedImage, ((128-resizedImage.getWidth(null))/2), ((128-resizedImage.getHeight(null))/2), null);
                g2d.dispose();
                return newImage;
            } else {
                Image resizedImage = image.getScaledInstance(128, 128*image.getHeight()/image.getWidth(), 1);
                BufferedImage newImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = newImage.createGraphics();
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, 128, 128);
                g2d.setComposite(AlphaComposite.Src);
                g2d.drawImage(resizedImage, ((128-resizedImage.getWidth(null))/2), ((128-resizedImage.getHeight(null))/2), null);
                g2d.dispose();
                return newImage;
            }
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

    public Image loadImage(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            return image;
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
                    String filename = file.getName().split(".png")[0];
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