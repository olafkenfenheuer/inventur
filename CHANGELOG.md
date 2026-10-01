# Changelog

Alle nennenswerten Änderungen an der App **Inventur**.
Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
Versionierung nach `versionName (versionCode)`.

## [Unveröffentlicht]

### Behoben
- Scanner-Karte: Die Knöpfe brechen auf schmalen Bildschirmen (Handy) um, statt zusammengequetscht zu werden.

### Dokumentation
- README und Screenshots (Handy und Tablet) auf den aktuellen Stand gebracht.

## [2.5 (19)] – 2026-10-01

### Hinzugefügt
- **Scanner zurücksetzen (Werkseinstellungen):** Knopf „Zurücksetzen …" in der Scanner-Karte und im Hinweis „Kein Scanner verbunden" mit vier Barcodes in
  getesteter Reihenfolge (Bluetooth-Verbindung zurücksetzen, Einstellungen aufrufen, Werkseinstellungen wiederherstellen, Beenden und speichern).
  Läuft der Hintergrunddienst oder ist ein Scanner verbunden, fragt die App vorher und stoppt den Dienst.
- **Einstellungen sichern / Sicherung einspielen** in der Konfiguration (z. B. nach einem Werksreset); Verbindungsmodus, Funk-, Update- und
  Werkseinstellungen werden nicht zurückgespielt.
- Nach „Auf Expertenmodus umstellen" und Neustart sucht die App den Scanner selbst unter seiner neuen Adresse und verbindet ihn (in etwa 7 Sekunden).

### Geändert
- Scannereinstellungen: kein roter Hinweis mehr im Expertenmodus; neutraler Hinweis „Kein Scanner verbunden", wenn weder Tastatur noch Scanner verbunden sind.
  Button „Modus-Barcodes" entfernt.
- Veraltete Scanner-Adressen (nach einem Moduswechsel meldet sich der Scanner unter einer neuen Adresse) werden verworfen; hängende Verbindungen werden neu aufgebaut.
- Konfiguration: Anzeige bleibt beim Neu-Laden stehen, bis zu drei Leseversuche.

### Behoben
- Das Symbol des Hintergrunddienstes fehlte: Die App fragt die Benachrichtigungs-Berechtigung (Android 13+) jetzt bei jedem Einschalten des Hintergrund-Empfangs ab.
- Setup-Barcodes des Scanners (`/*…*/`) landen nicht mehr als Scan in der Liste.

## [2.4 (18)] – 2026-10-01

### Hinzugefügt
- **Bildschirm anlassen:** Solange die App offen ist, bleibt der Bildschirm an (Menü → „Bildschirm anlassen", Standard: an).
- **Scanner-Cache (Inventurmodus) per USB-Kabel hochladen:** Beim Anstecken des Scanners per USB fragt ein Popup, ob der Cache hochgeladen werden soll;
  danach optional „Cache leeren". Schalter „Scannen per Cache" sowie Anzeige/„Cache leeren" in den Scannereinstellungen.
- **Scans im Hintergrund empfangen (Expertenmodus):** Der Scanner sendet im Expertenmodus Barcodes als Bluetooth-Nachricht; ein Dienst im Vordergrund
  hält die Verbindung auch bei ausgeschaltetem Bildschirm. Geführte Umstellung auf den Expertenmodus und Schalter „Expertenmodus" in den
  Scannereinstellungen; hängende Verbindungen werden neu aufgebaut.
- Scannereinstellungen verbinden sich beim Öffnen automatisch mit dem Scanner und zeigen denselben Scanner nur einmal.

### Geändert
- Der Button „HID + Enter" ist entfernt (er stellte entgegen der Beschriftung auf den Expertenmodus).
- Das Projekt wird nun aus dem gemeinsamen Kern der Variante „classic" gebaut (siehe `scripts/export-classic.sh` im Pro-Projekt); JNA kommt als
  16-KB-kompatible Bibliothek aus Gradle statt aus `app/libs`.

### Behoben
- Absturz in den Scannereinstellungen („Key … was already used"), wenn derselbe Scanner doppelt in der Geräteliste stand.
## [2.3.1 (17)] – 2026-08-25

### Behoben
- 16-KB-Speicherseiten-Kompatibilität: `libjnidispatch.so` (natives JNA-Modul,
  das das Inateck-SDK zur Scanner-Konfiguration nutzt) wurde mit einer alten,
  vor Android 15 gebauten JNA-Version (5.14.0) ausgeliefert und konnte auf
  16-KB-Seiten-Geräten abstürzen. JNA wird jetzt als reguläre Gradle-
  Abhängigkeit (`net.java.dev.jna:jna:5.19.1@aar`) statt als manuell
  eingebundene Jars/`.so`-Datei eingebunden – die Version enthält den
  offiziellen Fix (JNA-Issues #1618/#1647).

## [2.3 (16)] – 2026-08-25

### Hinzugefügt
- Scanner-Konfiguration ist jetzt auch erreichbar, wenn der BCST-47 nur im
  HID-Tastaturmodus gekoppelt ist – ein Tipp auf „Konfiguration" verbindet
  automatisch per Bluetooth-LE im Hintergrund, ohne den Tastaturmodus zu
  unterbrechen. Ein vorheriger Moduswechsel ist dafür nicht mehr nötig.
- Konfigurationsseite neu gegliedert: Kategorien-Navigation (Scan-Modus,
  Barcode-Typ, Datenverarbeitung, Codierungseinstellungen, Cache-Verwaltung)
  statt einer langen Flachliste, mit Detailseiten je Barcode-Typ.
- Hörbarer Bestätigungston des Scanners bei jeder geänderten Einstellung.

### Geändert
- Menüpunkt „Scanner verbinden" heißt jetzt „Scannereinstellungen"; das
  Options-Menü zeigt dafür ein Zahnrad-Icon statt der drei Punkte.
- Scanner-Erkennung prüft zusätzlich Vendor-/Product-ID (BCST-47), damit
  eine zufällig gleichzeitig verbundene andere Bluetooth-Tastatur nicht
  fälschlich als Scanner erkannt wird.
- Bedienungsanleitung auf den neuen Scanner-Workflow aktualisiert.
- Version auf 2.3 (versionCode 16) angehoben.

## [2.2.1 (15)] – 2026-07-24

### Geändert
- Version auf 2.2.1 (versionCode 15) angehoben für einen Upload in den
  internen Testkanal im Play Store. Keine funktionalen Änderungen gegenüber
  2.2.

## [2.2 (14)] – 2026-07-24

### Hinzugefügt
- Kamera-Scan zeigt den Inhalt des zuletzt erfassten Barcodes groß und in
  Monospace-Schrift an – so lässt sich direkt prüfen, was tatsächlich gescannt
  wurde.

### Behoben
- Kamera-Scan: Ein Drehen des Geräts löste eine Activity-Neuerstellung aus,
  wodurch kurz der Startbildschirm erschien und das Sucherbild neu aufgebaut
  wurde. Die Orientierung ist während des Kamera-Scans jetzt auf Hochformat
  fixiert.
- Startbildschirm erschien nach Konfigurationswechseln (z. B. Drehung) erneut;
  der Splash-Zustand übersteht die Activity-Neuerstellung nun (`rememberSaveable`).

### Geändert
- Version auf 2.2 (versionCode 14) angehoben.

## [2.1.1 (13)] – 2026-07-22

### Geändert
- Kopfzeile überarbeitet: Der App-Name entfällt, der Platz gehört der
  Geräte-/Benutzerkennung und den Aktions-Icons. „Scanner verbinden" ist ins
  ⋮-Menü umgezogen; lange Kennungen werden auf Handys begrenzt dargestellt.
- Version auf 2.1.1 (versionCode 13) angehoben.

### Behoben
- Hochformat: Der Hinweis „Keine Geräte-/Benutzerkennung" verdrängte Titel und
  Export-Symbole aus der Kopfzeile.

### Dokumentation
- Bedienungsanleitung durchgängig mit Telefon-Screenshots (Hochformat);
  private Inhalte in Export-Dialogen verpixelt.
- Zweites Demo-Video vom Telefon (Hochformat) unter `docs/demo/`.

## [2.1 (12)] – 2026-07-22

### Hinzugefügt
- Geräte-/Benutzerkennung: In der Kopfzeile wird eine frei wählbare Kennung
  (z. B. „Tablet-1" oder ein Name) gesetzt und angezeigt; jeder neue Scan
  speichert sie zum Erfassungszeitpunkt. Im CSV-Export erscheint sie als neue
  Spalte „Erfasst von" – bei Inventuren mit mehreren Geräten/Personen bleibt
  jede Zeile zuordenbar. Ohne Kennung erinnert ein roter Hinweis in der Kopfzeile.
- Scanner-Screen: Live-Statusbanner zeigt, ob der Scanner als HID-Tastatur
  gekoppelt ist (grün/rot), mit Direktlink zu den Bluetooth-Einstellungen.
- Modus-Barcodes: Die offiziellen Umschalt-Codes des BCST-47 (GATT-/HID-Modus)
  werden als QR-Codes direkt auf dem Display angezeigt – kein Papierhandbuch
  mehr nötig; auf Tablets 2-spaltig.
- Scanner-Konfiguration: Alle Einstellungen des BCST-47 werden per SDK gelesen
  und mit Klartext-Auswahllisten bearbeitet (Lautstärke, Vibration, Scan-Modus,
  Tastatur-Layout, Beleuchtung, Auto-Abschaltzeit, Barcode-Typen u. v. m.).
- Kopfzeile: Handscanner-Symbol mit Zahnrad (Material „barcode_reader") für den
  Scanner-Screen.

### Behoben
- Absturz im Scanner-Screen, sobald die Gerätesuche einen Scanner fand:
  activity-compose 1.13.0 zog Compose 1.9.2 in den Runtime-Klassenpfad, während
  gegen BOM 2024.09.00 (Compose 1.7.0) kompiliert wurde – die binär inkompatible
  FlowRow-Signatur führte zu `NoSuchMethodError`. activity-compose auf 1.10.1
  gepinnt, BOM auch für debugImplementation deklariert.
- Verbindungsstatus der Gerätekarte wird jetzt konsistent gelesen und regelmäßig
  aktualisiert (das SDK ändert ihn außerhalb von Compose) – kein eingefrorener
  „Verbinden"-Button mehr bei bestehender Verbindung.

### Geändert
- Version auf 2.1 (versionCode 12) angehoben.

## [2.0 (11)] – 2026-07-21

### Geändert
- Kamera-Scan: Derselbe Barcode wird nun 5 Sekunden lang nicht erneut übernommen
  (Entprellung von 2 s auf 5 s erhöht), um versehentliche Doppelerfassungen eines
  im Bild verbleibenden Codes zu vermeiden.
- Version auf 2.0 (versionCode 11) angehoben.

## [1.9 (10)] – 2026-07-19

### Behoben
- Randlose Darstellung (edge-to-edge): Das Theme setzt die System-Status- und
  -Navigationsleiste transparent, statt die opaken Farben des Framework-Themes
  `android:Theme.Material.Light.NoActionBar` zu erben. Behebt die Play-Console-
  Warnung „Randlose Anzeige funktioniert möglicherweise nicht für alle Nutzer".

### Geändert
- Kamera-Scan nutzt kontinuierlichen Autofokus (`continuousFocusEnabled`) statt
  ZXings periodischem Einzel-Autofokus – schnelleres, zuverlässigeres Erfassen.
- Version auf 1.9 (versionCode 10) angehoben.

## [1.8 (9)] – 2026-07-19

### Hinzugefügt
- CSV-Export an einen frei wählbaren Speicherort über das Storage Access
  Framework – u.a. direkt auf einen per USB-OTG angeschlossenen USB-Stick,
  ohne zusätzliche Speicherberechtigung. Neuer Toolbar-Button „Als CSV
  speichern"; der bestehende Teilen-Weg bleibt erhalten.

### Geändert
- Version auf 1.8 (versionCode 9) angehoben.

## [1.7 (8)] – 2026-07-19

### Behoben
- 16-KB-Speicherseiten-Kompatibilität: die native Inateck-Bibliothek
  `libscanner_cmd.so` wurde verlustfrei auf 16-KB-Segmentgrenzen ausgerichtet
  (`p_align` 0x4000). Auf einem 16-KB-Emulator (Android 16) verifiziert.

### Geändert
- Responsives Layout je nach Bildschirmbreite: einspaltige Liste (Handy
  hochkant), zwei Panele mit Liste und Seitenleiste (Handy quer / Tablet
  hochkant) und dreispaltiges Raster (Tablet quer).
- Version auf 1.7 (versionCode 8) angehoben.

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
