package com.kenfenheuer.inventur.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inateck.scanner.ble.BleScannerDevice
import com.kenfenheuer.inventur.scanner.ScannerManager

/** Eine Einstellung, wie sie der Scanner per getSettingInfo liefert. */
private data class SettingRow(val area: String, val name: String, val value: String)

/** Deutsche Labels fuer die wichtigsten Einstellungen; Rest wird aus dem Namen abgeleitet. */
private val settingLabels = mapOf(
    "volume" to "Lautstaerke (0=aus)",
    "shake_reminder" to "Vibration",
    "shake_intensity" to "Vibrationsstaerke",
    "scan_mode" to "Scan-Modus",
    "time_auto_off" to "Auto-Abschaltzeit",
    "time_continuous_mode" to "Timeout Dauer-Scan",
    "auto_close_mode" to "Auto-Abschaltung",
    "suffix_add_enter" to "Suffix: Enter",
    "subfix_add_tab" to "Suffix: Tab",
    "letter_case" to "Gross-/Kleinschreibung",
    "keyboard_type" to "Tastatur-Layout",
    "country_board" to "Laender-Layout",
    "data_transmission_speed" to "Uebertragungsgeschwindigkeit",
    "bt_mode_low" to "Bluetooth-Modus (low)",
    "bt_mode_high" to "Bluetooth-Modus (high)",
)

/** Reihenfolge der kuratierten Einstellungen im Abschnitt "Allgemein". */
private val curatedOrder = listOf(
    "volume", "shake_reminder", "shake_intensity", "scan_mode",
    "time_auto_off", "time_continuous_mode", "auto_close_mode",
    "suffix_add_enter", "subfix_add_tab", "letter_case",
    "keyboard_type", "country_board", "data_transmission_speed",
)

/** Einstellungen, deren Aenderung den Verbindungsmodus wechselt (Scanner trennt danach). */
private val modeSwitchingNames = setOf("bt_mode_low", "bt_mode_high")

/** Tastatur-Layouts laut offizieller SDK-Doku (Werte 1-17 Windows, +32 = Mac). */
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
 * Wertebedeutungen laut offizieller SDK-Doku (docs.inateck.com, General Configuration
 * List). Einstellungen mit Eintrag hier werden als Auswahlliste angezeigt; passt der
 * aktuelle Wert nicht zur Liste, faellt die Zeile auf den Rohwert-Editor zurueck.
 */
private val settingOptions: Map<String, List<Pair<String, String>>> = mapOf(
    "volume" to listOf("0" to "Stumm", "2" to "Leise", "4" to "Mittel", "8" to "Laut"),
    "scan_mode" to listOf(
        "1" to "Dauer-Scan",
        "2" to "Auto-Licht-aus (Standard)",
        "3" to "Auto-Sensor",
        "5" to "Freihand",
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

private fun labelFor(name: String): String =
    settingLabels[name] ?: name.removeSuffix("_on").replace('_', ' ')

private fun isSymbology(name: String): Boolean = name.endsWith("_on")

/** 0/1-Werte als Schalter darstellen (Barcode-Typen und bekannte Bool-Einstellungen). */
private fun isBooleanSetting(row: SettingRow): Boolean =
    row.value == "0" || row.value == "1"

/**
 * Konfigurationsansicht fuer einen verbundenen Scanner: liest alle Einstellungen
 * per SDK und schreibt Aenderungen einzeln zurueck. Aufgeteilt in "Allgemein"
 * (kuratiert), "Barcode-Typen" (…_on-Schalter) und "Weitere" (Rest, roh).
 */
@Composable
fun ScannerConfigPanel(
    device: BleScannerDevice,
    scanner: ScannerManager,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<SettingRow>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(device.mac, reloadKey) {
        loading = true
        error = null
        scanner.getSettings(device) { result ->
            loading = false
            result.fold(
                onSuccess = { list ->
                    rows = list.mapNotNull { map ->
                        val area = map["area"] ?: return@mapNotNull null
                        val name = map["name"] ?: return@mapNotNull null
                        SettingRow(area, name, map["value"] ?: "")
                    }
                },
                onFailure = { error = "Einstellungen konnten nicht gelesen werden." },
            )
        }
    }

    fun writeSetting(row: SettingRow, newValue: String) {
        scanner.setSetting(device, row.area, row.name, newValue) { result ->
            if (result.isSuccess) {
                rows = rows?.map { if (it.area == row.area && it.name == row.name) it.copy(value = newValue) else it }
                if (row.name in modeSwitchingNames) {
                    Toast.makeText(
                        context,
                        "Modus umgestellt – Scanner trennt die Verbindung und koppelt ggf. neu",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } else {
                Toast.makeText(context, "Schreiben fehlgeschlagen: ${labelFor(row.name)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
            }
            Text(
                text = "Konfiguration: ${device.name ?: device.mac ?: "Scanner"}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Aenderungen werden sofort an den Scanner gesendet. Werte ohne Schalter " +
                "sind Rohwerte des Scanners (Bedeutung siehe BCST-47-Handbuch).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))

        when {
            loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Einstellungen werden gelesen …")
            }
            error != null -> Column {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { reloadKey++ }) { Text("Erneut versuchen") }
            }
            else -> SettingsList(
                rows = rows.orEmpty(),
                onWrite = ::writeSetting,
                onReload = { reloadKey++ },
            )
        }
    }
}

@Composable
private fun SettingsList(
    rows: List<SettingRow>,
    onWrite: (SettingRow, String) -> Unit,
    onReload: () -> Unit,
) {
    val byName = rows.associateBy { it.name }
    val curated = curatedOrder.mapNotNull { byName[it] }
    val symbologies = rows.filter { isSymbology(it.name) }.sortedBy { it.name }
    val curatedNames = curated.map { it.name }.toSet()
    val others = rows
        .filter { it.name !in curatedNames && !isSymbology(it.name) }
        .sortedBy { it.name }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { SectionHeader("Allgemein") }
        items(curated, key = { "c:" + it.name }) { SettingRowItem(it, onWrite) }

        item { SectionHeader("Barcode-Typen") }
        items(symbologies, key = { "s:" + it.name }) { SettingRowItem(it, onWrite) }

        item { SectionHeader("Weitere (Experten)") }
        items(others, key = { "o:" + it.name }) { SettingRowItem(it, onWrite) }

        item {
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onReload) { Text("Neu laden") }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(Modifier.size(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider()
    }
}

@Composable
private fun SettingRowItem(row: SettingRow, onWrite: (SettingRow, String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(labelFor(row.name), style = MaterialTheme.typography.bodyMedium)
            Text(
                row.name,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val options = settingOptions[row.name]
        when {
            // Auswahlliste hat Vorrang (volume=0 etc. wuerde sonst als Schalter erscheinen).
            options != null && options.any { it.first == row.value } ->
                EnumDropdown(row, options, onWrite)
            isBooleanSetting(row) -> Switch(
                checked = row.value == "1",
                onCheckedChange = { checked -> onWrite(row, if (checked) "1" else "0") },
            )
            else -> RawValueEditor(row, onWrite)
        }
    }
}

@Composable
private fun EnumDropdown(
    row: SettingRow,
    options: List<Pair<String, String>>,
    onWrite: (SettingRow, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(options.first { it.first == row.value }.second)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (value != row.value) onWrite(row, value)
                    },
                )
            }
        }
    }
}

@Composable
private fun RawValueEditor(row: SettingRow, onWrite: (SettingRow, String) -> Unit) {
    // key = row.value, damit der Editor nach erfolgreichem Schreiben den neuen Stand zeigt.
    var text by remember(row.name, row.value) { mutableStateOf(row.value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.width(90.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
        )
        OutlinedButton(
            enabled = text != row.value,
            onClick = { onWrite(row, text) },
        ) { Text("Setzen") }
    }
}
