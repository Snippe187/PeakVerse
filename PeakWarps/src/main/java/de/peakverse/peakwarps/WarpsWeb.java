package de.peakverse.peakwarps;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

/** Web-Seite von PeakWarps ({@code /api/peakwarps/...}): Warps anlegen, aendern und loeschen. */
public final class WarpsWeb {
    private static final Gson GSON = new Gson();

    /** Zugriff auf den Server. */
    public interface Host {
        List<String> worlds();

        /** Position eines Spielers (online) als {world,x,y,z,yaw,pitch} oder {@code null}. */
        WarpStore.Warp positionOf(String player);

        List<String> onlinePlayers();

        /** Wird nach jeder Aenderung aufgerufen (Rechte und Karten-Marker neu setzen). */
        void changed();

        boolean mapAvailable();
    }

    private final WebRegistry web;
    private final WarpStore store;
    private final Host host;

    public WarpsWeb(WebRegistry web, WarpStore store, Host host) {
        this.web = web;
        this.store = store;
        this.host = host;
    }

    public void register(String script) {
        web.panel("Warps", "Warps", script);
        web.route("state", r -> main(() -> WebResponse.json(state())));
        web.route("save", r -> change(r, o -> {
            WarpStore.Warp w = GSON.fromJson(o, WarpStore.Warp.class);
            store.save(w, r.admin());
            web.log(r.admin(), "Warp gespeichert: " + w.name);
        }));
        web.route("delete", r -> change(r, o -> {
            String name = o.get("name").getAsString();
            if (!store.delete(name)) throw new IllegalArgumentException("Unbekannter Warp");
            web.log(r.admin(), "Warp geloescht: " + name);
        }));
        web.route("here", r -> main(() -> {
            WarpStore.Warp p = host.positionOf(r.query().getOrDefault("player", ""));
            if (p == null) throw new IllegalArgumentException("Der Spieler ist nicht online.");
            return WebResponse.json(GSON.toJson(p));
        }));
    }

    private interface Edit {
        void run(JsonObject body) throws IOException;
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

    private WebResponse change(WebRequest r, Edit e) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        WebResponse res = main(() -> {
            JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
            if (o == null) throw new IllegalArgumentException("Leere Anfrage.");
            e.run(o);
            host.changed();
            return WebResponse.json(state());
        });
        if (res.status() < 300) web.changed("warps", r.admin());
        return res;
    }

    private String state() {
        JsonObject o = new JsonObject();
        o.add("warps", GSON.toJsonTree(store.all()));
        JsonArray w = new JsonArray();
        host.worlds().forEach(w::add);
        o.add("worlds", w);
        JsonArray p = new JsonArray();
        host.onlinePlayers().forEach(p::add);
        o.add("players", p);
        o.addProperty("map", host.mapAvailable());
        return o.toString();
    }
}