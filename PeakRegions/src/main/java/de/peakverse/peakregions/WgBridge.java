package de.peakverse.peakregions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Anbindung an WorldGuard (nur aufrufen, wenn das Plugin installiert ist): Regionen lesen, anlegen, loeschen, Mitglieder und Prioritaet. Flags setzt das Modul ueber den Befehl /region. */
public final class WgBridge {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_,'\\-+/]{1,64}");

    private WgBridge() {}

    private static RegionManager manager(String world) {
        World w = Bukkit.getWorld(world);
        if (w == null) throw new IllegalArgumentException("Unbekannte Welt: " + world);
        RegionManager m = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(w));
        if (m == null) throw new IllegalStateException("WorldGuard verwaltet diese Welt nicht.");
        return m;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches() && !id.equalsIgnoreCase("__global__");
    }

    private static ProtectedRegion region(String world, String id) {
        ProtectedRegion r = manager(world).getRegion(id);
        if (r == null) throw new IllegalArgumentException("Unbekannte Region: " + id);
        return r;
    }

    public static JsonArray flagNames() {
        JsonArray a = new JsonArray();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Flag<?> f : WorldGuard.getInstance().getFlagRegistry()) names.add(f.getName());
        java.util.Collections.sort(names);
        names.forEach(a::add);
        return a;
    }

    private static JsonArray names(DefaultDomain d) {
        JsonArray a = new JsonArray();
        for (UUID id : d.getUniqueIds()) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            JsonObject o = new JsonObject();
            o.addProperty("uuid", id.toString());
            o.addProperty("name", n == null ? id.toString() : n);
            a.add(o);
        }
        return a;
    }

    public static JsonArray regions(String world) {
        JsonArray a = new JsonArray();
        for (ProtectedRegion r : manager(world).getRegions().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.getId());
            o.addProperty("type", r.getType().name().toLowerCase());
            o.addProperty("priority", r.getPriority());
            o.addProperty("parent", r.getParent() == null ? "" : r.getParent().getId());
            BlockVector3 min = r.getMinimumPoint(), max = r.getMaximumPoint();
            o.addProperty("x1", min.x());
            o.addProperty("y1", min.y());
            o.addProperty("z1", min.z());
            o.addProperty("x2", max.x());
            o.addProperty("y2", max.y());
            o.addProperty("z2", max.z());
            o.add("owners", names(r.getOwners()));
            o.add("members", names(r.getMembers()));
            JsonArray flags = new JsonArray();
            for (Map.Entry<Flag<?>, Object> e : r.getFlags().entrySet()) {
                JsonObject f = new JsonObject();
                f.addProperty("name", e.getKey().getName());
                f.addProperty("value", String.valueOf(e.getValue()));
                flags.add(f);
            }
            o.add("flags", flags);
            a.add(o);
        }
        return a;
    }

    private static void save(RegionManager m) {
        try {
            m.save();
        } catch (Exception e) {
            // WorldGuard speichert auch von selbst regelmaessig
        }
    }

    public static void create(String world, String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (!validId(id)) throw new IllegalArgumentException("Ungueltiger Regionsname (a-z, 0-9, _ , ' - + /; bis 64 Zeichen)");
        RegionManager m = manager(world);
        if (m.hasRegion(id)) throw new IllegalArgumentException("Es gibt schon eine Region mit diesem Namen.");
        m.addRegion(new ProtectedCuboidRegion(id, BlockVector3.at(x1, y1, z1), BlockVector3.at(x2, y2, z2)));
        save(m);
    }

    public static void remove(String world, String id) {
        RegionManager m = manager(world);
        region(world, id);
        m.removeRegion(id);
        save(m);
    }

    public static void priority(String world, String id, int priority) {
        region(world, id).setPriority(priority);
        save(manager(world));
    }

    public static void player(String world, String id, UUID player, boolean owner, boolean on) {
        ProtectedRegion r = region(world, id);
        DefaultDomain d = owner ? r.getOwners() : r.getMembers();
        if (on) d.addPlayer(player);
        else d.removePlayer(player);
        save(manager(world));
    }
}