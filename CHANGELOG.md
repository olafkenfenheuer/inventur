# Changelog

Alle nennenswerten Änderungen an der App **Inventur**.
Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
Versionierung nach `versionName (versionCode)`.

## [1.6 (7)] – 2026-07-18

### Geändert
- Kamera-Scan-FAB responsiv: auf breiten Bildschirmen mit Text „Kamera-Scan",
  auf schmalen mit Kurzlabel „Kamera".
- FAB-Gruppe zentriert (gleicher Randabstand links/rechts).
- Leerhinweis („Noch keine Inventarnummern …") vertikal zentriert und mit
  seitlichem Rand.
- System-Zurück im Kamera-Scan kehrt zur Inventurliste zurück.
- Version auf 1.6 (versionCode 7) angehoben.

## [1.5 (6)] – 2026-07-18

### Hinzugefügt
- Inventurscan per Gerätekamera (ZXing) als Alternative zum Bluetooth-Scanner:
  fortlaufender Scan mit Ton-/Vibrationsrückmeldung, Taschenlampen-Schalter und
  Duplikat-Entprellung. Das Kamerabild wird ausschließlich lokal und in Echtzeit
  verarbeitet.
- Kamera-Scan über einen eigenen FAB neben „Nummer eingeben" erreichbar.

### Geändert
- Version auf 1.5 (versionCode 6) angehoben.
- Datenschutzerklärung und Data-Safety-Angaben um den Kamera-Zugriff ergänzt.

## [1.4 (5)] – 2026-07-17

Neuer Play-Store-Upload. Keine funktionalen App-Änderungen gegenüber 1.3.

### Geändert
- Version auf 1.4 (versionCode 5) angehoben für einen neuen Play-Store-Upload.

### Hinzugefügt
- Vollständige Play-Store-Grafiken: 512×512-Icon, Feature-Grafik 1024×500,
  hochauflösende Screenshots (1080×2400) unter `docs/`.

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
