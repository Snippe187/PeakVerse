package de.peakverse.peakadmin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Spieler-Aktionen auf dem Server (immer auf dem Haupt-Thread aufrufen): Ansicht, Inventar, Endertruhe, Advancements, Strafen. */
public final class PlayerTools {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,16}");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private PlayerTools() {}

    /** Prueft einen Spielernamen, bevor er in einen Konsolen-Befehl kommt. */
    public static String safeName(String name) {
        if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Ungueltiger Spielername");
        return name;
    }

    /** Entfernt Zeilenumbrueche und Steuerzeichen aus einem Grund-Text. */
    public static String safeText(String text) {
        String t = text == null ? "" : text.replaceAll("[\\r\\n\\p{Cntrl}]", " ").strip();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    public static Player online(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) throw new IllegalStateException("Der Spieler ist nicht online.");
        return p;
    }

    private static void console(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    // ---- Liste und Ansicht ----

    public static JsonArray list() {
        Map<UUID, JsonObject> rows = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) rows.put(p.getUniqueId(), row(p.getUniqueId(), p.getName(), true, p.isOp(), p.getPing()));
        int n = 0;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (n++ >= 1000) break;
            if (rows.containsKey(op.getUniqueId()) || op.getName() == null) continue;
            rows.put(op.getUniqueId(), row(op.getUniqueId(), op.getName(), false, op.isOp(), -1));
            rows.get(op.getUniqueId()).addProperty("lastSeen", op.getLastSeen());
        }
        for (JsonObject o : rows.values()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(o.get("uuid").getAsString()));
            o.addProperty("whitelisted", op.isWhitelisted());
            o.addProperty("banned", op.isBanned());
        }
        JsonArray a = new JsonArray();
        rows.values().forEach(a::add);
        return a;
    }

    private static JsonObject row(UUID id, String name, boolean online, boolean op, int ping) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", id.toString());
        o.addProperty("name", name);
        o.addProperty("online", online);
        o.addProperty("op", op);
        o.addProperty("ping", ping);
        return o;
    }

    public static JsonObject detail(UUID id, MuteStore mutes) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        Player p = Bukkit.getPlayer(id);
        JsonObject o = row(id, p != null ? p.getName() : String.valueOf(off.getName()), p != null, off.isOp(), p != null ? p.getPing() : -1);
        o.addProperty("whitelisted", off.isWhitelisted());
        o.addProperty("banned", off.isBanned());
        o.addProperty("lastSeen", p != null ? System.currentTimeMillis() : off.getLastSeen());
        o.addProperty("firstPlayed", off.getFirstPlayed());
        MuteStore.Mute m = mutes.active(id);
        o.addProperty("muted", m != null);
        if (m != null) o.addProperty("muteReason", m.reason);
        if (p == null) return o;
        o.addProperty("health", p.getHealth());
        o.addProperty("food", p.getFoodLevel());
        o.addProperty("level", p.getLevel());
        o.addProperty("gamemode", p.getGameMode().name().toLowerCase(Locale.ROOT));
        o.addProperty("world", p.getWorld().getName());
        o.addProperty("x", Math.round(p.getLocation().getX()));
        o.addProperty("y", Math.round(p.getLocation().getY()));
        o.addProperty("z", Math.round(p.getLocation().getZ()));
        o.add("inventory", items(p.getInventory()));
        o.add("ender", items(p.getEnderChest()));
        return o;
    }

    /** Alle belegten Plaetze eines Inventars als {slot, item}. */
    public static JsonArray items(Inventory inv) {
        JsonArray a = new JsonArray();
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || c[i].getType().isAir()) continue;
            JsonObject o = item(c[i]);
            o.addProperty("slot", i);
            a.add(o);
        }
        return a;
    }

    static JsonObject item(ItemStack s) {
        JsonObject o = new JsonObject();
        o.addProperty("type", s.getType().getKey().toString());
        o.addProperty("amount", s.getAmount());
        if (s.hasItemMeta() && s.getItemMeta().hasDisplayName()) o.addProperty("name", PLAIN.serialize(s.getItemMeta().displayName()));
        if (s.hasItemMeta()) {
            var meta = s.getItemMeta();
            var lore = meta.lore();
            if (lore != null && !lore.isEmpty()) {
                JsonArray l = new JsonArray();
                for (var c : lore) l.add(PLAIN.serialize(c));
                o.add("lore", l);
            }
            if (meta instanceof org.bukkit.inventory.meta.Damageable d && d.hasDamage() && s.getType().getMaxDurability() > 0) {
                o.addProperty("durability", s.getType().getMaxDurability() - d.getDamage());
                o.addProperty("maxDurability", (int) s.getType().getMaxDurability());
            }
        }
        JsonArray ench = new JsonArray();
        for (Map.Entry<Enchantment, Integer> e : s.getEnchantments().entrySet()) ench.add(e.getKey().getKey() + " " + e.getValue());
        if (!ench.isEmpty()) o.add("enchants", ench);
        return o;
    }

    // ---- Inventar bearbeiten ----

    private static Inventory inv(Player p, String which) {
        return switch (which) {
            case "inventory" -> p.getInventory();
            case "ender" -> p.getEnderChest();
            default -> throw new IllegalArgumentException("Unbekanntes Inventar: " + which);
        };
    }

    public static void removeSlot(UUID id, String which, int slot) {
        Inventory inv = inv(online(id), which);
        if (slot < 0 || slot >= inv.getSize()) throw new IllegalArgumentException("Platz ausserhalb des Inventars");
        inv.setItem(slot, null);
    }

    public static void clear(UUID id, String which) {
        inv(online(id), which).clear();
    }

    public static void give(UUID id, String which, String material, int amount) {
        Material m = material == null ? null : Material.matchMaterial(material);
        if (m == null || !m.isItem() || m.isAir()) throw new IllegalArgumentException("Unbekannter Gegenstand: " + material);
        if (amount < 1 || amount > 6400) throw new IllegalArgumentException("Menge muss zwischen 1 und 6400 liegen.");
        Inventory inv = inv(online(id), which);
        Map<Integer, ItemStack> rest = inv.addItem(new ItemStack(m, amount));
        if (!rest.isEmpty()) throw new IllegalStateException("Kein Platz fuer " + rest.values().stream().mapToInt(ItemStack::getAmount).sum() + " Stueck.");
    }

    // ---- Werte und Aktionen ----

    public static void heal(UUID id) {
        Player p = online(id);
        p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
    }

    public static void gamemode(UUID id, String mode) {
        GameMode g;
        try {
            g = GameMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannter Spielmodus: " + mode);
        }
        online(id).setGameMode(g);
    }

    public static void setLevel(UUID id, int level) {
        if (level < 0 || level > 100000) throw new IllegalArgumentException("Level ausserhalb von 0 bis 100000");
        online(id).setLevel(level);
    }

    public static void teleportToPlayer(UUID id, UUID target) {
        online(id).teleport(online(target).getLocation());
    }

    public static void teleportTo(UUID id, String world, double x, double y, double z) {
        org.bukkit.World w = Bukkit.getWorld(world);
        if (w == null) throw new IllegalArgumentException("Unbekannte Welt: " + world);
        online(id).teleport(new org.bukkit.Location(w, x, y, z));
    }

    public static void kick(UUID id, String reason) {
        online(id).kick(net.kyori.adventure.text.Component.text(safeText(reason).isEmpty() ? "Vom Server entfernt." : safeText(reason)));
    }

    public static void ban(String name, String reason) {
        console("ban " + safeName(name) + " " + safeText(reason));
    }

    public static void pardon(String name) {
        console("pardon " + safeName(name));
    }

    public static void whitelist(String name, boolean add) {
        console("whitelist " + (add ? "add " : "remove ") + safeName(name));
    }

    public static void op(String name, boolean op) {
        console((op ? "op " : "deop ") + safeName(name));
    }

    // ---- Advancements ----

    /** Alle Fortschritte (ohne Rezepte) mit Status des Spielers. */
    public static JsonArray advancements(UUID id) {
        Player p = online(id);
        JsonArray a = new JsonArray();
        java.util.Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            String key = adv.getKey().toString();
            if (adv.getKey().getKey().startsWith("recipes/")) continue;
            AdvancementProgress pr = p.getAdvancementProgress(adv);
            JsonObject o = new JsonObject();
            o.addProperty("key", key);
            var disp = adv.getDisplay();
            if (disp == null) continue;   // ohne Anzeige (technische Fortschritte) nicht auflisten
            o.addProperty("title", PLAIN.serialize(disp.title()));
            o.addProperty("description", PLAIN.serialize(disp.description()));
            o.addProperty("icon", disp.icon().getType().getKey().getKey());
            o.addProperty("frame", disp.frame().name().toLowerCase(Locale.ROOT));
            var parent = adv.getParent();
            o.addProperty("parent", parent == null ? "" : parent.getKey().toString());
            o.addProperty("done", pr.isDone());
            o.addProperty("criteria", adv.getCriteria().size());
            o.addProperty("remaining", pr.getRemainingCriteria().size());
            a.add(o);
        }
        return a;
    }

    /** Vergibt oder entzieht einen einzelnen Fortschritt. Liefert die Zahl der geaenderten (0 oder 1). */
    public static int advancement(UUID id, String key, boolean grant) {
        Player p = online(id);
        List<Advancement> targets = new ArrayList<>();
        NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        Advancement adv = nk == null ? null : Bukkit.getAdvancement(nk);
        if (adv == null) throw new IllegalArgumentException("Unbekannter Fortschritt: " + key);
        targets.add(adv);
        int changed = 0;
        for (Advancement a : targets) {
            AdvancementProgress pr = p.getAdvancementProgress(a);
            if (grant && !pr.isDone()) {
                new ArrayList<>(pr.getRemainingCriteria()).forEach(pr::awardCriteria);
                changed++;
            } else if (!grant && !pr.getAwardedCriteria().isEmpty()) {
                new ArrayList<>(pr.getAwardedCriteria()).forEach(pr::revokeCriteria);
                changed++;
            }
        }
        return changed;
    }
}