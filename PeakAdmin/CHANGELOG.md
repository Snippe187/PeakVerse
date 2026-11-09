# Changelog

## 0.1.8
- Neues Modul **PeakAdmin**: Seite "Verwaltung" in der Web-Oberflaeche.
- **Spieler**: Liste (online/offline), Kick, Ban/Entbannen, Stummschalten, Whitelist, OP (nur Operatoren), Heilen, Spielmodus, XP-Level, Teleport zu Spieler; Inventar und Endertruhe ansehen, Gegenstaende entfernen/geben/alles leeren; Fortschritte (Advancements) ansehen, einzeln oder alle vergeben/entziehen. Inventar, Endertruhe und Fortschritte nur bei Spielern, die online sind.
- **Konsole**: Live-Log (logs/latest.log) und Befehle senden (nur Operatoren, protokolliert).
- **Status**: TPS, MSPT, RAM, Spieler, Uptime, Welten (Entitaeten/Chunks) mit Verlauf-Diagrammen.
- **Chat**: Live-Chat lesen, Nachricht an alle senden, Stummschaltungen verwalten (`mutes.json`).
- **Sicherungen und Neustart**: Welt-Sicherung als ZIP (von Hand oder alle X Stunden, alte werden aufgeraeumt), Neustart mit Countdown und Chat-Warnungen, optional taeglicher Neustart (`restart.daily-at`).
- Alle Aenderungen stehen im Protokoll (Reiter Protokoll).