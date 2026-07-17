# Changelog

Alle nennenswerten Änderungen an der App **Inventur**.
Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
Versionierung nach `versionName (versionCode)`.

## [1.3 (4)] – 2026-07-17

Erste Veröffentlichung im Google Play Store.

### Hinzugefügt
- Store-Materialien und Datenschutzerklärung unter `docs/`.

### Geändert
- Version auf 1.3 (versionCode 4) angehoben für das Play-Store-Release.

## [1.2 (3)] – 2026-07-15

### Geändert
- „Über die App"-Dialog: Info-Icon durch das App-Icon ersetzt.

### Hinzugefügt
- README: Screenshots für Splash-Screen und „Über die App".

## [1.1 (2)] – 2026-07-15

### Hinzugefügt
- Copyright-Hinweis auf Splash-Screen und im „Über die App"-Dialog.
- Release-Signierung über ungetrackte `keystore.properties`.
- Manuelle Eingabe von Inventarnummern per FAB (für unlesbare Barcodes).
- Neues App-Icon (Klemmbrett mit Barcode und Häkchen-Badge).

## [1.0 (1)] – 2026-07-14

### Hinzugefügt
- Erste Version: Inventur-App für den Inateck BCST-47.
- Scannen im HID-Tastaturmodus, ein Eintrag pro Scan mit Zeitstempel.
- Duplikat-Erkennung, Bemerkungen je Position, Löschen mit Bestätigung.
- CSV-Export und Teilen (semikolongetrennt, UTF-8 mit BOM für Excel).
- Lokale Persistenz der Liste (JSON).
- Scanner-Bildschirm zum Verbinden und Konfigurieren (Akku, Version, HID, Lautstärke).
