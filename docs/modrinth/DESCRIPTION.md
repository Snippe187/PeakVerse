# PeakVerse

**PeakVerse is an all-in-one server management suite for Paper (Minecraft 26.2) with an optional client mod.** One plugin jar, one client jar, one web interface.

## What you get

**Server plugin `PeakCore-<version>.jar`** (Paper 26.2, Java 25) with these built-in modules - each can be switched off in `config.yml`:

- **PeakCore** - web interface (login via one-time link, phone pairing, tunnel through the Minecraft connection), roles and permissions, per-player notes, warnings and sanctions, error board, audit log, update checker with beta channel, resource pack builder and pack library.
- **PeakAdmin** - player management (kick, ban, mute, whitelist, op, inventory, ender chest, advancements), live console, TPS/RAM history, chat, world backups with schedule, restart with countdown, optional LuckPerms management.
- **PeakWarps** - warps with per-warp permission, `/warp`, web editor, markers on SquareMap.
- **PeakSound** - uploadable records (MP3, FLAC, WAV, OGG), jukebox network, 7.1.4 speakers, DJ desk, radio, spotlights, song library.
- **TexturePeak** - custom item textures with pixel editor, edit vanilla textures, item abilities.
- **PeakBoard** - scoreboards, tab list and team editor with live preview and per-player sidebars.
- **PeakAchievements** - custom achievements as real advancements with rewards, plus an editor for all advancements.
- **PeakEnchants** - manage datapack enchantment addons (for example Enchantology) and edit, create and hand out enchantments from the web.

**Client mod `PeakVerse-Mod-<version>.jar`** (Fabric) - one jar for players. It only activates features that the server offers; on other servers it does nothing. Needs the Fabric API.

## Install

1. Server: put `PeakCore-<version>.jar` into `plugins/`, start once, run `/peakweb` in game for your login link.
2. Players (optional): put `PeakVerse-Mod-<version>.jar` and the Fabric API into `mods/`. If you used the old `PeakCore-Mod` / `PeakSound-Mod`, delete them first.

## Notes

- LuckPerms, SquareMap, WorldGuard and WorldEdit are optional; the matching features appear only when they are installed.
- Source and issues: see the project links.

---

## Deutsch

PeakVerse ist eine Komplett-Verwaltung fuer Paper-Server (Minecraft 26.2) mit optionaler Client-Mod: ein Plugin-Jar, ein Mod-Jar, eine Web-Oberflaeche. Enthalten sind Spieler-, Konsolen-, Backup- und Rechte-Verwaltung, Warps, Musik und Audio (PeakSound), eigene Texturen, Scoreboards, Achievements und Datapack-Verzauberungen. Die Client-Mod aktiviert nur, was der Server anbietet.