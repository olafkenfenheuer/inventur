<p align="center">
  <img src="docs/screenshots/icon.png" width="96" alt="App-Icon">
</p>

<h1 align="center">Inventur</h1>

<p align="center">
  Android-App für die Inventur mit dem Bluetooth-Barcodescanner <b>Inateck BCST-47</b>.
</p>

## Überblick

**Inventur** ist eine schlanke Android-App, um bei einer Bestandsaufnahme
Inventarnummern per Bluetooth-Scanner, Gerätekamera oder von Hand zu erfassen.
Jede Nummer wird mit Datum und Uhrzeit als eigene Position gespeichert,
versehentliche Doppel-Scans werden erkannt und rot markiert, und die fertige
Liste lässt sich als CSV-Datei teilen oder speichern (z. B. per E-Mail oder auf
einen USB-Stick).

Für Inventuren mit **mehreren Geräten oder Personen** trägt jeder Scan eine frei
wählbare **Geräte-/Benutzerkennung** (CSV-Spalte „Erfasst von") – so bleibt beim
Zusammenführen der Listen nachvollziehbar, woher jeder Eintrag stammt.

Der Scanner arbeitet in einem von zwei Modi: als Bluetooth-Tastatur (**Einfacher
Ausgabemodus**, „tippt" jede Inventarnummer gefolgt von Enter, die App fängt diese
Eingaben ab) oder im **Expertenmodus**, in dem er jeden Scan als Bluetooth-Nachricht
an die App sendet – zuverlässiger, auch bei ausgeschaltetem Bildschirm und mit der
App im Hintergrund (Dienst im Vordergrund mit Benachrichtigung). Die
Scannereinstellungen nutzen das
[Inateck Scanner SDK](https://github.com/Inateck-Technology-Inc/android_sdk), um den
BCST-47 zu verbinden, den Modus umzustellen (geführt oder per Schalter), ihn
vollständig zu konfigurieren, Einstellungen zu sichern und wieder einzuspielen, den
Scanner-Cache (Inventurmodus) per USB-Kabel hochzuladen und den Scanner per Barcode
auf Werkseinstellungen zurückzusetzen.

📖 **[Bedienungsanleitung](https://app.kenfenheuer.net/google/inventur-anleitung.html)** ·
🎬 **[Demo-Video](https://github.com/olafkenfenheuer/inventur/releases/download/v2.1/inventur-2.1-demo.mp4)** ·
📦 **[Aktuelles Release](https://github.com/olafkenfenheuer/inventur/releases/latest)**

## Screenshots

### Smartphone

<p align="center">
  <img src="docs/screenshots/splash.png" width="220" alt="Startbildschirm mit App-Icon und Copyright">
  &nbsp;&nbsp;
  <img src="docs/screenshots/main.png" width="220" alt="Inventurliste mit Zeitstempeln, Duplikat-Markierung und Bemerkungen">
  &nbsp;&nbsp;
  <img src="docs/screenshots/camera.png" width="220" alt="Barcode-Scan mit der Gerätekamera">
</p>

<p align="center">
  <img src="docs/screenshots/manual.png" width="220" alt="Inventarnummer von Hand eingeben">
  &nbsp;&nbsp;
  <img src="docs/screenshots/note.png" width="220" alt="Bemerkung zu einem Scan erfassen">
  &nbsp;&nbsp;
  <img src="docs/screenshots/about.png" width="220" alt="Über die App mit Version und Copyright">
</p>

<p align="center">
  <i>Startbildschirm &nbsp;·&nbsp; Inventurliste &nbsp;·&nbsp; Kamera-Scan &nbsp;·&nbsp; Nummer von Hand eingeben &nbsp;·&nbsp; Bemerkung erfassen &nbsp;·&nbsp; Über die App</i>
</p>

### Tablet

<p align="center">
  <img src="docs/screenshots/tablet-main.png" width="640" alt="Inventurliste auf dem Tablet mit Gerätekennung in der Kopfzeile, Duplikat-Markierung und Bemerkung">
</p>
<p align="center">
  <i>Inventurliste mit Gerätekennung „Tablet-1" in der Kopfzeile, roten Duplikaten und Bemerkung</i>
</p>

<p align="center">
  <img src="docs/screenshots/device-dialog.png" width="420" alt="Dialog Gerät / Benutzer zum Festlegen der Kennung">
  &nbsp;&nbsp;
  <img src="docs/screenshots/scanner.png" width="420" alt="Scannereinstellungen mit HID-Statusbanner und Modus-Barcodes">
</p>
<p align="center">
  <i>Geräte-/Benutzerkennung festlegen &nbsp;·&nbsp; Scannereinstellungen mit HID-Statusbanner</i>
</p>

<p align="center">
  <img src="docs/screenshots/scanner-config.png" width="420" alt="Scanner-Konfiguration nach Kategorien gegliedert: Scan-Modus, Barcode-Typ, Datenverarbeitung, Codierungseinstellungen, Cache-Verwaltung">
  &nbsp;&nbsp;
  <img src="docs/screenshots/mode-barcodes.png" width="420" alt="Moduswechsel-QR-Codes direkt auf dem Display">
</p>
<p align="center">
  <i>Konfiguration nach Kategorien &nbsp;·&nbsp; Moduswechsel-QR-Codes (GATT ↔ HID) vom Display abscannen</i>
</p>

<p align="center">
  <img src="docs/screenshots/tablet-usb.png" width="420" alt="CSV-Export direkt auf einen angeschlossenen USB-Stick">
</p>
<p align="center">
  <i>CSV-Export auf USB-Stick</i>
</p>

## Funktionen

- **Scannen per HID-Tastaturmodus:** Der BCST-47 wird als Bluetooth-Tastatur
  gekoppelt und „tippt" jede Inventarnummer + Enter. Die App fängt das global ab
  (`MainActivity.dispatchKeyEvent`) und legt jeden Scan als eigene Position an.
- **Manuelle Eingabe** über den Button „Nummer eingeben": Ist ein Barcode
  beschädigt oder nicht lesbar, lässt sich die Inventarnummer von Hand erfassen.
  Sie landet über denselben Weg wie ein Scan in der Liste (inkl. Zeitstempel und
  Duplikat-Erkennung); während der Eingabe pausiert die HID-Scan-Erfassung.
- **Ein Eintrag pro Scan** mit sekundengenauem Datum/Uhrzeit – keine Mengen,
  Inventarnummern sind eindeutig.
- **Duplikat-Erkennung:** versehentlich doppelt gescannte Nummern werden rot
  hervorgehoben und als „Duplikate" gezählt.
- **Kamera-Scan:** Barcodes direkt mit der Gerätekamera einlesen – mit
  5-Sekunden-Entprellung gegen versehentliche Doppelerfassung. Der zuletzt
  erkannte Barcode wird groß angezeigt (zum Prüfen des Inhalts), und der
  Bildschirm bleibt dabei im Hochformat fixiert.
- **Geräte-/Benutzerkennung:** frei wählbare Kennung in der Kopfzeile
  (z. B. „Tablet-1" oder ein Name); jeder neue Scan speichert sie zum
  Erfassungszeitpunkt. Ohne Kennung erinnert ein roter Hinweis.
- **Bemerkungen** je Scan – per Long-Press oder Bearbeiten-Button.
- **Löschen mit Bestätigung** (einzeln und ganze Liste).
- **CSV-Export & Teilen** (`;`-getrennt, UTF-8 mit BOM für Excel; Spalten:
  Nr, Inventarnummer, Zeitpunkt, **Erfasst von**, Bemerkung).
- **Persistenz:** die Liste übersteht einen Neustart (lokale JSON-Datei).
- **Scannereinstellungen:** Scanner verbinden (automatisch beim Öffnen),
  **Expertenmodus** (Schalter und geführte Umstellung), Hinweis „Kein Scanner
  verbunden" mit Direktlink zu den Bluetooth-Einstellungen, vollständige
  **Konfiguration nach Kategorien** (Scan-Modus, Barcode-Typ, Datenverarbeitung,
  Codierungseinstellungen, Cache-Verwaltung – Werte-Semantik laut offizieller
  SDK-Doku; der Scanner bestätigt jede Änderung mit einem Ton),
  **Einstellungen sichern / Sicherung einspielen** und **Scanner zurücksetzen
  (Werkseinstellungen)** mit vier Barcodes direkt auf dem Display.
- **Scans im Hintergrund (Expertenmodus):** Die App liest die Bluetooth-Nachrichten
  des Scanners mit und hält die Verbindung über einen Dienst im Vordergrund auch bei
  ausgeschaltetem Bildschirm; bei Abbruch verbindet sie selbst neu. Nach dem
  Umstellen findet die App den Scanner unter seiner neuen Adresse selbst wieder.
- **Scanner-Cache (Inventurmodus):** Scannen ohne Verbindung; der Cache wird per
  USB-Kabel hochgeladen (Popup beim Anstecken) und danach optional geleert.
- **Bildschirm anlassen**, solange die App offen ist (Menü, Standard: an).
- **Startbildschirm & „Über die App"** mit App-Version und Copyright-Hinweis
  (Menü oben rechts).

## So funktioniert das Scannen

1. In der Kopfzeile die **Geräte-/Benutzerkennung** festlegen (z. B. „Tablet-1").
2. BCST-47 verbinden: entweder als Bluetooth-Tastatur koppeln (**Einfacher
   Ausgabemodus**; das Statusbanner wird grün, sobald Scans ankommen) oder in den
   Scannereinstellungen auf den **Expertenmodus** umstellen (die App verbindet den
   Scanner selbst, „Scans im Hintergrund empfangen" einschalten; den Scanner dann
   **nicht** in den Android-Bluetooth-Einstellungen koppeln).
3. App öffnen und scannen – die Inventarnummern erscheinen automatisch mit
   Zeitstempel in der Liste.
4. Ist ein Barcode nicht lesbar, über den Button **„Nummer eingeben"** die
   Inventarnummer von Hand erfassen (oder den **Kamera-Scan** nutzen).
5. Bei Bedarf Bemerkungen ergänzen und Fehlscans löschen.
6. Über das Teilen-Symbol die CSV exportieren – oder direkt speichern,
   z. B. auf einen USB-Stick.

## Architektur-Hinweis

Das Inateck BLE-SDK (`inateck-scanner-ble-2.0.0`) besitzt **keinen öffentlichen
Echtzeit-Callback für gescannte Barcodes** – es dient zum Verbinden, Abfragen und
Konfigurieren. Der Scan-Empfang läuft deshalb auf zwei Wegen:

1. **Einfacher Ausgabemodus:** Der Scanner ist eine Bluetooth-Tastatur;
   `MainActivity.dispatchKeyEvent` fängt die Eingaben ab.
2. **Expertenmodus:** Der Scanner sendet Scans als GATT-Nachricht
   (`C1 | Länge | Barcode | Prüfsumme`). Die App hängt sich dazu in den
   Notify-Callback des SDK (`NotifyScanHook`, ändert die SDK-Befehle nicht) und wertet
   die Rahmen aus (`ScanFrameParser`); `ScannerService` hält die Verbindung als
   Dienst im Vordergrund. Der Modus wird über die Scanner-Einstellung `bt_mode_low`
   umgeschaltet (0 = Expertenmodus, 1 = Einfacher Ausgabemodus).

Den **Scanner-Cache** liefert der Scanner nur über das USB-Kabel (generisches
HID-Gerät `0483:5750`); die App liest die Berichte selbst (`UsbCacheReader`).

Die nativen SDK-Bibliotheken (`libscanner_cmd.so`, JNA) liegen nur für
**arm64-v8a** vor (`abiFilters += "arm64-v8a"`). Der Inventur-/CSV-Kern
läuft dennoch auf jedem Gerät; nur die nativen Konfigurationsbefehle brauchen
arm64.

## Technik

- Kotlin, Jetpack Compose, Material 3
- minSdk 24, targetSdk 36
- SDK-Jar unter `app/libs/`, JNA als Gradle-Abhängigkeit (`@aar`, 16-KB-Seiten-kompatibel)
- Abhängigkeiten: Inateck BLE-SDK, FastBle, Gson

## Build

```bash
./gradlew assembleClassicDebug      # Debug-APK (Kennung com.kenfenheuer.inventur.debug)
./gradlew testClassicDebugUnitTest
./gradlew assembleClassicRelease bundleClassicRelease   # benötigt keystore.properties (nicht im Git)
```

Benötigt ein vollständiges JDK 17+ (in Android Studio wird automatisch das
gebündelte JBR genutzt). Die fertige APK liegt danach unter
`app/build/outputs/apk/classic/debug/app-classic-debug.apk`.

Der Kern dieser App wird gemeinsam mit [Inventur Pro](https://github.com/olafkenfenheuer/inventur-pro)
(mit Server-Abgleich) entwickelt und von dort in dieses Repo gespiegelt.
