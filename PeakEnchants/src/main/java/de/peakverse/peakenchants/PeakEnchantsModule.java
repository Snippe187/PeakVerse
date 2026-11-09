package de.peakverse.peakenchants;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peakverse.core.api.pack.PackSink;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** PeakEnchants: Datapack-Addons mit Verzauberungen verwalten (Web-Seite) und Verzauberungen vergeben. */
public final class PeakEnchantsModule implements PeakModule, CommandExecutor, TabCompleter {
    private ModuleContext context;
    private EnchantStore store;

    @Override
    public String id() {
        return "peakenchants";
    }

    @Override
    public String displayName() {
        return "PeakEnchants";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        Path datapacks = datapacksFolder();
        store = new EnchantStore(datapacks, context.dataFolder().toPath().resolve("backups"));
        PluginCommand command = context.host().getCommand("peakenchants");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakEnchantsModule.class.getResourceAsStream("/modules/peakenchants/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                new EnchantsWeb(context.web(), store, new EnchantsWeb.Host() {
                    @Override
                    public boolean loaded(String key) {
                        return find(key) != null;
                    }

                    @Override
                    public String giveBook(String player, String key, int level) {
                        return PeakEnchantsModule.this.giveBook(player, key, level);
                    }

                    @Override
                    public String applyToHand(String player, String key, int level) {
                        return PeakEnchantsModule.this.applyToHand(player, key, level);
                    }

                    @Override
                    public List<String> onlinePlayers() {
                        List<String> l = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
                        return l;
                    }
                }).register(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakEnchants nicht verfuegbar: " + e.getMessage());
            }
        }
    }

    /** Der Ordner datapacks der Hauptwelt. Seit Paper 26 liegt die Overworld in {@code <Welt>/dimensions/minecraft/overworld}, der Ordner datapacks eine Ebene der Welt darueber. */
    private static Path datapacksFolder() {
        Path p = Bukkit.getWorlds().get(0).getWorldFolder().toPath().toAbsolutePath();
        for (int i = 0; i < 5 && p != null; i++, p = p.getParent()) {
            if (java.nio.file.Files.isRegularFile(p.resolve("level.dat"))) return p.resolve("datapacks");
        }
        return Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("datapacks");
    }

    @Override
    public void contributePack(PackSink sink) {
        // Keine Pack-Dateien
    }

    @Override
    public void disable() {
        context = null;
        store = null;
    }

    // ---- Vergabe ----

    private static Enchantment find(String key) {
        NamespacedKey k = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        return k == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(k);
    }

    private String giveBook(String player, String key, int level) {
        Player p = Bukkit.getPlayerExact(player);
        if (p == null) return "Der Spieler ist nicht online.";
        Enchantment e = find(key);
        if (e == null) return "Die Verzauberung ist noch nicht im Server geladen (nach Aenderungen: Server neu starten).";
        if (level < 1 || level > 255) return "Stufe muss zwischen 1 und 255 liegen.";
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(e, level, true);
        book.setItemMeta(meta);
        p.getInventory().addItem(book).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        return null;
    }

    private String applyToHand(String player, String key, int level) {
        Player p = Bukkit.getPlayerExact(player);
        if (p == null) return "Der Spieler ist nicht online.";
        Enchantment e = find(key);
        if (e == null) return "Die Verzauberung ist noch nicht im Server geladen (nach Aenderungen: Server neu starten).";
        if (level < 1 || level > 255) return "Stufe muss zwischen 1 und 255 liegen.";
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) return "Der Spieler hat nichts in der Hand.";
        hand.addUnsafeEnchantment(e, level);
        return null;
    }

    // ---- Befehl ----

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                StringBuilder sb = new StringBuilder("Verzauberungen im Server: ");
                int n = 0;
                for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
                    if (e.getKey().getNamespace().equals("minecraft")) continue;
                    sb.append(e.getKey()).append(' ');
                    n++;
                }
                sender.sendMessage(n == 0 ? "Keine Datapack-Verzauberungen geladen." : sb.toString());
            }
            case "info" -> {
                Enchantment e = a.length > 1 ? find(a[1]) : null;
                sender.sendMessage(e == null ? "Unbekannt." : e.getKey() + ": Stufe 1-" + e.getMaxLevel());
            }
            case "book" -> {
                if (a.length < 3) {
                    sender.sendMessage("/peakenchants book <Spieler> <namespace:name> [Stufe]");
                    return true;
                }
                String err = giveBook(a[1], a[2], level(a, 3));
                sender.sendMessage(err == null ? "Buch gegeben." : err);
            }
            case "apply" -> {
                if (a.length < 2 || !(sender instanceof Player p)) {
                    sender.sendMessage("Im Spiel: /peakenchants apply <namespace:name> [Stufe] (verzaubert das Item in deiner Hand)");
                    return true;
                }
                String err = applyToHand(p.getName(), a[1], level(a, 2));
                sender.sendMessage(err == null ? "Item verzaubert." : err);
            }
            default -> sender.sendMessage("/peakenchants <list|info|book|apply>");
        }
        return true;
    }

    private static int level(String[] a, int i) {
        try {
            return a.length > i ? Integer.parseInt(a[i]) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("list", "info", "book", "apply"));
        else {
            int keyIdx = a[0].equalsIgnoreCase("book") ? 2 : 1;
            if (a.length == 2 && a[0].equalsIgnoreCase("book")) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            else if (a.length == keyIdx + 1) for (Enchantment e : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) out.add(e.getKey().toString());
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
