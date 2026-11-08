# Changelog

## 0.1.8-beta.1
- Alle Minecraft-Fortschritte erscheinen als stille Kopien im Reiter PeakVerse (erreicht, sobald das Original erreicht ist; Minecraft bleibt die Quelle, die Kopie folgt auch nach /advancement revoke). Schalter `mirror-vanilla` in der config.yml.
- Neue Web-Seite **Achievements** (Module -> Achievements): Skill-Tree aller Knoten, Minecraft-Fortschritte und eigene Achievements bearbeiten, per Ziehen umhaengen, Entwurf mit Veroeffentlichen und Sicherung (`backups/`), Vorschau als Kachel und Toast. Die Spieler-Verwaltung folgt.

## 0.1.7
- Neues Modul: eigene Achievements als echte Minecraft-Fortschritte (eigener Reiter "PeakVerse" bei Taste L, Toast, funktioniert ohne Mod).
- Ausloeser: Mobs toeten, Bloecke abbauen/setzen, craften, angeln, Spruenge, Laufen, Spielzeit, Tode, manuell. Belohnungen: XP, Items, Konsolen-Befehle, Titel, Sound, Feuerwerk.
- Zwei Modi fuer den ganzen Server: `peak` (alle Features) und `vanilla` (alles aus, Minecraft-Fortschritte unveraendert). Umschalten mit `/peakachievements mode <peak|vanilla>` oder in der config.yml.
- Befehl `/peakachievements` (`/ach`): me, list, info, give, revoke, enable, disable, add, remove, reload. 8 Beispiel-Achievements beim ersten Start.
- Hinweis: Wechsel auf vanilla im laufenden Betrieb stoppt Ausloeser sofort, der Reiter verschwindet sicher erst nach einem Serverneustart.
