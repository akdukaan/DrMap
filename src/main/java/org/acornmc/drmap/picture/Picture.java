package org.acornmc.drmap.picture;

import org.bukkit.Bukkit;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class Picture {
    private MapView mapView;

    public Picture(java.awt.Image image, MapView mapView) {
        this.mapView = mapView;

        for (MapRenderer render : mapView.getRenderers()) {
            mapView.removeRenderer(render);
        }
        mapView.addRenderer(new PictureRenderer(image));

        Bukkit.getOnlinePlayers().forEach(player -> player.sendMap(mapView));
    }

    public MapView getMapView() {
        return mapView;
    }
}