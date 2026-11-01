# PeakAchievements: Plan

Neues Modul von PeakVerse: Minecraft-Fortschritte (Advancements) ansehen und verwalten und eigene Achievements mit Spaß-Mechaniken dazu.
Modul-Kennung `peakachievements`, Datenordner `plugins/PeakAchievements`, im All-in-one-Jar wie PeakSound und TexturePeak (ein-/ausschaltbar unter `modules.peakachievements.enabled`).

## Entscheidungen des Besitzers
- **Vanilla-Fortschritte:** pro Spieler ansehen (Fortschritt in Prozent), vergeben und entziehen, ausblenden/abschalten, Ranglisten.
- **Anzeige eigener Achievements:** echte Fortschritte (eigener Reiter im Fortschritts-Fenster, Toast oben rechts wie bei Minecraft).
- **Auslöser:** Minecraft-Ereignisse (töten, abbauen, craften, fischen, Orte/Biome), Zähler (z. B. 100 Zombies, Spielzeit), PeakVerse-Ereignisse (Song hochgeladen, Platte geschrieben, eigenes Item benutzt, Textur-Fähigkeit ausgelöst), versteckte und manuell vergebene.
- **Belohnungen:** Items und XP, Befehle, Titel/Präfix, Effekte (Feuerwerk, Partikel, Sound, Bildschirm-Titel).
- **Editor im Web:** Vorschau wie im Spiel (Kachel und Toast mit Rahmenform Aufgabe/Ziel/Herausforderung), Icon aus den Standard-Bildern, Vorlagen (mitgelieferte und **eigene Vorlagen, die man aus bestehenden Achievements anlegen kann**), Ketten und Reihen (Stufen 10/100/1000).
- **Spaß:** Seltenheit und Punkte mit Server-Rangliste, Server-Meldung bei Seltenem (mit Feuerwerk), Saison/Events (zeitlich begrenzt), Mitspieler-Achievements (Team-Ziele, "als Erster").
- **Mitgeliefert zum Start:** allgemeine Spaß-Reihe (z. B. Tod durch Kaktus, 100 Sprünge, in Lava schwimmen, Schaf umbenennen), PeakSound-Reihe (erster Song, DJ-Pult bauen, Radio benutzen, Party mit vielen Spielern), geheime Reihe (Easter Eggs, erst nach dem Erreichen sichtbar).
- **Rechte:** eigene Rolle "Achievements" mit Web-Teilzugriff nur auf diese Seite (passt zum Rechte-System).

## Technik und Risiken (ehrlich)
- **Eigene Fortschritte sind echte Advancements** mit dem Auslöser `minecraft:impossible`. Vergeben wird nur durch PeakCore (Paper-API `AdvancementProgress.awardCriteria`). Das ist über Versionen stabil, weil Minecraft die Bedingung nie selbst auslöst.
- **Neu anlegen oder ändern heißt: Datenpaket neu schreiben und neu laden.** Das geht über ein Datenpaket im Welt-Ordner mit `Bukkit.reloadData()`; das kann kurz ruckeln und setzt bei Spielern die Anzeige des Fortschritts-Fensters zurück. Ob das in Minecraft 26.2 sauber klappt, muss ein Test zeigen (Stufe A3). Ausweichplan: Änderungen sammeln und gebündelt veröffentlichen (passt zum Entwurf-Muster).
- **Ausblenden** von Vanilla-Fortschritten geht über das Abbrechen von `PlayerAdvancementCriterionGrantEvent` (kein Datenpaket nötig).
- **Zähler** liegen in `progress.json` je Spieler (asynchron gespeichert), nicht im Minecraft-Scoreboard, damit nichts mit PeakBoard kollidiert.
- **PeakVerse-Ereignisse** brauchen einen kleinen Ereignis-Bus im Kern (PeakSound und TexturePeak melden an, PeakAchievements hört zu). Das ändert die Core-API (nur Ergänzung).
- Befehle als Belohnung laufen mit Konsolen-Rechten: nur für die Rolle mit Voll-Zugang einstellbar und im Web-Protokoll vermerkt.

## Stufen
- [ ] **A1 Kern:** Datenmodell (Achievement, Vorlage, Auslöser, Belohnung), Speicher (`achievements.json`, `templates.json`, `progress.json`), Prüfungen und Tests
- [ ] **A2 Auslöser:** Ereignisse und Zähler, Ereignis-Bus für PeakSound/TexturePeak, Tests mit gespielten Ereignissen
- [ ] **A3 Anzeige:** Datenpaket schreiben, neu laden, Toast; Test in 26.2 im Spiel
- [ ] **A4 Belohnungen:** Items, XP, Befehle, Titel/Präfix, Effekte, "als Erster", Team-Ziele
- [ ] **A5 Web, Vanilla:** Fortschritts-Übersicht pro Spieler, vergeben/entziehen, ausblenden, Ranglisten
- [ ] **A6 Web, Editor:** Editor mit Vorschau, Icon-Auswahl, Vorlagen (eigene), Ketten, Saison
- [ ] **A7 Spaß und Start-Reihen:** Seltenheit, Punkte, Server-Meldung, mitgelieferte Achievements
- [ ] Rolle "Achievements" im Rechte-System, Doku, Testliste, Modrinth-Eintrag
