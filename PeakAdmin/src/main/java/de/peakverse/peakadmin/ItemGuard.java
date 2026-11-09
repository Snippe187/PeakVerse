package de.peakverse.peakadmin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Getrenntes Creative-Inventar und Item-Protokoll. Beim Wechsel zwischen Creative und den anderen Spielmodi wird das Inventar
 * (inkl. Ruestung und Zweithand) gesichert und das des Zielmodus geladen. Items aus dem Creative-Menue, per Befehl
 * (give/item/summon), Droppen und Ablegen in Behaeltern im Creative-Modus werden mit Zeit, Spieler und Quelle protokolliert.
 */
public final class ItemGuard implements Listener {
    private static final Pattern CMD = Pattern.compile("^/?(?:minecraft:)?(give|item|summon)(?:\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final long MAX_FILE = 5L * 1024 * 1024;

    private final Path folder;
    private final Path logFile;
    private final Logger logger;
    private final int keep;
    private final boolean separate;
    private final Deque<JsonObject> recent = new ArrayDeque<>();

    public ItemGuard(Path dataFolder, Logger logger, boolean separate, int keep) {
        this.folder = dataFolder.resolve("modeinv");
        this.logFile = dataFolder.resolve("itemlog.jsonl");
        this.logger = logger;
        this.separate = separate;
        this.keep = Math.max(100, keep);
        try {
            Files.createDirectories(folder);
            load();
        } catch (IOException e) {
            logger.warning("Item-Log konnte nicht geladen werden: " + e.getMessage());
        }
    }

    // ---------- Protokoll ----------

    private void load() throws IOException {
        if (!Files.isRegularFile(logFile)) return;
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            try {
                JsonObject o = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                recent.addLast(o);
                if (recent.size() > keep) recent.removeFirst();
            } catch (RuntimeException ignored) {
                // kaputte Zeile ueberspringen
            }
        }
    }

    public synchronized void log(String player, UUID uuid, String source, String item, int amount, String detail) {
        JsonObject o = new JsonObject();
        o.addProperty("time", System.currentTimeMillis());
        o.addProperty("player", player);
        o.addProperty("uuid", uuid == null ? "" : uuid.toString());
        o.addProperty("source", source);
        o.addProperty("item", item);
        o.addProperty("amount", amount);
        o.addProperty("detail", detail == null ? "" : detail);
        recent.addLast(o);
        if (recent.size() > keep) recent.removeFirst();
        try {
            if (Files.exists(logFile) && Files.size(logFile) > MAX_FILE) {
                Files.move(logFile, logFile.resolveSibling("itemlog.old.jsonl"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(logFile, o + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Item-Log schreiben fehlgeschlagen: " + e.getMessage());
        }
    }

    /** Die letzten Eintraege, neueste zuerst; leere Filter = alle. */
    public synchronized JsonArray entries(String player, String source, int limit) {
        JsonArray a = new JsonArray();
        var it = recent.descendingIterator();
        while (it.hasNext() && a.size() < limit) {
            JsonObject o = it.next();
            if (player != null && !player.isBlank() && !o.get("player").getAsString().toLowerCase(Locale.ROOT).contains(player.toLowerCase(Locale.ROOT))) continue;
            if (source != null && !source.isBlank() && !o.get("source").getAsString().equals(source)) continue;
            a.add(o);
        }
        return a;
    }

    private void logStack(Player p, String source, ItemStack s, String detail) {
        if (s == null || s.getType().isAir()) return;
        log(p.getName(), p.getUniqueId(), source, s.getType().getKey().getKey(), s.getAmount(), detail);
    }

    // ---------- Creative-Quellen ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cursor = e.getCursor();
        if (cursor.getType().isAir()) return;
        if (e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE) {
            logStack(p, "creative-drop", cursor, "");
            return;
        }
        ItemStack current = e.getCurrentItem();
        // nur neue Items zaehlen, nicht das Verschieben schon vorhandener
        if (current != null && current.getType() == cursor.getType() && current.getAmount() == cursor.getAmount()) return;
        logStack(p, "creative", cursor, "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) logStack(p, "creative-drop", e.getItemDrop().getItemStack(), "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainer(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || p.getGameMode() != GameMode.CREATIVE) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top == InventoryType.CRAFTING || top == InventoryType.CREATIVE || top == InventoryType.PLAYER) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        InventoryAction a = e.getAction();
        if (a == InventoryAction.PLACE_ALL || a == InventoryAction.PLACE_ONE || a == InventoryAction.PLACE_SOME || a == InventoryAction.SWAP_WITH_CURSOR) {
            logStack(p, "creative-container", e.getCursor(), top.name().toLowerCase(Locale.ROOT));
        } else if (a == InventoryAction.MOVE_TO_OTHER_INVENTORY || a == InventoryAction.HOTBAR_SWAP) {
            logStack(p, "creative-container", e.getCurrentItem(), top.name().toLowerCase(Locale.ROOT));
        }
    }

    // ---------- Befehle ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        var m = CMD.matcher(e.getMessage());
        if (m.find()) log(e.getPlayer().getName(), e.getPlayer().getUniqueId(), "command", m.group(1).toLowerCase(Locale.ROOT), 0, cut(e.getMessage()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        var m = CMD.matcher(e.getCommand());
        if (m.find()) log("Konsole", null, "command", m.group(1).toLowerCase(Locale.ROOT), 0, cut(e.getCommand()));
    }

    private static String cut(String s) {
        String t = s.replaceAll("[\\p{Cntrl}]", " ").strip();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    // ---------- Getrennte Inventare ----------

    private static String key(GameMode m) {
        return m == GameMode.CREATIVE ? "creative" : "normal";
    }

    private Path file(UUID id, String key) {
        return folder.resolve(id + "." + key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMode(PlayerGameModeChangeEvent e) {
        if (!separate) return;
        Player p = e.getPlayer();
        String from = key(p.getGameMode()), to = key(e.getNewGameMode());
        if (from.equals(to)) return;
        UUID id = p.getUniqueId();
        try {
            // Spieler, die schon vor dieser Funktion im Creative-Modus waren: erste Rueckkehr laesst das Inventar unveraendert
            boolean tracked = Files.exists(file(id, "tracked"));
            if (from.equals("creative") && !tracked) {
                Files.writeString(file(id, "tracked"), "1");
                return;
            }
            Files.write(file(id, from), ItemStack.serializeItemsAsBytes(p.getInventory().getContents()));
            if (to.equals("creative")) Files.writeString(file(id, "tracked"), "1");
            Path target = file(id, to);
            ItemStack[] items = Files.exists(target) ? ItemStack.deserializeItemsFromBytes(Files.readAllBytes(target)) : new ItemStack[0];
            p.getInventory().clear();
            if (items.length > 0) p.getInventory().setContents(java.util.Arrays.copyOf(items, p.getInventory().getContents().length));
            log(p.getName(), id, "mode-switch", to, 0, from + " -> " + to);
        } catch (IOException | RuntimeException ex) {
            logger.warning("Inventar-Wechsel fuer " + p.getName() + " fehlgeschlagen: " + ex.getMessage());
        }
    }

    /** Fuer Tests: der Gegenstandsname so, wie er im Protokoll steht. */
    static String itemName(Material m) {
        return m.getKey().getKey();
    }
}
