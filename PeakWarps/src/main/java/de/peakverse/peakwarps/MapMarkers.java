package de.peakverse.peakwarps;

import java.awt.Color;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;

/**
 * Zeigt Warps als Marker auf SquareMap. Nur aufrufen, wenn das Plugin da ist (die Klassen werden sonst nicht gefunden).
 * Ebenen gelten fuer alle Betrachter der Karte; darum erscheinen nur Warps ohne Zugriffsbeschraenkung und mit gesetztem "auf der Karte zeigen".
 */
public final class MapMarkers {
    private static final Key LAYER = Key.of("peakwarps");
    private static final java.util.Map<String, SimpleLayerProvider> PROVIDERS = new java.util.HashMap<>();

    private MapMarkers() {}

    /** Setzt alle Marker neu. */
    public static void update(List<WarpStore.Warp> warps) {
        for (World w : Bukkit.getWorlds()) {
            MapWorld mw = SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(w)).orElse(null);
            if (mw == null) continue;
            SimpleLayerProvider provider = PROVIDERS.computeIfAbsent(w.getName(), k -> {
                SimpleLayerProvider p = SimpleLayerProvider.builder("Warps").showControls(true).defaultHidden(false).build();
                mw.layerRegistry().register(LAYER, p);
                return p;
            });
            provider.clearMarkers();
            for (WarpStore.Warp warp : warps) {
                if (warp.restricted || !warp.onMap || !warp.world.equals(w.getName())) continue;
                Marker m = Marker.circle(Point.of(warp.x, warp.z), 6);
                m.markerOptions(MarkerOptions.builder()
                        .fillColor(new Color(0x55, 0xAA, 0xFF))
                        .fillOpacity(0.7)
                        .strokeColor(new Color(0x22, 0x55, 0xAA))
                        .hoverTooltip("<b>" + WarpStore.html(warp.name) + "</b>" + (warp.description.isEmpty() ? "" : "<br>" + WarpStore.html(warp.description)))
                        .build());
                provider.addMarker(Key.of("warp_" + warp.name), m);
            }
        }
    }

    public static void clear() {
        for (World w : Bukkit.getWorlds()) {
            MapWorld mw = SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(w)).orElse(null);
            if (mw != null && PROVIDERS.containsKey(w.getName())) mw.layerRegistry().unregister(LAYER);
        }
        PROVIDERS.clear();
    }
}