# Inventur

Android-App zur Inventur mit dem Bluetooth-Barcodescanner **Inateck BCST-47**.
Gescannte Inventarnummern werden mit Zeitstempel erfasst und lassen sich als
CSV-Datei teilen.

## Funktionen

- **Scannen per HID-Tastaturmodus:** Der BCST-47 wird als Bluetooth-Tastatur
  gekoppelt und „tippt" jede Inventarnummer + Enter. Die App fängt das global ab
  (`MainActivity.dispatchKeyEvent`) und legt jeden Scan als eigene Position an.
- **Ein Eintrag pro Scan** mit sekundengenauem Datum/Uhrzeit – keine Mengen,
  Inventarnummern sind eindeutig.
- **Duplikat-Erkennung:** versehentlich doppelt gescannte Nummern werden rot
  hervorgehoben.
- **Bemerkungen** je Scan – per Long-Press oder Bearbeiten-Button.
- **Löschen mit Bestätigung** (einzeln und ganze Liste).
- **CSV-Export & Teilen** (`;`-getrennt, UTF-8 mit BOM für Excel; Spalten:
  Nr, Inventarnummer, Zeitpunkt, Bemerkung).
- **SDK-Screen** zum Verbinden/Konfigurieren des Scanners (Akku, Version,
  Umschalten auf HID + Enter, Lautstärke) über das
  [Inateck Scanner SDK](https://github.com/Inateck-Technology-Inc/android_sdk).

## Architektur-Hinweis

Das Inateck BLE-SDK (`inateck-scanner-ble-2.0.0`) besitzt **keinen öffentlichen
Echtzeit-Callback für gescannte Barcodes** – interne Notify-Daten werden verworfen,
wenn kein Konfigurationsbefehl läuft. Das SDK dient daher nur zum Verbinden,
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
gebündelte JBR genutzt).

## Bedienung

1. BCST-47 im HID-Tastaturmodus mit dem Gerät koppeln (bei Bedarf über den
   SDK-Screen der App auf „HID + Enter" umstellen).
2. App öffnen und scannen – die Inventarnummern erscheinen automatisch.
3. Über das Teilen-Symbol die CSV exportieren.
