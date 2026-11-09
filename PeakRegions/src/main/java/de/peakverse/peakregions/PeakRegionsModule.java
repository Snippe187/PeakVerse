package de.peakverse.peakregions;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;

/** PeakRegions: WorldGuard-Regionen und Flags sowie WorldEdit-Schematics im Web verwalten, Regionen auf SquareMap zeigen. */
public final class PeakRegionsModule implements PeakModule {
    private static final Pattern FLAG = Pattern.compile("[A-Za-z0-9-]{1,40}");
    private ModuleContext context;

    @Override
    public String id() {
        return "peakregions";
    }

    @Override
    public String displayName() {
        return "PeakRegions";
    }

    private static boolean on(String plugin) {
        return Bukkit.getPluginManager().isPluginEnabled(plugin);
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        SchemStore schems = new SchemStore(schematicFolder());
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakRegionsModule.class.getResourceAsStream("/modules/peakregions/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                new RegionsWeb(context.web(), schems, new RegionsWeb.Host() {
                    @Override
                    public boolean worldGuard() {
                        return on("WorldGuard");
                    }

                    @Override
                    public boolean worldEdit() {
                        return on("WorldEdit") || on("FastAsyncWorldEdit");
                    }

                    @Override
                    public boolean map() {
                        return on("squaremap");
                    }

                    @Override
                    public void regionsChanged() {
                        syncMap();
                    }

                    @Override
                    public String setFlag(String world, String region, String flag, String value) {
                        if (!WgBridge.validId(region) || !FLAG.matcher(flag).matches()) return "Ungueltiger Name.";
                        if (Bukkit.getWorld(world) == null) return "Unbekannte Welt.";
                        String v = value == null ? "" : value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").strip();
                        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "region flag " + region + " -w " + world + " " + flag + (v.isEmpty() ? "" : " " + v));
                        return ok ? null : "WorldGuard hat den Befehl abgelehnt (Flag oder Wert pruefen).";
                    }
                }).register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakRegions nicht verfuegbar: " + e.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskLater(context.host(), this::syncMap, 100L);
    }

    /** Schematics liegen bei WorldEdit (oder FastAsyncWorldEdit); die config.yml kann einen eigenen Ordner setzen. */
    private Path schematicFolder() {
        String custom = context.config().getString("schematics-folder", "");
        if (custom != null && !custom.isBlank()) return Path.of(custom);
        Path plugins = context.dataFolder().toPath().toAbsolutePath().getParent().getParent();
        Path fawe = plugins.resolve("FastAsyncWorldEdit").resolve("schematics");
        return Files.isDirectory(fawe) && !Files.isDirectory(plugins.resolve("WorldEdit").resolve("schematics")) ? fawe : plugins.resolve("WorldEdit").resolve("schematics");
    }

    private void syncMap() {
        if (context == null || !on("WorldGuard") || !on("squaremap")) return;
        try {
            MapRegions.update();
        } catch (RuntimeException | LinkageError e) {
            context.logger().warning("SquareMap-Regionen konnten nicht gesetzt werden: " + e);
        }
    }

    @Override
    public void contributePack(PackSink sink) {
        // Keine Pack-Dateien
    }

    @Override
    public void disable() {
        if (context == null) return;
        if (on("WorldGuard") && on("squaremap")) {
            try {
                MapRegions.clear();
            } catch (RuntimeException | LinkageError e) {
                // Karte schon beendet
            }
        }
        context = null;
    }
}