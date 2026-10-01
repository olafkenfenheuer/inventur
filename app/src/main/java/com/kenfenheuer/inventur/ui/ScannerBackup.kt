package com.kenfenheuer.inventur.ui

import android.content.Context
import java.io.File

/**
 * Sicherung und Wiederherstellung der Scanner-Einstellungen (z. B. nach einem Werksreset). Die Sicherung ist eine Textdatei im
 * App-Verzeichnis mit einer Zeile je Einstellung: `name=wert (area N)`.
 */
object ScannerBackup {
    /** Nicht zurueckspielen: Verbindungsmodus, Funk-/Update- und Werkseinstellungen (geraetespezifisch bzw. gefaehrlich). */
    val SKIP = setOf(
        "bt_mode_low", "bt_mode_high", "wireless_mode", "factory_aging_mode", "factory_test_mode",
        "scan_head_upgrade_status", "serial_upgrade_scan_head_on",
    )

    private val LINE = Regex("""^(\w+)=(\S*) \(area (\d+)\)$""")

    fun file(context: Context) = File(context.filesDir, "scanner-einstellungen.txt")

    fun save(context: Context, rows: List<SettingRow>): Int {
        val text = rows.sortedBy { it.name }.joinToString("\n") { "${it.name}=${it.value} (area ${it.area})" } + "\n"
        file(context).writeText(text)
        return rows.size
    }

    fun load(context: Context): List<SettingRow>? {
        val f = file(context)
        if (!f.exists()) return null
        return f.readLines().mapNotNull { l -> LINE.matchEntire(l.trim())?.let { SettingRow(it.groupValues[3], it.groupValues[1], it.groupValues[2]) } }
    }

    /** Einstellungen aus der Sicherung, die vom aktuellen Stand abweichen (Fläche/Area immer vom aktuellen Geraet). */
    fun differences(saved: List<SettingRow>, current: List<SettingRow>): List<SettingRow> {
        val now = current.associateBy { it.name }
        return saved.filter { it.name !in SKIP }.mapNotNull { s ->
            val c = now[s.name] ?: return@mapNotNull null
            if (c.value != s.value) SettingRow(c.area, s.name, s.value) else null
        }
    }
}
