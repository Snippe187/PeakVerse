package de.peakverse.peakenchants;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.peakverse.core.api.web.WebRegistry;
import de.peakverse.core.api.web.WebRequest;
import de.peakverse.core.api.web.WebResponse;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Web-Seite von PeakEnchants ({@code /api/peakenchants/...}): Addons verwalten, Verzauberungen bearbeiten, anlegen, loeschen und vergeben. */
public final class EnchantsWeb {
    private static final Gson GSON = new Gson();

    /** Zugriff auf den Server. */
    public interface Host {
        /** Ist die Verzauberung gerade im Server geladen? */
        boolean loaded(String key);

        /** Gibt einem Spieler ein verzaubertes Buch; liefert eine Fehlermeldung oder {@code null}. */
        String giveBook(String player, String key, int level);

        /** Verzaubert das Item in der Hand eines Spielers; liefert eine Fehlermeldung oder {@code null}. */
        String applyToHand(String player, String key, int level);

        java.util.List<String> onlinePlayers();
    }

    private final WebRegistry web;
    private final EnchantStore store;
    private final Host host;

    public EnchantsWeb(WebRegistry web, EnchantStore store, Host host) {
        this.web = web;
        this.store = store;
        this.host = host;
    }

    private interface Action {
        void run() throws Exception;
    }

    public void register(String script) {
        web.panel("Verzauberungen", "Enchantments", script);
        web.route("state", r -> web.onMain(() -> WebResponse.json(state())));
        web.route("addon/upload", r -> change(r, () -> {
            String name = r.query().getOrDefault("name", "");
            String id = name.toLowerCase(Locale.ROOT).replaceAll("\\.zip$", "").replaceAll("[^a-z0-9_.-]", "_");
            store.install(id, r.body());
            web.log(r.admin(), "Datapack-Addon installiert: " + id + " (" + r.body().length + " Bytes)");
        }));
        web.route("addon/enable", r -> change(r, () -> {
            JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
            store.setEnabled(o.get("id").getAsString(), o.get("enabled").getAsBoolean());
            web.log(r.admin(), "Datapack-Addon " + (o.get("enabled").getAsBoolean() ? "eingeschaltet: " : "ausgeschaltet: ") + o.get("id").getAsString());
        }));
        web.route("addon/remove", r -> change(r, () -> {
            String id = GSON.fromJson(r.text(), JsonObject.class).get("id").getAsString();
            store.remove(id);
            web.log(r.admin(), "Datapack-Addon entfernt (in backups gesichert): " + id);
        }));
        web.route("addon/unpack", r -> change(r, () -> {
            String id = GSON.fromJson(r.text(), JsonObject.class).get("id").getAsString();
            store.unpack(id);
            web.log(r.admin(), "Datapack-ZIP entpackt: " + id);
        }));
        web.route("enchant", r -> change(r, () -> {
            JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
            String addon = o.get("addon").getAsString(), key = o.get("key").getAsString();
            boolean isNew = store.enchant(addon, key) == null;
            store.stage(addon, key, o.getAsJsonObject("json"));
            if (o.has("tags")) {
                Set<String> tags = new LinkedHashSet<>();
                for (var t : o.getAsJsonArray("tags")) tags.add(t.getAsString());
                store.stageTags(addon, key, tags);
            }
            web.log(r.admin(), (isNew ? "Verzauberung angelegt (Entwurf): " : "Verzauberung bearbeitet (Entwurf): ") + key);
        }));
        web.route("delete", r -> change(r, () -> {
            JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
            store.stageDelete(o.get("addon").getAsString(), o.get("key").getAsString());
            web.log(r.admin(), "Verzauberung geloescht (Entwurf): " + o.get("key").getAsString());
        }));
        web.route("publish", r -> change(r, () -> {
            int n = store.publish();
            web.log(r.admin(), "Verzauberungen veroeffentlicht (" + n + " Dateien, Sicherung in backups). Neustart noetig.");
        }));
        web.route("discard", r -> change(r, () -> {
            store.discard();
            web.log(r.admin(), "Verzauberungs-Entwurf verworfen");
        }));
        web.route("give", r -> {
            if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
            return web.onMain(() -> {
                JsonObject o = GSON.fromJson(r.text(), JsonObject.class);
                String player = o.get("player").getAsString(), key = o.get("key").getAsString();
                int level = o.has("level") ? o.get("level").getAsInt() : 1;
                boolean hand = o.has("mode") && o.get("mode").getAsString().equals("hand");
                String err = hand ? host.applyToHand(player, key, level) : host.giveBook(player, key, level);
                if (err != null) return WebResponse.error(400, err);
                web.log(r.admin(), (hand ? "Item verzaubert: " : "Buch gegeben: ") + key + " " + level + " -> " + player);
                return WebResponse.ok();
            });
        });
    }

    private WebResponse change(WebRequest r, Action action) throws Exception {
        if (!r.method().equals("POST")) return WebResponse.error(405, "POST");
        return web.onMain(() -> {
            try {
                action.run();
            } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException e) {
                return WebResponse.error(400, e.getMessage() == null ? "Ungueltige Eingabe." : e.getMessage());
            } catch (NullPointerException | UnsupportedOperationException | ClassCastException e) {
                return WebResponse.error(400, "Ungueltige Eingabe (Feld fehlt oder hat den falschen Typ).");
            } catch (IOException e) {
                return WebResponse.error(500, "Dateifehler: " + e.getMessage());
            }
            web.changed("enchants", r.admin());
            return WebResponse.json(state());
        });
    }

    private String state() throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("restartNeeded", store.restartNeeded());
        o.addProperty("draft", store.hasDraft());
        o.addProperty("draftSize", store.draftSize());
        JsonArray tags = new JsonArray();
        EnchantStore.TAGS.forEach(tags::add);
        o.add("tagNames", tags);
        JsonArray players = new JsonArray();
        host.onlinePlayers().forEach(players::add);
        o.add("players", players);
        JsonArray addons = new JsonArray();
        for (EnchantStore.Addon a : store.addons()) {
            JsonObject n = new JsonObject();
            n.addProperty("id", a.id());
            n.addProperty("description", a.description());
            n.addProperty("enabled", a.enabled());
            n.addProperty("zip", a.zip());
            n.addProperty("count", a.count());
            addons.add(n);
        }
        o.add("addons", addons);
        JsonArray list = new JsonArray();
        for (Map.Entry<String, Map<String, JsonObject>> a : store.all().entrySet()) {
            boolean enabled = store.addons().stream().anyMatch(x -> x.id().equals(a.getKey()) && x.enabled());
            for (Map.Entry<String, JsonObject> e : a.getValue().entrySet()) {
                JsonObject n = new JsonObject();
                n.addProperty("addon", a.getKey());
                n.addProperty("key", e.getKey());
                n.addProperty("name", e.getValue() == null ? e.getKey() : EnchantStore.text(e.getValue().get("description")));
                n.addProperty("broken", e.getValue() == null);
                n.add("json", e.getValue());
                n.addProperty("loaded", host.loaded(e.getKey()));
                n.addProperty("enabled", enabled);
                n.addProperty("staged", store.isStaged(a.getKey(), e.getKey()));
                JsonArray t = new JsonArray();
                store.tagsOf(a.getKey(), e.getKey()).forEach(t::add);
                n.add("tags", t);
                list.add(n);
            }
        }
        o.add("enchants", list);
        return o.toString();
    }
}
