<p align="center">
  <img src="docs/screenshots/icon.png" width="96" alt="App-Icon">
</p>

<h1 align="center">Inventur</h1>

<p align="center">
  Android-App für die Inventur mit dem Bluetooth-Barcodescanner <b>Inateck BCST-47</b>.
</p>

## Überblick

**Inventur** ist eine schlanke Android-App, um bei einer Bestandsaufnahme
Inventarnummern per Bluetooth-Scanner zu erfassen. Jede gescannte Nummer wird
mit Datum und Uhrzeit als eigene Position gespeichert, versehentliche Doppel-Scans
werden erkannt und rot markiert, und die fertige Liste lässt sich als CSV-Datei
teilen (z. B. per E-Mail oder in eine Tabellenkalkulation).

Der Scanner koppelt sich als Bluetooth-Tastatur (**HID-Modus**) und „tippt" jede
Inventarnummer gefolgt von Enter – die App fängt diese Eingaben ab und trägt sie
automatisch in die Liste ein. Ein separater Scanner-Bildschirm nutzt das
[Inateck Scanner SDK](https://github.com/Inateck-Technology-Inc/android_sdk), um
den BCST-47 zu verbinden, seinen Status zu prüfen (Akku, Version) und ihn zu
konfigurieren.

## Screenshots

<p align="center">
  <img src="docs/screenshots/splash.png" width="240" alt="Startbildschirm mit App-Icon und Copyright">
  &nbsp;&nbsp;
  <img src="docs/screenshots/main.png" width="240" alt="Inventurliste mit Zeitstempeln, Duplikat-Markierung und Bemerkungen">
  &nbsp;&nbsp;
  <img src="docs/screenshots/manual.png" width="240" alt="Inventarnummer von Hand eingeben">
</p>

<p align="center">
  <img src="docs/screenshots/note.png" width="240" alt="Bemerkung zu einem Scan erfassen">
  &nbsp;&nbsp;
  <img src="docs/screenshots/scanner.png" width="240" alt="Scanner verbinden und konfigurieren">
  &nbsp;&nbsp;
  <img src="docs/screenshots/about.png" width="240" alt="Über die App mit Version und Copyright">
</p>

<p align="center">
  <i>Startbildschirm &nbsp;·&nbsp; Inventurliste &nbsp;·&nbsp; Nummer von Hand eingeben &nbsp;·&nbsp; Bemerkung erfassen &nbsp;·&nbsp; Scanner verbinden &nbsp;·&nbsp; Über die App</i>
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
- **Bemerkungen** je Scan – per Long-Press oder Bearbeiten-Button.
- **Löschen mit Bestätigung** (einzeln und ganze Liste).
- **CSV-Export & Teilen** (`;`-getrennt, UTF-8 mit BOM für Excel; Spalten:
  Nr, Inventarnummer, Zeitpunkt, Bemerkung).
- **Persistenz:** die Liste übersteht einen Neustart (lokale JSON-Datei).
- **SDK-Screen** zum Verbinden/Konfigurieren des Scanners (Akku, Version,
  Umschalten auf HID + Enter, Lautstärke).
- **Startbildschirm & „Über die App"** mit App-Version und Copyright-Hinweis
  (Menü oben rechts).

## So funktioniert das Scannen

1. BCST-47 im **HID-Tastaturmodus** mit dem Gerät koppeln (bei Bedarf über den
   Scanner-Screen der App auf „HID + Enter" umstellen).
2. App öffnen und scannen – die Inventarnummern erscheinen automatisch mit
   Zeitstempel in der Liste.
3. Ist ein Barcode nicht lesbar, über den Button **„Nummer eingeben"** die
   Inventarnummer von Hand erfassen.
4. Bei Bedarf Bemerkungen ergänzen und Fehlscans löschen.
5. Über das Teilen-Symbol die CSV exportieren.

## Architektur-Hinweis

Das Inateck BLE-SDK (`inateck-scanner-ble-2.0.0`) besitzt **keinen öffentlichen
Echtzeit-Callback für gescannte Barcodes** – interne Notify-Daten werden verworfen,
wenn gerade kein Konfigurationsbefehl läuft. Das SDK dient daher nur zum Verbinden,
Abfragen und Konfigurieren. Der eigentliche Scan-Empfang läuft über den
**HID-Tastaturmodus**. Deshalb der Hybrid-Ansatz.

Die nativen SDK-Bibliotheken (`libscanner_cmd.so`, JNA) liegen nur für
**arm64-v8a** vor (`abiFilters += "arm64-v8a"`). Der HID-/CSV-/Inventur-Kern
läuft dennoch auf jedem Gerät; nur die nativen Konfigurationsbefehle brauchen
arm64.

## Technik

- Kotlin, Jetpack Compose, Material 3
- minSdk 24, targetSdk 36
- SDK-Jars unter `app/libs/`, native Libs unter `app/src/main/jniLibs/arm64-v8a/`
- Abhängigkeiten: Inateck BLE-SDK, FastBle, Gson

## Build

```bash
./gradlew :app:assembleDebug
```

Benötigt ein vollständiges JDK 17+ (in Android Studio wird automatisch das
gebündelte JBR genutzt). Die fertige APK liegt danach unter
`app/build/outputs/apk/debug/app-debug.apk`.
