# Ablaufplan: Server-Verwaltung im PeakCore-Web

Entschieden mit dem Nutzer:
- Reihenfolge: Admin-Tools zuerst, dann Rechte, Warps/Karte, WorldGuard/WorldEdit.
- Pro fertigem, getestetem Paket: Commit + Push in die privaten Repos. Kein Release ohne ausdrückliches Ja.
- Spieler-Daten: ansehen UND bearbeiten, jede Änderung im Audit-Log.
- Rechte: LuckPerms als normales Plugin nutzbar, alles davon im Web verwaltbar. Ohne LuckPerms greift ein eigenes Gruppen-System (Bukkit-Rechte).
- Karte: SquareMap (vorhanden), kein eigenes Kartensystem.
- Warps: im Web anlegen, `/warp`, Recht je Warp, Marker auf SquareMap.

## Pro Paket (immer gleich)
1. Modul/Erweiterung bauen (neues Modul über `tools\new-module.ps1`, sonst im bestehenden Modul).
2. Unit-Tests, `tools\build-all.ps1 -Install`.
3. Test auf `run\server` über die Web-API, Oberfläche im Browser ansehen.
4. CHANGELOG, Commit, Push (Memory: GitHub-Push erlaubt).
5. Kurzer Bericht: was geht, was nicht geprüft ist.

## Pakete
0. PeakEnchants: Oberfläche im Browser prüfen, Vergabe an echten Spieler, Commit + Push.
1. Spieler-Verwaltung: Liste online/offline, Kick/Ban/Mute/Whitelist/OP, Inventar, Endertruhe, Advancements, Notizen, Verwarnungen.
2. Konsole & Befehle: Live-Log, Befehle senden (nur Operatoren), Verlauf.
3. Backups & Neustart: Welt-Backup, Zeitplan, Neustart mit Countdown, geplante Aufgaben.
4. Status, Audit-Log, Chat: TPS/RAM-Verlauf, Wer-hat-was-geändert, Chat lesen/senden, Alarm per Discord-Webhook.
5. Rechte: Erkennung von LuckPerms (Gruppen/Spieler/Rechte lesen und schreiben) sonst eigenes Gruppen-System; Rechteliste aus den installierten Plugins; Rollen mit feinen Web-Rechten für Moderatoren.
6. PeakWarps + Karte: Warps (Web, `/warp`, Recht je Warp), Reiter Karte (SquareMap eingebettet, Tunnel), Marker über die SquareMap-API.
7. WorldGuard/WorldEdit: Regionen und Flags im Web, Regionen auf der Karte, Schematics verwalten, Auswahl als Region/Warp.

## Offene Hinweise
- Auf dem Test-Server gibt es weder SquareMap, LuckPerms, WorldGuard noch WorldEdit; die Anbindungen werden mit Tests und, wo möglich, mit den Plugins vom Test-Server geprüft.
- Das Release (Entwurf 0.1.8 ersetzen oder 0.1.9) braucht ein ausdrückliches Ja.
