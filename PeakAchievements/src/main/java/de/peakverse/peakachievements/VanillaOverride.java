package de.peakverse.peakachievements;

/**
 * Einstellungen des Owners fuer einen Minecraft-Fortschritt (Schluessel wie {@code minecraft:story/mine_stone}).
 * Alles, was {@code null} ist, bleibt wie in Minecraft.
 */
public final class VanillaOverride {
    /** {@code false} = wird nicht vergeben und nicht angezeigt. */
    public boolean enabled = true;
    public String parent;
    public String title;
    public String description;
    public String icon;
    public Integer points;
    public Achievement.Visibility visibility;
    public Integer x;
    public Integer y;
    public Achievement.Rewards rewards;
}
