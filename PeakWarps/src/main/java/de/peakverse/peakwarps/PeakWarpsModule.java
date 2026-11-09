package de.peakverse.peakwarps;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitTask;

/** PeakWarps: Warp-Punkte mit Recht je Warp, Befehle /warp /warps /setwarp /delwarp, Web-Verwaltung und Marker auf SquareMap. */
public final class PeakWarpsModule implements PeakModule, CommandExecutor, TabCompleter {
    private ModuleContext context;
    private WarpStore store;
    private final Set<String> registeredPerms = new HashSet<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    @Override
    public String id() {
        return "peakwarps";
    }

    @Override
    public String displayName() {
        return "PeakWarps";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        store = new WarpStore(context.dataFolder().toPath().resolve("warps.json"));
        for (String n : List.of("warp", "warps", "setwarp", "delwarp")) {
            PluginCommand c = context.host().getCommand(n);
            if (c != null) {
                c.setExecutor(this);
                c.setTabCompleter(this);
            }
        }
        syncPermissions();
        syncMap();
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakWarpsModule.class.getResourceAsStream("/modules/peakwarps/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                new WarpsWeb(context.web(), store, new WarpsWeb.Host() {
                    @Override
                    public List<String> worlds() {
                        List<String> l = new ArrayList<>();
                        for (World w : Bukkit.getWorlds()) l.add(w.getName());
                        return l;
                    }

                    @Override
                    public WarpStore.Warp positionOf(String player) {
                        Player p = Bukkit.getPlayerExact(player);
                        if (p == null) return null;
                        WarpStore.Warp w = new WarpStore.Warp();
                        Location l = p.getLocation();
                        w.world = l.getWorld().getName();
                        w.x = Math.round(l.getX() * 10) / 10.0;
                        w.y = Math.round(l.getY() * 10) / 10.0;
                        w.z = Math.round(l.getZ() * 10) / 10.0;
                        w.yaw = l.getYaw();
                        w.pitch = l.getPitch();
                        return w;
                    }

                    @Override
                    public List<String> onlinePlayers() {
                        List<String> l = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
                        return l;
                    }

                    @Override
                    public void changed() {
                        syncPermissions();
                        syncMap();
                    }

                    @Override
                    public boolean mapAvailable() {
                        return mapOn();
                    }
                }).register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakWarps nicht verfuegbar: " + e.getMessage());
            }
        }
    }

    private static boolean mapOn() {
        return Bukkit.getPluginManager().isPluginEnabled("squaremap");
    }

    /** Fuer jeden Warp das Recht peakwarps.warp.&lt;name&gt; anmelden (erscheint in Rechte-Listen); Standard: aus. */
    private void syncPermissions() {
        Set<String> want = new HashSet<>();
        for (WarpStore.Warp w : store.all()) want.add("peakwarps.warp." + w.name);
        for (String p : new ArrayList<>(registeredPerms)) {
            if (!want.contains(p)) {
                Bukkit.getPluginManager().removePermission(p);
                registeredPerms.remove(p);
            }
        }
        for (String p : want) {
            if (registeredPerms.add(p) && Bukkit.getPluginManager().getPermission(p) == null) {
                Bukkit.getPluginManager().addPermission(new Permission(p, "Warp " + p.substring("peakwarps.warp.".length()) + " benutzen", PermissionDefault.FALSE));
            }
        }
    }

    private void syncMap() {
        if (!mapOn()) return;
        try {
            MapMarkers.update(store.all());
        } catch (RuntimeException | LinkageError e) {
            context.logger().warning("SquareMap-Marker konnten nicht gesetzt werden: " + e);
        }
    }

    private boolean allowed(Player p, WarpStore.Warp w) {
        return !w.restricted || p.hasPermission("peakwarps.admin") || p.hasPermission("peakwarps.warp." + w.name);
    }

    @Override
    public void contributePack(PackSink sink) {
        // Keine Pack-Dateien
    }

    @Override
    public void disable() {
        if (context == null) return;
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        if (mapOn()) {
            try {
                MapMarkers.clear();
            } catch (RuntimeException | LinkageError e) {
                // Karte schon beendet
            }
        }
        for (String p : registeredPerms) Bukkit.getPluginManager().removePermission(p);
        registeredPerms.clear();
        context = null;
    }

    // ---- Befehle ----

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "warps" -> {
                List<String> names = new ArrayList<>();
                for (WarpStore.Warp w : store.all()) if (!(sender instanceof Player p) || allowed(p, w)) names.add(w.name);
                sender.sendMessage(names.isEmpty() ? "Keine Warps vorhanden." : "Warps: " + String.join(", ", names));
            }
            case "warp" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Nur im Spiel.");
                    return true;
                }
                if (a.length < 1) {
                    sender.sendMessage("/warp <name>  (Liste: /warps)");
                    return true;
                }
                WarpStore.Warp w = store.get(a[0]);
                if (w == null || !allowed(p, w)) {
                    sender.sendMessage("Diesen Warp gibt es nicht oder du darfst ihn nicht benutzen.");
                    return true;
                }
                teleport(p, w);
            }
            case "setwarp" -> {
                if (!sender.hasPermission("peakwarps.admin") || !(sender instanceof Player p)) {
                    sender.sendMessage("Nur Operatoren im Spiel.");
                    return true;
                }
                // Klammern wie in "/setwarp [Name]" und Leerzeichen im Namen werden verziehen
                String raw = String.join("_", a).replaceAll("[\\[\\]<>]", "").toLowerCase(Locale.ROOT);
                if (raw.isEmpty()) {
                    sender.sendMessage("Benutzung: /setwarp <name>  (setzt den Warp an deiner Position)");
                    return true;
                }
                if (!WarpStore.validName(raw)) {
                    sender.sendMessage("Ungueltiger Name '" + raw + "'. Erlaubt: a-z, 0-9, _ und -, bis 24 Zeichen (keine Umlaute).");
                    return true;
                }
                a = new String[] {raw};
                WarpStore.Warp w = new WarpStore.Warp();
                Location l = p.getLocation();
                w.name = a[0].toLowerCase(Locale.ROOT);
                w.world = l.getWorld().getName();
                w.x = l.getX();
                w.y = l.getY();
                w.z = l.getZ();
                w.yaw = l.getYaw();
                w.pitch = l.getPitch();
                WarpStore.Warp old = store.get(w.name);
                if (old != null) {
                    w.icon = old.icon;
                    w.description = old.description;
                    w.restricted = old.restricted;
                    w.onMap = old.onMap;
                }
                try {
                    store.save(w, p.getName());
                } catch (IOException | IllegalArgumentException e) {
                    sender.sendMessage("Fehler: " + e.getMessage());
                    return true;
                }
                syncPermissions();
                syncMap();
                sender.sendMessage("Warp " + w.name + " gesetzt.");
            }
            case "delwarp" -> {
                if (!sender.hasPermission("peakwarps.admin")) {
                    sender.sendMessage("Nur Operatoren.");
                    return true;
                }
                try {
                    sender.sendMessage(a.length > 0 && store.delete(a[0].toLowerCase(Locale.ROOT)) ? "Warp geloescht." : "Unbekannter Warp.");
                } catch (IOException e) {
                    sender.sendMessage("Fehler: " + e.getMessage());
                }
                syncPermissions();
                syncMap();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void teleport(Player p, WarpStore.Warp w) {
        World world = Bukkit.getWorld(w.world);
        if (world == null) {
            p.sendMessage(Component.text("Die Welt dieses Warps ist nicht geladen.", NamedTextColor.RED));
            return;
        }
        int cool = context.config().getInt("cooldown-seconds", 10), warm = context.config().getInt("warmup-seconds", 3);
        boolean admin = p.hasPermission("peakwarps.admin");
        Long last = cooldown.get(p.getUniqueId());
        if (!admin && last != null && System.currentTimeMillis() - last < cool * 1000L) {
            p.sendMessage(Component.text("Bitte warte noch " + ((cool * 1000L - (System.currentTimeMillis() - last)) / 1000 + 1) + " Sekunden.", NamedTextColor.RED));
            return;
        }
        Location target = new Location(world, w.x, w.y, w.z, w.yaw, w.pitch);
        if (admin || warm <= 0) {
            p.teleport(target);
            cooldown.put(p.getUniqueId(), System.currentTimeMillis());
            return;
        }
        BukkitTask old = pending.remove(p.getUniqueId());
        if (old != null) old.cancel();
        Location start = p.getLocation();
        p.sendMessage(Component.text("Teleport zu " + w.name + " in " + warm + " Sekunden. Nicht bewegen!", NamedTextColor.YELLOW));
        int[] left = {warm};
        BukkitTask[] self = new BukkitTask[1];
        self[0] = Bukkit.getScheduler().runTaskTimer(context.host(), () -> {
            if (!p.isOnline() || p.getLocation().getWorld() != start.getWorld() || p.getLocation().distanceSquared(start) > 1.0) {
                self[0].cancel();
                pending.remove(p.getUniqueId());
                if (p.isOnline()) p.sendMessage(Component.text("Teleport abgebrochen (du hast dich bewegt).", NamedTextColor.RED));
                return;
            }
            if (--left[0] <= 0) {
                self[0].cancel();
                pending.remove(p.getUniqueId());
                p.teleport(target);
                cooldown.put(p.getUniqueId(), System.currentTimeMillis());
            }
        }, 20L, 20L);
        pending.put(p.getUniqueId(), self[0]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (a.length == 1 && (n.equals("warp") || n.equals("delwarp"))) {
            for (WarpStore.Warp w : store.all()) if (!(sender instanceof Player p) || n.equals("delwarp") || allowed(p, w)) out.add(w.name);
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}