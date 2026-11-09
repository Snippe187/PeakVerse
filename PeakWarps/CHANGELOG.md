# Changelog

## 0.1.8
- Neues Modul **PeakWarps**: Warp-Punkte im Web anlegen, aendern und loeschen (Name, Welt, Position, Symbol, Beschreibung); Position eines Spielers uebernehmen.
- Befehle `/warp <name>`, `/warps`, `/setwarp <name>`, `/delwarp <name>`; Wartezeit (stillstehen) und Abklingzeit einstellbar (`warmup-seconds`, `cooldown-seconds`), Operatoren ohne Wartezeit.
- **Recht je Warp**: "Nur mit Recht benutzbar" legt `peakwarps.warp.<name>` an; es erscheint in den Rechte-Listen (Rollen, Spieler, LuckPerms).
- **SquareMap**: Warps ohne Beschraenkung erscheinen als Marker auf der Karte (nur wenn SquareMap installiert ist; noch nicht mit echtem SquareMap geprueft).