package de.peakverse.peakregions;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Web-Seite von PeakRegions ({@code /api/peakregions/...}): WorldGuard-Regionen und Flags, WorldEdit-Schematics. */
public final class RegionsWeb {
    private static final Gson GSON = new Gson();

    /** Zugriff auf die Plugins (Aufrufe nur auf dem Haupt-Thread). */
    public interface Host {
        boolean worldGuard();

        boolean worldEdit();

        boolean map();

        void regionsChanged();

        /** Setzt oder loescht ein Flag ueber den Befehl /region flag; {@code value == null} loescht. Liefert eine Fehlermeldung oder null. */
        String setFlag(String world, String region, String flag, String value);
    }

    private final WebRegistry web;
    private final SchemStore schems;
    private final Host host;

    public RegionsWeb(WebRegistry web, SchemStore schems, Host host) {
        this.web = web;
        this.schems = schems;
        this.host = host;
    }

    private interface Action {
        void run(JsonObject o) throws Exception;
    }

    public void register(String script) {
        web.panel("Regionen", "Regions", script);
        web.route("state", r -> main(() -> WebResponse.json(state())));
        web.route("regions", r -> main(() -> {
            needWg();
            return WebResponse.json(WgBridge.regions(r.query().getOrDefault("world", "")).toString());
        }));
        web.route("region/create", r -> change(r, "Region angelegt", o -> WgBridge.create(str(o, "world"), str(o, "id"), num(o, "x1"), num(o, "y1"), num(o, "z1"), num(o, "x2"), num(o, "y2"), num(o, "z2"))));
        web.route("region/delete", r -> change(r, "Region geloescht", o -> WgBridge.remove(str(o, "world"), str(o, "id"))));
        web.route("region/priority", r -> change(r, "Region-Prioritaet geaendert", o -> WgBridge.priority(str(o, "world"), str(o, "id"), num(o, "priority"))));
        web.route("region/player", r -> change(r, "Region-Mitglied geaendert", o -> {
            OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(str(o, "uuid")));
            WgBridge.player(str(o, "world"), str(o, "id"), p.getUniqueId(), str(o, "role").equals("owner"), o.get("on").getAsBoolean());
        }));
        web.route("region/flag", r -> change(r, "Region-Flag geaendert", o -> {
            String err = host.setFlag(str(o, "world"), str(o, "id"), str(o, "flag"), o.has("value") && !o.get("value").isJsonNull() ? o.get("value").getAsString() : null);
            if (err != null) throw new IllegalArgumentException(err);
        }));
        web.route("schem/upload", r -> {
            if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
            return changeNoBody(r, "Schematic hochgeladen: " + r.query().getOrDefault("name", ""), () -> schems.save(r.query().getOrDefault("name", ""), r.body()));
        });
        web.route("schem/delete", r -> change(r, "Schematic geloescht", o -> {
            if (!schems.delete(str(o, "name"))) throw new IllegalArgumentException("Datei nicht gefunden");
        }));
        web.route("schem/download", r -> {
            try {
                return WebResponse.bytes("application/octet-stream", schems.read(r.query().getOrDefault("name", "")));
            } catch (IllegalArgumentException e) {
                return WebResponse.error(400, e.getMessage());
            }
        });
    }

    private void needWg() {
        if (!host.worldGuard()) throw new IllegalStateException("WorldGuard ist nicht installiert.");
    }

    private static boolean isOp(String name) {
        for (OfflinePlayer op : Bukkit.getOperators()) if (name.equalsIgnoreCase(op.getName())) return true;
        return false;
    }

    private WebResponse main(Callable<WebResponse> c) throws Exception {
        try {
            return web.onMain(c);
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
            return WebResponse.error(400, e.getMessage() == null ? "Ungueltige Eingabe." : e.getMessage());
        } catch (NullPointerException | UnsupportedOperationException | ClassCastException e) {
            return WebResponse.error(400, "Ungueltige Eingabe (Feld fehlt oder hat den falschen Typ).");
        } catch (IOException e) {
            return WebResponse.error(500, "Dateifehler: " + e.getMessage());
        }
    }

    private interface Plain {
        void run() throws Exception;
    }

    private WebResponse changeNoBody(WebRequest r, String what, Plain p) throws Exception {
        WebResponse res = main(() -> {
            if (!isOp(r.admin())) throw new IllegalStateException("Nur fuer Operatoren.");
            p.run();
            return WebResponse.json(state());
        });
        if (res.status() < 300) {
            web.log(r.admin(), what);
            web.changed("regions", r.admin());
        }
        return res;
    }

    private WebResponse change(WebRequest r, String what, Action a) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
        if (o == null) return WebResponse.error(400, "Leere Anfrage.");
        return changeNoBody(r, what + (o.has("id") ? ": " + o.get("id").getAsString() : ""), () -> {
            if (what.startsWith("Region")) {
                needWg();
                a.run(o);
                host.regionsChanged();
            } else {
                a.run(o);
            }
        });
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) throw new IllegalArgumentException("Feld fehlt: " + k);
        return o.get(k).getAsString();
    }

    private static int num(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) throw new IllegalArgumentException("Feld fehlt: " + k);
        return o.get(k).getAsInt();
    }

    private String state() throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("worldGuard", host.worldGuard());
        o.addProperty("worldEdit", host.worldEdit());
        o.addProperty("map", host.map());
        JsonArray worlds = new JsonArray();
        for (org.bukkit.World w : Bukkit.getWorlds()) worlds.add(w.getName());
        o.add("worlds", worlds);
        o.add("flags", host.worldGuard() ? WgBridge.flagNames() : new JsonArray());
        JsonArray list = new JsonArray();
        for (SchemStore.Schem s : schems.list()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name());
            j.addProperty("size", s.size());
            j.addProperty("time", s.time());
            list.add(j);
        }
        o.add("schematics", list);
        o.addProperty("schematicFolder", schems.folder().toString());
        return o.toString();
    }
}