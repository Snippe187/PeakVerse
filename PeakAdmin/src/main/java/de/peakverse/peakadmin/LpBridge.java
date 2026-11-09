package de.peakverse.peakadmin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Anbindung an LuckPerms (nur benutzen, wenn das Plugin installiert ist, der Aufrufer prueft das vorher): Gruppen, Rechte und Gruppen-Zugehoerigkeit lesen und aendern.
 * Aendern laeuft ueber die LuckPerms-API und gilt sofort; LuckPerms speichert selbst.
 */
public final class LpBridge {
    private LpBridge() {}

    private static LuckPerms lp() {
        RegisteredServiceProvider<LuckPerms> p = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (p == null) throw new IllegalStateException("LuckPerms ist nicht bereit.");
        return p.getProvider();
    }

    private static Group group(String name) {
        Group g = lp().getGroupManager().getGroup(name);
        if (g == null) throw new IllegalArgumentException("Unbekannte Gruppe: " + name);
        return g;
    }

    private static String checkName(String name) {
        if (name == null || !name.matches("[a-z0-9_.-]{1,36}")) throw new IllegalArgumentException("Gruppenname: 1-36 Zeichen, a-z, 0-9, _ . -");
        return name;
    }

    private static String checkNode(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_.*:/-]{1,200}")) throw new IllegalArgumentException("Ungueltiges Recht: " + key);
        return key;
    }

    private static void saveGroup(Group g) {
        lp().getGroupManager().saveGroup(g).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private static JsonObject groupJson(Group g) {
        JsonObject o = new JsonObject();
        o.addProperty("name", g.getName());
        o.addProperty("display", g.getFriendlyName());
        o.addProperty("weight", g.getWeight().orElse(0));
        JsonArray perms = new JsonArray(), parents = new JsonArray();
        for (Node n : g.getNodes()) {
            if (n.getType() == NodeType.PERMISSION) {
                JsonObject p = new JsonObject();
                p.addProperty("key", n.getKey());
                p.addProperty("value", n.getValue());
                p.addProperty("temporary", n.hasExpiry());
                perms.add(p);
            } else if (n.getType() == NodeType.INHERITANCE) {
                parents.add(((InheritanceNode) n).getGroupName());
            }
        }
        o.add("permissions", perms);
        o.add("parents", parents);
        return o;
    }

    /** Alle Gruppen mit Rechten und Eltern-Gruppen sowie der Katalog aller bekannten Rechte aller Plugins. */
    public static JsonObject groups() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        lp().getGroupManager().getLoadedGroups().stream().sorted((x, y) -> x.getName().compareTo(y.getName())).forEach(g -> a.add(groupJson(g)));
        o.add("groups", a);
        JsonArray catalog = new JsonArray();
        for (Permission p : Bukkit.getPluginManager().getPermissions()) {
            JsonObject c = new JsonObject();
            c.addProperty("node", p.getName());
            c.addProperty("desc", p.getDescription() == null ? "" : p.getDescription());
            catalog.add(c);
        }
        o.add("catalog", catalog);
        return o;
    }

    public static JsonObject createGroup(String name) {
        checkName(name);
        if (lp().getGroupManager().getGroup(name) != null) throw new IllegalArgumentException("Die Gruppe gibt es schon.");
        Group g = lp().getGroupManager().createAndLoadGroup(name).orTimeout(10, TimeUnit.SECONDS).join();
        return groupJson(g);
    }

    public static void deleteGroup(String name) {
        if (name.equalsIgnoreCase("default")) throw new IllegalArgumentException("Die Gruppe default kann nicht geloescht werden.");
        lp().getGroupManager().deleteGroup(group(name)).orTimeout(10, TimeUnit.SECONDS).join();
    }

    /** Setzt ein Recht der Gruppe; {@code value == null} entfernt es. */
    public static void groupPermission(String group, String key, Boolean value) {
        Group g = group(group);
        checkNode(key);
        g.data().clear(n -> n.getType() == NodeType.PERMISSION && n.getKey().equalsIgnoreCase(key) && !n.hasExpiry());
        if (value != null) g.data().add(Node.builder(key).value(value).build());
        saveGroup(g);
    }

    public static void groupParent(String group, String parent, boolean on) {
        Group g = group(group);
        group(parent);
        if (group.equalsIgnoreCase(parent)) throw new IllegalArgumentException("Eine Gruppe kann nicht von sich selbst erben.");
        InheritanceNode n = InheritanceNode.builder(parent).build();
        if (on) g.data().add(n);
        else g.data().remove(n);
        saveGroup(g);
    }

    private static User user(UUID id) {
        return lp().getUserManager().loadUser(id).orTimeout(10, TimeUnit.SECONDS).join();
    }

    /** Gruppen und einzeln gesetzte Rechte eines Spielers. */
    public static JsonObject userInfo(UUID id) {
        User u = user(id);
        JsonObject o = new JsonObject();
        o.addProperty("primary", u.getPrimaryGroup());
        JsonArray groups = new JsonArray(), perms = new JsonArray();
        for (Node n : u.getNodes()) {
            if (n.getType() == NodeType.INHERITANCE) groups.add(((InheritanceNode) n).getGroupName());
            else if (n.getType() == NodeType.PERMISSION) {
                JsonObject p = new JsonObject();
                p.addProperty("key", n.getKey());
                p.addProperty("value", n.getValue());
                perms.add(p);
            }
        }
        o.add("groups", groups);
        o.add("permissions", perms);
        return o;
    }

    public static void userGroup(UUID id, String group, boolean on) {
        group(group);
        User u = user(id);
        InheritanceNode n = InheritanceNode.builder(group).build();
        if (on) u.data().add(n);
        else u.data().remove(n);
        lp().getUserManager().saveUser(u).orTimeout(10, TimeUnit.SECONDS).join();
    }

    public static void userPermission(UUID id, String key, Boolean value) {
        checkNode(key);
        User u = user(id);
        u.data().clear(n -> n.getType() == NodeType.PERMISSION && n.getKey().equalsIgnoreCase(key) && !n.hasExpiry());
        if (value != null) u.data().add(Node.builder(key).value(value).build());
        lp().getUserManager().saveUser(u).orTimeout(10, TimeUnit.SECONDS).join();
    }
}