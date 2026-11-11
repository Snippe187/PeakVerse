# Arbeitsablauf PeakVerse

Stand: 2026-11-11. Dieser Ablauf gilt für jede neue Funktion. Er hält fest, wie wir arbeiten und was als Nächstes dran ist.

## 1. So läuft ein Paket

1. **Fragen vor dem Bauen.** Bei jeder neuen Funktion zuerst Fragen stellen (Umfang, Aussehen, Rechte, Daten). Erst nach den Antworten wird gebaut.
2. **Kleines Paket schneiden.** Ein Paket ist in einem Zug baubar und testbar. Größere Wünsche werden in Pakete geteilt (siehe Plan unten).
3. **Bauen mit Tests.** Gradle-Tests für alles, was sich ohne Spiel prüfen lässt (Datenbank, Regeln, Eingabeprüfung). Neue Eingaben werden geprüft (Namen, Zahlen, Pfade), Ausgaben im Web werden maskiert.
4. **Auf dem Test-Server prüfen.** Neu bauen und installieren (`tools\build-all.ps1 -Install`), Server neu starten, über Web und Dev-Client prüfen. Was nur im echten Spiel geht, wird dem Nutzer zum Testen gegeben und als „nicht geprüft“ benannt.
5. **Commit und Push** nach jedem fertigen, getesteten Paket (private Repos). Changelog des Moduls gehört dazu.
6. **Testen durch den Nutzer** auf dem Test-Server (G-Portal und Instant Host später).
7. **Veröffentlichen nur mit ausdrücklichem Ja.** Release bei GitHub (`tools\release.ps1 -Upload`, Entwurf, dann „Publish“) und Modrinth-Upload sind erst nach einem klaren Ja des Nutzers dran.

## 2. Regeln

- Ehrlich berichten: Was gebaut, was getestet, was nicht geprüft ist. Nichts als fertig bezeichnen, was nicht im Spiel gesehen wurde.
- Nichts Unumkehrbares ohne Nachfrage (Welt löschen, Releases ersetzen, Daten zurücksetzen).
- Server nur im Heimnetz freigeben, `online-mode=false` nur zum Testen.
- Optionale Plugins (LuckPerms, SquareMap, WorldGuard, WorldEdit) werden nur angesprochen, wenn sie installiert sind.
- Statistik-Werte und Spieler-Daten: Admin entscheidet je Wert, was Spieler sehen. Datenschutz geht vor.

## 3. Plan der nächsten Pakete (Reihenfolge)

| # | Paket | Inhalt | Abhängigkeit |
|---|-------|--------|--------------|
| 1 | PeakStats Paket 2 | `/stats`, `/top` im Chat (klickbar), nur freigegebene Werte; PeakBoard-Zeile mit festen Werten | Freigabe je Wert (fertig) |
| 2 | PeakStats Paket 3 | Spieler-Webseite: Einmal-Link per `/stats web`, eigene Werte und Verlauf, Bestenlisten, Spieler ansehen, Vergleich | Paket 1 |
| 3 | PeakStats Paket 4 | Mod-Bildschirm Statistik im Pausenmenü, Stil wie die Fortschrittsansicht | Paket 1, Datenkanal PeakCore |
| 4 | Achievements Web | Spieler-Bereich im Web: Fortschritt je Spieler ansehen und vergeben, eigene Achievement-Rolle | PeakAdmin-Spielerseite |
| 5 | Achievements Punkte und Rang | Punktestand und Rang vom Server in den Mod-Bildschirm übertragen | Datenkanal PeakCore |
| 6 | PeakAdmin Inventar | Drag-and-drop im Web-Inventar (verschieben, Inventar ↔ Endertruhe) | Item-Log (fertig) |
| 7 | PeakAdmin Offline | Inventar und Daten von Offline-Spielern bearbeiten | Paper-Zugriff auf Spielerdaten prüfen |
| 8 | PeakAdmin Karte | Spieler live auf einer Karte (SquareMap oder eigene Karte im Web) | PeakWarps-SquareMap-Anbindung |
| 9 | PeakAdmin Alarme | Alarme und Regeln: Lag, Speicher, Absturz, ungewöhnliche Aktionen | Status-Verlauf (fertig) |
| 10 | Enchants Rezepte | Wie man Verzauberungen bekommt (Villager, Truhen, Amboss) im Web einstellen | PeakEnchants |
| 11 | Warps-Menü | Teleport-Menü im Spiel mit Symbolen | PeakWarps, Mod |
| 12 | Claims/Grundstücke | Spieler schützen Gebiet selbst, Verwaltung über PeakRegions | WorldGuard, Rechte |
| 13 | Quests/Aufgaben | Aufgabenlisten mit Belohnungen, verbunden mit Achievements | Achievements Punkte |

Die Reihenfolge ist ein Vorschlag. Der Nutzer kann jederzeit umsortieren.

## 4. Offene Punkte vor einer Veröffentlichung auf Modrinth

- Entscheidung zum Mod-Updater (lädt von GitHub, könnte gegen die Modrinth-Regeln verstoßen; nicht geprüft).
- Test von `PeakVerse-Mod` in einem echten Fabric-Client (beide eingebetteten Mods laden, alte Einzel-Jars löschen).
- Tests mit echten LuckPerms, SquareMap, WorldGuard, WorldEdit und einem fremden Plugin für die Rechte-Verwaltung.
- Icon, Galerie-Bilder, Name der Adresse (Slug), Lizenz und Quell-Link.
- Fortschrittsansicht und Statistik-Design im Spiel ansehen und abnehmen.

## 5. Veröffentlichung (Checkliste)

1. Alle Module bauen mit Tests (`tools\release.ps1`, ohne `-Upload`).
2. Changelogs enthalten den Abschnitt der Version.
3. Nutzer gibt ausdrücklich frei.
4. `tools\release.ps1 -Upload` legt einen Entwurf bei `Snippe187/PeakVerse-Releases` an.
5. Nutzer klickt „Publish release“ (oder gibt dem Assistenten das Ja dafür).
6. Server holen das Update mit `/peakcore update`; Spieler tauschen die Mod-Datei `PeakVerse-Mod-<Version>.jar`.
7. Modrinth: ein Projekt, beide Dateien je Version, Beschreibung aus `docs/modrinth/`.
