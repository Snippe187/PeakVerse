package de.peakverse.peakachievements;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * PeakAchievements (Server): eigene Achievements als echte Minecraft-Fortschritte (Ausloeser {@code minecraft:impossible}, vergeben durch dieses Modul),
 * Ausloeser fuer Ereignisse und Statistiken, Belohnungen und ein Befehl zum Verwalten. Minecraft-Fortschritte lassen sich per {@code vanilla.json} abschalten.
 */
public final class PeakAchievementsModule implements PeakModule, Listener, CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("mode", "list", "me", "info", "give", "revoke", "enable", "disable", "add", "remove", "reload");

    private ModuleContext context;
    private AchievementStore store;
    private ProgressStore progress;
    private final Set<NamespacedKey> loaded = new HashSet<>();
    private BukkitTask statTask;
    private BukkitTask saveTask;
    private boolean active;

    @Override
    public String id() {
        return "peakachievements";
    }

    @Override
    public String displayName() {
        return "PeakAchievements";
    }

    @Override
    public void enable(ModuleContext context) {
        this.context = context;
        context.saveDefaultConfig();
        store = new AchievementStore(context.dataFolder().toPath());
        progress = new ProgressStore(context.dataFolder().toPath());
        try {
            store.load();
            progress.load();
        } catch (IOException e) {
            throw new IllegalStateException("Achievements konnten nicht geladen werden: " + e.getMessage(), e);
        }
        PluginCommand command = context.host().getCommand("peakachievements");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        if (context.web().enabled()) {
            try (java.io.InputStream in = PeakAchievementsModule.class.getResourceAsStream("/modules/peakachievements/panel.js")) {
                if (in == null) throw new IOException("panel.js fehlt im Jar");
                new AchievementsWeb(context.web(), new AchievementsWeb.Host() {
                    @Override
                    public AchievementStore live() {
                        return store;
                    }

                    @Override
                    public java.nio.file.Path folder() {
                        return context.dataFolder().toPath();
                    }

                    @Override
                    public boolean peakMode() {
                        return PeakAchievementsModule.this.peakMode();
                    }

                    @Override
                    public void applyLive() throws IOException {
                        store.load();
                        if (!active) return;
                        // Entfernte Fortschritte gibt Paper erst spaeter frei: kurz warten, dann neu laden
                        unpublish();
                        Bukkit.getScheduler().runTaskLater(context.host(), () -> {
                            if (!active) return;
                            publish();
                            for (Player online : Bukkit.getOnlinePlayers()) sync(online);
                        }, 40L);
                    }
                }).register(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                context.logger().warning("Web-Seite von PeakAchievements nicht verfuegbar: " + e.getMessage());
            }
        }
        if (peakMode()) activate();
        else context.logger().info("Vanilla-Modus: Minecraft-Fortschritte bleiben unveraendert, PeakAchievements ist aus. Einschalten: /peakachievements mode peak");
    }

    /** {@code mode: peak} (Standard) schaltet alle Features ein, {@code mode: vanilla} laesst Minecraft vollstaendig in Ruhe. */
    private boolean peakMode() {
        return !"vanilla".equalsIgnoreCase(context.config().getString("mode", "peak"));
    }

    /** Peak-Modus: Listener, Fortschritte, Aufgaben. */
    private void activate() {
        if (active) return;
        try {
            if (store.all().isEmpty() && !java.nio.file.Files.exists(context.dataFolder().toPath().resolve("achievements.json")) && context.config().getBoolean("examples", true)) {
                for (Achievement a : Examples.all()) store.put(a);
            }
        } catch (IOException e) {
            context.logger().warning("Beispiele konnten nicht angelegt werden: " + e.getMessage());
        }
        var host = context.host();
        host.getServer().getPluginManager().registerEvents(this, host);
        publish();
        for (Player online : Bukkit.getOnlinePlayers()) sync(online);
        statTask = host.getServer().getScheduler().runTaskTimer(host, this::checkStats, 100L, 100L);
        saveTask = host.getServer().getScheduler().runTaskTimer(host, this::save, 600L, 600L);
        active = true;
        context.logger().info("Peak-Modus: " + store.all().size() + " Achievements geladen.");
    }

    /** Vanilla-Modus: alles zurueck, Minecraft verhaelt sich wieder wie ohne dieses Modul. */
    private void deactivate() {
        if (!active) return;
        if (statTask != null) statTask.cancel();
        if (saveTask != null) saveTask.cancel();
        statTask = null;
        saveTask = null;
        org.bukkit.event.HandlerList.unregisterAll(this);
        save();
        unpublish();
        active = false;
    }

    @Override
    public void disable() {
        if (context != null) {
            deactivate();
            PluginCommand command = context.host().getCommand("peakachievements");
            if (command != null && command.getExecutor() == this) command.setExecutor(null);
        }
        context = null;
        store = null;
        progress = null;
    }

    private void setMode(CommandSender who, String mode) {
        if (!mode.equals("peak") && !mode.equals("vanilla")) throw new IllegalArgumentException("Modus: peak oder vanilla.");
        context.config().set("mode", mode);
        try {
            context.config().save(new java.io.File(context.dataFolder(), "config.yml"));
        } catch (IOException e) {
            msg(who, "Modus gilt jetzt, konnte aber nicht in die config.yml geschrieben werden: " + e.getMessage(), NamedTextColor.RED);
        }
        if (mode.equals("peak")) activate();
        else deactivate();
        msg(who, mode.equals("peak") ? "Peak-Modus ist an." : "Vanilla-Modus ist an: Minecraft-Fortschritte sind unveraendert, PeakAchievements ist aus. Der Reiter PeakVerse verschwindet sicher erst nach einem Serverneustart.", NamedTextColor.GREEN);
    }

    private void save() {
        if (progress == null) return;
        try {
            progress.saveIfDirty();
        } catch (IOException e) {
            context.logger().warning("Fortschritt konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    // ---- echte Fortschritte laden ----

    @SuppressWarnings("deprecation")
    private void publish() {
        unpublish();
        var cfg = context.config();
        NamespacedKey root = new NamespacedKey(AdvancementJson.NAMESPACE, AdvancementJson.ROOT);
        if (!load(root, AdvancementJson.root(cfg.getString("tab.title", "PeakVerse"), cfg.getString("tab.description", "Eigene Achievements dieses Servers")))) return;
        boolean announce = cfg.getBoolean("announce-to-chat", true);
        // Alle Knoten (Kopien der Minecraft-Fortschritte und eigene Achievements) in einer Liste; geladen wird nach Eltern geordnet
        List<Node> pending = new ArrayList<>();
        java.util.Set<String> mirrored = new HashSet<>();
        List<Advancement> vanilla = cfg.getBoolean("mirror-vanilla", true) ? VanillaMirror.collect() : List.of();
        for (Advancement adv : vanilla) if (store.vanillaEnabled(adv.getKey().toString())) mirrored.add(adv.getKey().toString());
        for (Advancement adv : vanilla) {
            String vk = adv.getKey().toString();
            if (!mirrored.contains(vk)) continue;
            VanillaOverride o = store.vanilla(vk);
            String wanted = o.parent; // null = wie in Minecraft, leer = direkt an die Wurzel
            String parent = wanted == null ? null : wanted.isEmpty() ? root.toString() : resolveParent(wanted, mirrored);
            if (parent == null) {
                parent = root.toString();
                for (Advancement p = adv.getParent(); p != null; p = p.getParent()) {
                    if (mirrored.contains(p.getKey().toString())) {
                        parent = VanillaMirror.mirrorKey(p.getKey()).toString();
                        break;
                    }
                }
            }
            pending.add(new Node(VanillaMirror.mirrorKey(adv.getKey()), adv.getKey(), parent, p -> VanillaMirror.json(adv, p, o)));
        }
        for (Achievement a : store.all()) {
            if (!a.enabled) continue;
            String parent = a.parent == null || a.parent.isEmpty() ? null : resolveParent(a.parent, mirrored);
            pending.add(new Node(NamespacedKey.fromString(AdvancementJson.key(a.id)), null, parent == null ? root.toString() : parent, p -> AdvancementJson.of(a, p, announce)));
        }
        boolean progressed = true;
        while (!pending.isEmpty() && progressed) {
            progressed = false;
            for (var it = pending.iterator(); it.hasNext(); ) {
                Node n = it.next();
                if (!n.parent.equals(root.toString()) && !loaded.contains(NamespacedKey.fromString(n.parent))) continue; // Elternteil kommt spaeter
                if (load(n.key, n.json.apply(n.parent)) && n.vanilla != null) mirrors.put(n.vanilla, n.key);
                it.remove();
                progressed = true;
            }
        }
        long live = loaded.stream().filter(k -> Bukkit.getAdvancement(k) != null).count();
        if (live != loaded.size()) context.logger().warning("Nur " + live + " von " + loaded.size() + " Fortschritten sind aktiv (Paper gibt entfernte erst spaeter frei).");
        for (Node n : pending) context.logger().warning("Fortschritt " + n.key + " hat keinen ladbaren Elternteil (" + n.parent + ") und wird nicht angezeigt.");
        context.logger().info(mirrors.size() + " Minecraft-Fortschritte in den Reiter gespiegelt.");
    }

    /** Ein Knoten des Baums: Schluessel, Original (nur Kopien), Elternteil und der Bauplan fuer das JSON. */
    private record Node(NamespacedKey key, NamespacedKey vanilla, String parent, java.util.function.Function<String, String> json) {}

    /** Elternangabe ({@code minecraft:...} oder eigene Kennung) -> Schluessel des Fortschritts oder {@code null}, wenn es den Elternteil nicht gibt. */
    private String resolveParent(String wanted, java.util.Set<String> mirrored) {
        if (wanted.startsWith("minecraft:")) return mirrored.contains(wanted) ? VanillaMirror.mirrorKey(NamespacedKey.fromString(wanted)).toString() : null;
        Achievement p = store.get(wanted);
        return p != null && p.enabled ? AdvancementJson.key(p.id) : null;
    }

    /** Minecraft-Fortschritt -> Kopie im Reiter PeakVerse. */
    private final java.util.Map<NamespacedKey, NamespacedKey> mirrors = new java.util.HashMap<>();
    /** Setzt die Kopien eines Spielers auf den Stand der Originale (still, ohne Toast). */
    private void syncMirrors(Player p) {
        if (mirrors.isEmpty() || !p.isOnline()) return;
        for (var e : mirrors.entrySet()) {
            Advancement orig = Bukkit.getAdvancement(e.getKey());
            Advancement copy = Bukkit.getAdvancement(e.getValue());
            if (orig == null || copy == null) continue;
            boolean origDone = p.getAdvancementProgress(orig).isDone();
            var copyProgress = p.getAdvancementProgress(copy);
            // Minecraft hat das Sagen: die Kopie folgt dem Original in beide Richtungen (auch nach /advancement revoke)
            if (origDone && !copyProgress.isDone()) copyProgress.awardCriteria(AdvancementJson.CRITERION);
            else if (!origDone && copyProgress.isDone()) copyProgress.revokeCriteria(AdvancementJson.CRITERION);
        }
    }

    /** Nimmt der Server Fortschritte per Befehl zurueck, ziehen die Kopien kurz danach nach. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(org.bukkit.event.server.ServerCommandEvent e) {
        if (e.getCommand().toLowerCase(Locale.ROOT).startsWith("advancement")) resyncSoon();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent e) {
        if (e.getMessage().toLowerCase(Locale.ROOT).startsWith("/advancement")) resyncSoon();
    }

    private void resyncSoon() {
        Bukkit.getScheduler().runTaskLater(context.host(), () -> {
            for (Player online : Bukkit.getOnlinePlayers()) syncMirrors(online);
        }, 5L);
    }

    @EventHandler
    public void onVanillaDone(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {
        NamespacedKey mk = mirrors.get(e.getAdvancement().getKey());
        if (mk == null) return;
        Advancement copy = Bukkit.getAdvancement(mk);
        if (copy != null) e.getPlayer().getAdvancementProgress(copy).awardCriteria(AdvancementJson.CRITERION);
    }

    /** Laedt einen Fortschritt; existiert er noch von frueher, wird er zuerst entfernt und dann neu geladen. */
    @SuppressWarnings("deprecation")
    private boolean load(NamespacedKey key, String json) {
        var unsafe = Bukkit.getUnsafe();
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                unsafe.loadAdvancement(key, json);
                loaded.add(key);
                return true;
            } catch (IllegalArgumentException e) {
                if (attempt == 0 && Bukkit.getAdvancement(key) != null) {
                    unsafe.removeAdvancement(key);
                    continue;
                }
                if (String.valueOf(e.getMessage()).contains("already exists")) {
                    // Paper gibt entfernte Fortschritte erst spaeter frei; die alte Fassung bleibt bis zum naechsten Serverstart
                    loaded.add(key);
                    return true;
                }
                context.logger().warning("Fortschritt " + key + " konnte nicht geladen werden: " + e);
                return false;
            } catch (RuntimeException e) {
                context.logger().warning("Fortschritt " + key + " konnte nicht geladen werden: " + e);
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private void unpublish() {
        for (NamespacedKey k : loaded) {
            try {
                if (!Bukkit.getUnsafe().removeAdvancement(k)) context.logger().warning("Fortschritt " + k + " liess sich nicht entfernen.");
            } catch (RuntimeException e) {
                context.logger().warning("Fortschritt " + k + " liess sich nicht entfernen: " + e);
            }
        }
        loaded.clear();
        mirrors.clear();
    }

    // ---- Ausloeser ----

    private void count(Player p, Achievement.Type type, String name, int amount) {
        if (store == null) return;
        for (Achievement a : Matcher.forEvent(store.all(), type, name)) {
            if (progress.isDone(p.getUniqueId(), a.id)) continue;
            long v = progress.add(p.getUniqueId(), "a:" + a.id, amount);
            if (Matcher.reached(a, v)) award(p, a);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        count(e.getPlayer(), Achievement.Type.BREAK, e.getBlock().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        count(e.getPlayer(), Achievement.Type.PLACE, e.getBlockPlaced().getType().name(), 1);
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null) count(killer, Achievement.Type.KILL, e.getEntityType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack result = e.getRecipe().getResult();
        if (result.getType().isAir()) return;
        // Umschalt-Klick stellt mehrere Stuecke her; als eine Handlung gezaehlt, das genuegt fuer Ziele wie "stelle X her"
        count(p, Achievement.Type.CRAFT, result.getType().name(), Math.max(1, e.isShiftClick() ? 1 : result.getAmount()));
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        String name = e.getCaught() instanceof org.bukkit.entity.Item item ? item.getItemStack().getType().name() : "";
        count(e.getPlayer(), Achievement.Type.FISH, name, 1);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(context.host(), () -> sync(p), 20L);
    }

    /** Stellt sicher, dass alle gespeicherten Achievements auch im Fortschritts-Fenster des Spielers erreicht sind. */
    private void sync(Player p) {
        if (store == null || !p.isOnline()) return;
        syncMirrors(p);
        for (String id : progress.done(p.getUniqueId())) {
            Achievement a = store.get(id);
            if (a != null && a.enabled) grantAdvancement(p, a);
        }
    }

    @EventHandler
    public void onVanillaGrant(PlayerAdvancementCriterionGrantEvent e) {
        if (store == null) return;
        String key = e.getAdvancement().getKey().toString();
        if (key.startsWith("minecraft:") && !store.vanillaEnabled(key)) e.setCancelled(true);
    }

    private void checkStats() {
        if (store == null) return;
        List<Achievement> stat = store.all().stream().filter(a -> a.enabled && Matcher.isStat(a.type)).toList();
        if (stat.isEmpty()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Achievement a : stat) {
                if (progress.isDone(p.getUniqueId(), a.id)) continue;
                long raw = switch (a.type) {
                    case JUMP -> p.getStatistic(Statistic.JUMP);
                    case WALK -> p.getStatistic(Statistic.WALK_ONE_CM);
                    case PLAYTIME -> p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    case DEATH -> p.getStatistic(Statistic.DEATHS);
                    default -> 0;
                };
                if (Matcher.reached(a, Matcher.statValue(a.type, raw))) award(p, a);
            }
        }
    }

    // ---- Vergabe und Belohnungen ----

    /** @return {@code true}, wenn das Achievement neu erreicht wurde */
    boolean award(Player p, Achievement a) {
        if (!progress.award(p.getUniqueId(), a.id, System.currentTimeMillis())) return false;
        grantAdvancement(p, a);
        reward(p, a);
        return true;
    }

    private void grantAdvancement(Player p, Achievement a) {
        Advancement adv = Bukkit.getAdvancement(NamespacedKey.fromString(AdvancementJson.key(a.id)));
        if (adv != null) p.getAdvancementProgress(adv).awardCriteria(AdvancementJson.CRITERION);
    }

    private void reward(Player p, Achievement a) {
        Achievement.Rewards r = a.rewards;
        if (r == null) return;
        if (r.xp > 0) p.giveExp(r.xp);
        for (String s : r.items) {
            String[] part = s.split(":");
            Material m = Material.matchMaterial(part[0]);
            if (m == null || !m.isItem()) {
                context.logger().warning("Belohnung von " + a.id + ": unbekannter Gegenstand " + s);
                continue;
            }
            int amount = part.length > 1 ? Integer.parseInt(part[1]) : 1;
            int stack = Math.max(1, m.getMaxStackSize());
            while (amount > 0) {
                int n = Math.min(stack, amount);
                p.getInventory().addItem(new ItemStack(m, n)).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                amount -= n;
            }
        }
        for (String c : r.commands) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
        if (r.title != null && !r.title.isBlank()) p.showTitle(net.kyori.adventure.title.Title.title(Component.text(r.title, NamedTextColor.GOLD), Component.text(a.title, NamedTextColor.YELLOW)));
        if (r.sound != null && !r.sound.isBlank()) p.playSound(p.getLocation(), r.sound, 1f, 1f);
        if (r.firework) {
            var fw = p.getWorld().spawn(p.getLocation(), org.bukkit.entity.Firework.class);
            var meta = fw.getFireworkMeta();
            meta.addEffect(org.bukkit.FireworkEffect.builder().withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE).with(org.bukkit.FireworkEffect.Type.STAR).build());
            meta.setPower(0);
            fw.setFireworkMeta(meta);
            fw.detonate();
        }
    }

    // ---- Befehl ----

    private static void msg(CommandSender who, String text, NamedTextColor color) {
        who.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender who, Command command, String label, String[] args) {
        if (store == null) return true;
        String sub = args.length == 0 ? "me" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = who.hasPermission("peakcore.admin");
        if (!admin && !sub.equals("me") && !sub.equals("list")) {
            msg(who, "Nur fuer Administratoren.", NamedTextColor.RED);
            return true;
        }
        if (!active && !sub.equals("mode")) {
            msg(who, admin ? "Vanilla-Modus: PeakAchievements ist aus. Einschalten mit /peakachievements mode peak" : "Auf diesem Server gelten die normalen Minecraft-Fortschritte.", NamedTextColor.GRAY);
            return true;
        }
        try {
            switch (sub) {
                case "mode" -> {
                    if (args.length < 2) msg(who, "Aktueller Modus: " + (active ? "peak" : "vanilla") + ". Wechseln: /peakachievements mode <peak|vanilla>", NamedTextColor.GOLD);
                    else if (!admin) msg(who, "Nur fuer Administratoren.", NamedTextColor.RED);
                    else setMode(who, args[1].toLowerCase(Locale.ROOT));
                }
                case "me" -> me(who);
                case "list" -> list(who);
                case "info" -> info(who, arg(args));
                case "give" -> give(who, args);
                case "revoke" -> revoke(who, args);
                case "enable", "disable" -> {
                    store.setEnabled(arg(args), sub.equals("enable"));
                    publish();
                    msg(who, "Achievement " + args[1] + (sub.equals("enable") ? " eingeschaltet." : " ausgeschaltet."), NamedTextColor.GREEN);
                }
                case "add" -> add(who, args);
                case "remove" -> {
                    store.remove(arg(args));
                    publish();
                    msg(who, "Achievement " + args[1] + " entfernt.", NamedTextColor.GREEN);
                }
                case "reload" -> {
                    store.load();
                    publish();
                    msg(who, store.all().size() + " Achievements neu geladen.", NamedTextColor.GREEN);
                }
                default -> msg(who, "Benutzung: /peakachievements <me|list|info <id>|give <spieler> <id>|revoke <spieler> <id>|enable <id>|disable <id>|add <id> <ART> <ziel|*> <anzahl> <titel...>|remove <id>|reload>", NamedTextColor.RED);
            }
        } catch (IllegalArgumentException e) {
            msg(who, e.getMessage(), NamedTextColor.RED);
        } catch (IOException e) {
            msg(who, "Fehler beim Speichern: " + e, NamedTextColor.RED);
        }
        return true;
    }

    private static String arg(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("Es fehlt die Kennung.");
        return args[1].toLowerCase(Locale.ROOT);
    }

    private void me(CommandSender who) {
        if (!(who instanceof Player p)) {
            list(who);
            return;
        }
        int total = 0, done = 0;
        for (Achievement a : store.all()) {
            if (!a.enabled) continue;
            total++;
            if (progress.isDone(p.getUniqueId(), a.id)) done++;
        }
        msg(who, "Du hast " + done + " von " + total + " Achievements. Alle ansehen mit /peakachievements list, im Spiel Taste L.", NamedTextColor.GOLD);
    }

    private void list(CommandSender who) {
        UUIDHolder me = who instanceof Player p ? new UUIDHolder(p) : null;
        if (store.all().isEmpty()) {
            msg(who, "Noch keine Achievements.", NamedTextColor.GRAY);
            return;
        }
        for (Achievement a : store.all()) {
            if (!a.enabled && me != null) continue;
            boolean done = me != null && progress.isDone(me.player.getUniqueId(), a.id);
            String state = me != null && !done && a.count > 1 && Matcher.isEvent(a.type) ? " (" + progress.counter(me.player.getUniqueId(), "a:" + a.id) + "/" + a.count + ")" : "";
            msg(who, (done ? "[x] " : "[ ] ") + a.id + "  " + a.title + state + (a.enabled ? "" : "  (aus)"), done ? NamedTextColor.GREEN : NamedTextColor.WHITE);
        }
    }

    private record UUIDHolder(Player player) {}

    private void info(CommandSender who, String id) {
        Achievement a = store.get(id);
        if (a == null) throw new IllegalArgumentException("Kein Achievement " + id + ".");
        msg(who, a.title + " (" + a.id + ")", NamedTextColor.GOLD);
        msg(who, a.description, NamedTextColor.GRAY);
        msg(who, "Art: " + a.type + (a.target.isBlank() ? "" : " " + a.target) + ", Anzahl: " + a.count + ", Rahmen: " + a.frame + ", Punkte: " + a.points, NamedTextColor.WHITE);
        int n = progress.holders(a.id);
        msg(who, n + " von " + progress.playerCount() + " Spielern haben es.", NamedTextColor.WHITE);
    }

    private void give(CommandSender who, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Benutzung: /peakachievements give <spieler> <id>");
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) throw new IllegalArgumentException("Spieler " + args[1] + " ist nicht online.");
        Achievement a = store.get(args[2].toLowerCase(Locale.ROOT));
        if (a == null) throw new IllegalArgumentException("Kein Achievement " + args[2] + ".");
        msg(who, award(p, a) ? "Vergeben." : p.getName() + " hat es schon.", NamedTextColor.GREEN);
    }

    private void revoke(CommandSender who, String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Benutzung: /peakachievements revoke <spieler> <id>");
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) throw new IllegalArgumentException("Spieler " + args[1] + " ist nicht online.");
        Achievement a = store.get(args[2].toLowerCase(Locale.ROOT));
        if (a == null) throw new IllegalArgumentException("Kein Achievement " + args[2] + ".");
        boolean had = progress.revoke(p.getUniqueId(), a.id);
        Advancement adv = Bukkit.getAdvancement(NamespacedKey.fromString(AdvancementJson.key(a.id)));
        if (adv != null) p.getAdvancementProgress(adv).revokeCriteria(AdvancementJson.CRITERION);
        progress.add(p.getUniqueId(), "a:" + a.id, -progress.counter(p.getUniqueId(), "a:" + a.id));
        msg(who, had ? "Entzogen." : p.getName() + " hatte es nicht.", NamedTextColor.GREEN);
    }

    private void add(CommandSender who, String[] args) throws IOException {
        if (args.length < 6) throw new IllegalArgumentException("Benutzung: /peakachievements add <id> <ART> <ziel|*> <anzahl> <titel...>  (ART: " + String.join(", ", java.util.Arrays.stream(Achievement.Type.values()).map(Enum::name).toList()) + ")");
        Achievement a = new Achievement();
        a.id = args[1].toLowerCase(Locale.ROOT);
        try {
            a.type = Achievement.Type.valueOf(args[2].toUpperCase(Locale.ROOT));
            a.count = Integer.parseInt(args[4]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Art oder Anzahl ungueltig.");
        }
        a.target = args[3].equals("*") ? "" : args[3].toUpperCase(Locale.ROOT);
        a.title = String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length));
        a.description = a.type + " " + (a.target.isEmpty() ? "alles" : a.target) + " x" + a.count;
        store.put(a);
        publish();
        msg(who, "Achievement " + a.id + " angelegt. Feinheiten (Symbol, Belohnung, Elternteil) in plugins/PeakAchievements/achievements.json, danach /peakachievements reload.", NamedTextColor.GREEN);
    }

    @Override
    public List<String> onTabComplete(CommandSender who, Command command, String alias, String[] args) {
        if (store == null) return List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("info", "enable", "disable", "remove").contains(sub)) return store.all().stream().map(a -> a.id).filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 2 && List.of("give", "revoke").contains(sub)) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        if (args.length == 3 && List.of("give", "revoke").contains(sub)) return store.all().stream().map(a -> a.id).filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 2 && sub.equals("mode")) return List.of("peak", "vanilla").stream().filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 3 && sub.equals("add")) return java.util.Arrays.stream(Achievement.Type.values()).map(Enum::name).filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        return List.of();
    }
}
