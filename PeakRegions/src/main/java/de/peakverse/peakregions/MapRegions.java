package de.peakverse.peakregions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/** Zeigt WorldGuard-Regionen (Quader) als Flaechen auf SquareMap. Nur aufrufen, wenn WorldGuard und SquareMap da sind. */
public final class MapRegions {
    private static final Key LAYER = Key.of("peakregions");
    private static final Map<String, SimpleLayerProvider> PROVIDERS = new HashMap<>();

    private MapRegions() {}

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static void update() {
        for (World w : Bukkit.getWorlds()) {
            MapWorld mw = SquaremapProvider.get().getWorldIfEnabled(xyz.jpenilla.squaremap.api.BukkitAdapter.worldIdentifier(w)).orElse(null);
            RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(w));
            if (mw == null || rm == null) continue;
            SimpleLayerProvider provider = PROVIDERS.computeIfAbsent(w.getName(), k -> {
                SimpleLayerProvider p = SimpleLayerProvider.builder("Regionen").showControls(true).defaultHidden(true).build();
                mw.layerRegistry().register(LAYER, p);
                return p;
            });
            provider.clearMarkers();
            for (ProtectedRegion r : rm.getRegions().values()) {
                if (r.getType() != RegionType.CUBOID) continue;
                Marker m = Marker.rectangle(Point.of(r.getMinimumPoint().x(), r.getMinimumPoint().z()), Point.of(r.getMaximumPoint().x() + 1, r.getMaximumPoint().z() + 1));
                m.markerOptions(MarkerOptions.builder().fillColor(new Color(0xFF, 0xAA, 0x33)).fillOpacity(0.25).strokeColor(new Color(0xCC, 0x77, 0x11))
                        .hoverTooltip("<b>" + esc(r.getId()) + "</b>").build());
                provider.addMarker(Key.of("region_" + r.getId().replaceAll("[^A-Za-z0-9_-]", "_")), m);
            }
        }
    }

    public static void clear() {
        for (World w : Bukkit.getWorlds()) {
            MapWorld mw = SquaremapProvider.get().getWorldIfEnabled(xyz.jpenilla.squaremap.api.BukkitAdapter.worldIdentifier(w)).orElse(null);
            if (mw != null && PROVIDERS.containsKey(w.getName())) mw.layerRegistry().unregister(LAYER);
        }
        PROVIDERS.clear();
    }
}
