package de.peakverse.peakstats;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /stats [Spieler]} und {@code /top [Wert] [Zeitraum]}: im Chat, klickbar; Spieler sehen nur freigegebene Werte, Operatoren alle. */
public final class StatsCommands implements CommandExecutor, TabCompleter {
    private static final List<String> PERIODS = List.of("heute", "woche", "monat", "gesamt");
    private final StatsDb db;
    private final Logger logger;
    private final org.bukkit.plugin.Plugin host;

    public StatsCommands(org.bukkit.plugin.Plugin host, StatsDb db, Logger logger) {
        this.host = host;
        this.db = db;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        // Datenbank nicht im Hauptthread befragen: kurz asynchron, dann antworten (sendMessage ist thread-sicher)
        Runnable job = () -> {
            try {
                if (cmd.getName().equalsIgnoreCase("top")) top(sender, a); else stats(sender, a);
            } catch (SQLException e) {
                logger.warning("PeakStats-Befehl: " + e.getMessage());
                sender.sendMessage(Component.text("Die Statistik ist gerade nicht verfuegbar.", NamedTextColor.RED));
            }
        };
        // Konsole/RCON warten nicht auf spaetere Antworten: dort direkt ausfuehren
        if (sender instanceof Player && host != null && host.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(host, job); else job.run();
        return true;
    }

    private Set<String> allowed(CommandSender s) throws SQLException {
        if (s.isOp()) {
            java.util.HashSet<String> all = new java.util.HashSet<>();
            StatKeys.all().forEach(d -> all.add(d.key()));
            return all;
        }
        return db.visible();
    }

    private static StatKeys.Def def(String key) {
        return StatKeys.all().stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
    }

    private void stats(CommandSender s, String[] a) throws SQLException {
        UUID id;
        String name;
        if (a.length > 0) {
            id = db.idOf(a[0]);
            if (id == null) {
                s.sendMessage(Component.text("Unbekannter Spieler: " + a[0], NamedTextColor.RED));
                return;
            }
        } else if (s instanceof Player p) {
            id = p.getUniqueId();
        } else {
            s.sendMessage(Component.text("Benutzung: /stats <spieler>", NamedTextColor.RED));
            return;
        }
        name = db.nameOf(id);
        Map<String, Long> totals = db.totals(id);
        Set<String> allow = allowed(s);
        Component out = Component.text("== Statistik: ", NamedTextColor.GOLD).append(Component.text(name == null ? "?" : name, NamedTextColor.YELLOW))
                .append(Component.text(" ==", NamedTextColor.GOLD));
        boolean any = false;
        for (StatKeys.Def d : StatKeys.all()) {
            if (!allow.contains(d.key())) continue;
            any = true;
            long v = totals.getOrDefault(d.key(), 0L);
            out = out.appendNewline().append(Component.text(d.labelDe() + ": ", NamedTextColor.GRAY)).append(Component.text(StatFormat.value(d.unit(), v), NamedTextColor.WHITE)
                    .clickEvent(ClickEvent.runCommand("/top " + d.key())).hoverEvent(HoverEvent.showText(Component.text("Bestenliste anzeigen"))));
        }
        if (!any) out = out.appendNewline().append(Component.text("Es sind noch keine Werte freigegeben.", NamedTextColor.GRAY));
        s.sendMessage(out);
    }

    private void top(CommandSender s, String[] a) throws SQLException {
        Set<String> allow = allowed(s);
        String key = a.length > 0 ? StatFormat.keyFor(a[0]) : null;
        if (a.length == 0 || key == null) {
            if (a.length > 0) s.sendMessage(Component.text("Unbekannter Wert: " + a[0], NamedTextColor.RED));
            Component menu = Component.text("== Bestenlisten == ", NamedTextColor.GOLD);
            boolean any = false;
            for (StatKeys.Def d : StatKeys.all()) {
                if (!allow.contains(d.key())) continue;
                any = true;
                menu = menu.appendNewline().append(Component.text("[" + d.labelDe() + "]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/top " + d.key()))
                        .hoverEvent(HoverEvent.showText(Component.text("Top 10 anzeigen"))));
            }
            if (!any) menu = menu.appendNewline().append(Component.text("Es sind noch keine Werte freigegeben.", NamedTextColor.GRAY));
            s.sendMessage(menu);
            return;
        }
        if (!allow.contains(key)) {
            s.sendMessage(Component.text("Dieser Wert ist nicht freigegeben.", NamedTextColor.RED));
            return;
        }
        String period = StatFormat.periodFor(a.length > 1 ? a[1] : null);
        if (period == null) {
            s.sendMessage(Component.text("Zeitraum: heute, woche, monat oder gesamt", NamedTextColor.RED));
            return;
        }
        StatKeys.Def d = def(key);
        LocalDate since = StatsWeb.since(period, LocalDate.now());
        List<StatsDb.Row> rows = db.top(key, since, 10, Set.of());
        Component out = Component.text("== Top 10: " + d.labelDe() + " (" + periodName(period) + ") ==", NamedTextColor.GOLD);
        if (rows.isEmpty()) out = out.appendNewline().append(Component.text("Noch keine Werte.", NamedTextColor.GRAY));
        int rank = 1;
        for (StatsDb.Row r : rows) {
            out = out.appendNewline().append(Component.text(rank++ + ". ", NamedTextColor.GRAY))
                    .append(Component.text(r.name(), NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/stats " + r.name())).hoverEvent(HoverEvent.showText(Component.text("Statistik von " + r.name()))))
                    .append(Component.text("  " + StatFormat.value(d.unit(), r.value()), NamedTextColor.WHITE));
        }
        Component periods = Component.empty();
        for (String p : PERIODS) {
            periods = periods.append(Component.text("[" + p + "] ", p.equals(periodWord(period)) ? NamedTextColor.GREEN : NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/top " + key + " " + p)));
        }
        s.sendMessage(out.appendNewline().append(periods.decorate(TextDecoration.UNDERLINED)));
    }

    private static String periodName(String p) {
        return switch (p) { case "today" -> "heute"; case "week" -> "7 Tage"; case "month" -> "30 Tage"; default -> "gesamt"; };
    }

    private static String periodWord(String p) {
        return switch (p) { case "today" -> "heute"; case "week" -> "woche"; case "month" -> "monat"; default -> "gesamt"; };
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        try {
            if (cmd.getName().equalsIgnoreCase("top")) {
                if (a.length == 1) {
                    Set<String> allow = allowed(s);
                    StatKeys.all().stream().filter(d -> allow.contains(d.key())).forEach(d -> out.add(d.key()));
                } else if (a.length == 2) out.addAll(PERIODS);
            } else if (a.length == 1) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } catch (SQLException e) {
            return List.of();
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
