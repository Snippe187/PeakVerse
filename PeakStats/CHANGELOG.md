# Changelog

## 0.1.8
- Neues Modul **PeakStats** (Paket 1): zaehlt Spielzeit (aktiv und AFK getrennt), Anmeldungen, Kills (Spieler und Mob-Arten), Tode, Schaden, abgebaute Bloecke (je Blockart, Erze), gesetzte Bloecke, Strecke und hergestellte Items. Creative zaehlt nicht fuer Kills, Bloecke und Strecke; selbst gesetzte Bloecke zaehlen beim schnellen Abbauen nicht.
- Alle Werte werden sofort gespeichert (jede Aenderung gleich, keine Zeitabstaende).
- Speicher: SQLite-Datenbank plugins/PeakStats/stats.db (Summen, Tageswerte, Einzelereignisse 90 Tage). Vanille-Statistiken werden beim ersten Login einmalig als Startwert uebernommen.
- Web-Reiter **Statistik**: Spielerliste mit allen Werten (sortierbar), Verlauf der letzten 30 Tage, letzte Ereignisse, Bestenlisten nach Zeitraum, Freigabe je Wert fuer Spieler (Standard: nur Admins), Werte setzen und zuruecksetzen (nur Operatoren, protokolliert), CSV-Export. Noch nicht im Spiel geprueft; /stats, /top, PeakBoard-Zeile, Spieler-Webseite und Mod-Bildschirm folgen.
