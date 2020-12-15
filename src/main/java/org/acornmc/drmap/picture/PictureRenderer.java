package org.acornmc.drmap.picture;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class PictureRenderer extends MapRenderer {
    private final java.awt.Image image;
    private boolean rendered;

    public PictureRenderer(java.awt.Image image) {
        this.image = image;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        rendered = true;
        for (int i = 0; i < canvas.getCursors().size(); i++) {
            canvas.getCursors().removeCursor(canvas.getCursors().getCursor(i));
        }
        try {
            canvas.drawImage(0, 0, image);
            player.sendMap(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}