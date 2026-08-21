package com.kenfenheuer.inventur.ui

/** Eine Einstellung, wie sie der Scanner per getSettingInfo liefert/setSettingInfo erwartet. */
data class SettingRow(val area: String, val name: String, val value: String)

/** Kategorie einer Einstellung – Gliederung und Beschriftungen wie in der Inateck-Office-App. */
enum class SettingCategory(val title: String) {
    ALLGEMEIN("Allgemein"),
    SCAN_MODUS("Scan-Modus"),
    BARCODE_TYP("Barcode-Typ"),
    DATENVERARBEITUNG("Datenverarbeitung"),
    CODIERUNG("Codierungseinstellungen"),
    CACHE("Cache-Verwaltung"),
    WEITERE("Weitere Einstellungen"),
}

/** Eine Barcode-Symbologie mit ihrem Ein/Aus-Schalter und zugehoerigen Detaileinstellungen. */
data class SymbologyDef(val displayName: String, val onSetting: String, val extraSettings: List<String> = emptyList())

/**
 * Barcode-Typen wie in der Inateck-Office-App ("Barcode-Typ"-Seite). Namen und Gruppierung
 * anhand der realen Einstellungsnamen des BCST-47 (per getSettingInfo ausgelesen).
 */
val symbologies: List<SymbologyDef> = listOf(
    SymbologyDef("Code 128", "code128_on", listOf("symb_128_on", "gs1_128")),
    SymbologyDef("Code 93", "code93_on"),
    SymbologyDef(
        "Code 39", "code39_on",
        listOf("code39_all_asci_on", "code39_start_stop_on", "code39_check_status", "code39_to_code32", "code32_output_a"),
    ),
    SymbologyDef("Code 11", "code11_on", listOf("code11_send_check", "code11_checksum_on")),
    SymbologyDef("Codabar", "codabar_on", listOf("codabar_start_stop_on")),
    SymbologyDef("Standard 2 of 5", "standard25_on", listOf("standard25_send_check", "standard25_checksum_on")),
    SymbologyDef("Interleaved 2 of 5", "interleaved25_on"),
    SymbologyDef("Matrix 2 of 5", "matrix25_on", listOf("matrix25_send_check", "matrix25_checksum_on")),
    SymbologyDef("IATA 2 of 5", "iata25_on", listOf("iata25_send_check", "iata25_checksum_on")),
    SymbologyDef("China Post", "chinese_post_on", listOf("chnpt_checksum_on", "chnpt_send_check")),
    SymbologyDef("MSI", "msi_on", listOf("msi_send_check", "msi_checksum_on")),
    SymbologyDef("UPC-A", "upc_a_on", listOf("upc_a_output", "upc_a_send_check", "upc_a_out_sn")),
    SymbologyDef("UPC-E0", "upc_e0_on", listOf("upc_e_output", "upc_e_send_check")),
    SymbologyDef("UPC-E1", "upc_e1_on"),
    SymbologyDef("EAN-8", "ean_8_on", listOf("ean_8_send_check", "ean_8_extend_ean_13")),
    SymbologyDef("EAN-13", "ean_13_on", listOf("ean_13_send_check", "ean_13_to_isbn", "ean_13_to_issn")),
    SymbologyDef("GS1 DataBar (RSS-14)", "rss_14_on", listOf("rss14_composite_on")),
    SymbologyDef("Aztec", "aztec_on"),
    SymbologyDef("MaxiCode", "maxicode_on"),
    SymbologyDef("QR-Code", "qrcode_on", listOf("qrcode_read_phase", "qrcode_read_more_code")),
    SymbologyDef("Han Xin Code", "hanxin_on", listOf("hanxin_read_phase")),
    SymbologyDef("Data Matrix", "datamatrix_on", listOf("datamatrix_read_phase", "datamatrix_read_multi")),
    SymbologyDef("PDF417", "pdf417_on", listOf("pdf417_read_phase", "pdf417_read_more_code")),
    SymbologyDef("Telepen", "telepen_on", listOf("telepen_check")),
    SymbologyDef("Plessey", "plessey_on", listOf("plessey_check")),
)

/** Alle von {@link symbologies} referenzierten Einstellungsnamen (Haupt- und Zusatzschalter). */
val symbologySettingNames: Set<String> =
    symbologies.flatMap { listOf(it.onSetting) + it.extraSettings }.toHashSet()

/** Deutsche Labels fuer Einstellungen ausserhalb der Barcode-Typen-Seite. */
val settingLabels: Map<String, String> = mapOf(
    // Allgemein
    "volume" to "Lautstaerke",
    "shake_reminder" to "Vibration",
    "shake_intensity" to "Vibrationsstaerke",
    "lighting_lamp_control" to "Beleuchtung",
    "positioning_lamp_control" to "Navigationslicht",
    "keyboard_type" to "Tastatursprache",
    "country_board" to "Tastatursprache",
    "auto_off" to "Auto-Ruhezustand",
    "auto_close_mode" to "Auto-Abschaltung (Geraet)",
    "data_transmission_speed" to "Uebertragungsgeschwindigkeit",
    "read_inverse_color" to "Erkennung von inversen Barcodes",
    // Scan-Modus
    "scan_mode" to "Scan-Modus",
    "time_continuous_mode" to "Intervallzeit fuer kontinuierliches Scannen",
    "time_auto_off" to "Automatische Abschaltzeit des roten Lichts",
    "auto_sense_distan" to "Sensor-Entfernung (Auto-Erkennungsmodus)",
    // Datenverarbeitung
    "suffix_add_enter" to "Suffix: Enter",
    "subfix_add_tab" to "Suffix: Tab",
    "letter_case" to "Gross-/Kleinschreibung",
    "output_time" to "Uhrzeit ausgeben",
    "output_date" to "Datum ausgeben",
    "custom_prefix_on" to "Benutzerdefiniertes Praefix",
    "custom_suffix_on" to "Benutzerdefiniertes Suffix",
    "delete_character" to "Zeichen loeschen (Anzahl)",
    "hide_front_byte" to "Zeichen am Anfang ausblenden",
    "hide_back_byte" to "Zeichen am Ende ausblenden",
    "barcode_id_on" to "Code ID",
    "aim_id_on" to "AIM ID",
    "vehicle_id_on" to "Fahrzeug-ID",
    "capslock" to "Einfluss von Caps Lock",
    "hide_special_start_symbol" to "Spezielle Startzeichen ausblenden",
    "ean_5" to "Zusatzcode EAN-2",
    "ean_2" to "Zusatzcode EAN-5",
    "gsreplace_char_on" to "GS-Zeichen ersetzen",
    "usps_fedex" to "USPS/FedEx-Sondercodes",
    // Codierungseinstellungen
    "output_char_type" to "Zeichensatz",
    "iso_8859_on" to "ISO-8859-Kodierung",
    "diacritic_on" to "Diakritische Zeichen",
    // Cache-Verwaltung
    "start_up_clean_cache" to "Cache beim Start loeschen",
    "auto_upload_cache" to "Automatischer Upload des Caches",
    "dclick_upload_flag" to "Doppelklick-Upload",
    "inventory_mode" to "Inventurmodus",
    "repeat_data_check_on" to "Ueberpruefung auf wiederholte Barcode-Scans",
    // Verbindung (Bluetooth-Modus, eigener Button "HID + Enter" fuer den Wechsel)
    "bt_mode_low" to "Bluetooth-Modus (low)",
    "bt_mode_high" to "Bluetooth-Modus (high)",
    // Barcode-Detail-Unterpunkte
    "code39_all_asci_on" to "Alle ASCII-Zeichen",
    "code39_start_stop_on" to "START/STOP-Zeichen uebertragen",
    "code39_check_status" to "Pruefziffer verlangen",
    "code39_to_code32" to "In Code 32 umwandeln",
    "code32_output_a" to "Code 32 mit Praefix \"A\"",
    "codabar_start_stop_on" to "START/STOP-Zeichen uebertragen",
    "ean_8_extend_ean_13" to "Als EAN-13 ausgeben",
    "ean_13_to_isbn" to "In ISBN umwandeln",
    "ean_13_to_issn" to "In ISSN umwandeln",
    "rss14_composite_on" to "RSS-14 Composite",
    "gs1_128" to "GS1-128 (UCC/EAN-128)",
)

/** Einstellungen, deren Name auf "_send_check" endet: Pruefziffer wird mit uebertragen. */
private val sendCheckSuffix = "_send_check"

/** Einstellungen, deren Name auf "_checksum_on" endet: Pruefziffer wird beim Lesen verlangt. */
private val checksumOnSuffix = "_checksum_on"

fun labelFor(name: String): String {
    settingLabels[name]?.let { return it }
    if (name.endsWith(sendCheckSuffix)) return "Pruefziffer uebertragen"
    if (name.endsWith(checksumOnSuffix)) return "Pruefziffer verlangen"
    return name.removeSuffix("_on").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Wertebedeutungen laut offizieller SDK-Doku bzw. Abgleich mit der Inateck-Office-App. */
private val keyboardLayouts: List<Pair<String, String>> = run {
    val base = listOf(
        1 to "US", 2 to "Italienisch", 3 to "Deutsch", 4 to "Spanisch",
        5 to "Franzoesisch", 6 to "GB", 7 to "Japanisch", 8 to "Kanadisch",
        9 to "Litauisch", 10 to "Serbisch", 11 to "Schwedisch", 12 to "Niederlaendisch",
        13 to "Daenisch", 14 to "Norwegisch", 16 to "Portugiesisch", 17 to "Polnisch",
    )
    base.map { (v, l) -> v.toString() to "$l (Windows)" } +
        base.map { (v, l) -> (v + 32).toString() to "$l (Mac)" }
}

/**
 * Auswahllisten fuer Einstellungen mit festem Wertebereich. "scan_mode" ist anhand der
 * Optionen in der Inateck-Office-App verifiziert (Tastmodus = aktueller Scanner-Wert 2).
 */
val settingOptions: Map<String, List<Pair<String, String>>> = mapOf(
    "volume" to listOf("0" to "Stumm", "2" to "Leise", "4" to "Mittel", "8" to "Laut"),
    "scan_mode" to listOf(
        "2" to "Tastmodus",
        "3" to "Automatischer Erkennungsmodus",
        "1" to "Kontinuierlicher Scan-Modus",
    ),
    "letter_case" to listOf(
        "0" to "Keine Umwandlung",
        "1" to "Kleinbuchstaben",
        "2" to "Grossbuchstaben",
    ),
    "lighting_lamp_control" to listOf("0" to "Beim Lesen", "1" to "Immer an", "2" to "Immer aus"),
    "positioning_lamp_control" to listOf("0" to "Beim Lesen", "1" to "Immer an", "2" to "Immer aus"),
    "shake_intensity" to listOf("0" to "Aus", "1" to "Schwach", "3" to "Stark"),
    "keyboard_type" to keyboardLayouts,
    "country_board" to keyboardLayouts,
)

/** 0/1-Werte, die als Schalter dargestellt werden koennen. */
fun isBooleanSetting(row: SettingRow): Boolean = row.value == "0" || row.value == "1"

/** Einstellungsnamen, die auf der Allgemein-Seite erscheinen (Reihenfolge wie Inateck-App). */
val allgemeinOrder = listOf(
    "volume", "shake_reminder", "shake_intensity", "lighting_lamp_control",
    "positioning_lamp_control", "keyboard_type", "auto_off", "auto_close_mode",
    "data_transmission_speed", "read_inverse_color",
)

val scanModusOrder = listOf("scan_mode", "time_continuous_mode", "time_auto_off", "auto_sense_distan")

val datenverarbeitungOrder = listOf(
    "suffix_add_enter", "subfix_add_tab", "letter_case", "output_time", "output_date",
    "custom_prefix_on", "custom_suffix_on", "delete_character", "hide_front_byte", "hide_back_byte",
    "barcode_id_on", "aim_id_on", "vehicle_id_on", "capslock", "hide_special_start_symbol",
    "ean_5", "ean_2", "gsreplace_char_on", "usps_fedex",
)

val codierungOrder = listOf("output_char_type", "iso_8859_on", "diacritic_on")

val cacheOrder = listOf(
    "start_up_clean_cache", "auto_upload_cache", "dclick_upload_flag", "inventory_mode", "repeat_data_check_on",
)

/** Alle Einstellungsnamen, die einer festen Kategorie zugeordnet sind (Rest -> "Weitere"). */
val categorizedNames: Set<String> =
    (allgemeinOrder + scanModusOrder + datenverarbeitungOrder + codierungOrder + cacheOrder + symbologySettingNames)
        .toHashSet() + setOf("country_board")
