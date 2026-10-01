package com.kenfenheuer.inventur.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.DisposableEffect
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

/** Einstellungen, deren Aenderung den Verbindungsmodus wechselt (Scanner trennt danach). */
private val modeSwitchingNames = setOf("bt_mode_low", "bt_mode_high")

/** Navigationsebenen der Konfigurationsseite, nachgebildet nach der Inateck-Office-App. */
private sealed interface ConfigScreen {
    data object Root : ConfigScreen
    data object ScanModus : ConfigScreen
    data object BarcodeTyp : ConfigScreen
    data class BarcodeDetail(val symbology: SymbologyDef) : ConfigScreen
    data object Datenverarbeitung : ConfigScreen
    data object Codierung : ConfigScreen
    data object Cache : ConfigScreen
    data object Weitere : ConfigScreen
}

/**
 * Konfigurationsansicht fuer einen verbundenen Scanner: liest alle Einstellungen per SDK und
 * schreibt Aenderungen einzeln zurueck. Menuefuehrung und Beschriftungen orientieren sich an
 * der offiziellen Inateck-Office-App (Kategorien-Uebersicht -> Detailseiten).
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
    var screen by remember { mutableStateOf<ConfigScreen>(ConfigScreen.Root) }

    LaunchedEffect(device.mac, reloadKey) {
        // Nur beim ersten Laden den Ladeindikator zeigen; bei erneutem Laden bleibt die Anzeige stehen. Bis zu 3 Versuche,
        // weil der Scanner direkt nach Schreibvorgaengen kurz nicht antwortet.
        if (rows == null) loading = true
        error = null
        for (attempt in 1..3) {
            var done = false
            var ok = false
            scanner.getSettings(device) { result ->
                result.getOrNull()?.let { list ->
                    rows = list.mapNotNull { map ->
                        val area = map["area"] ?: return@mapNotNull null
                        val name = map["name"] ?: return@mapNotNull null
                        SettingRow(area, name, map["value"] ?: "")
                    }
                    ok = true
                }
                done = true
            }
            var waited = 0
            while (!done && waited < 6000) { kotlinx.coroutines.delay(100); waited += 100 }
            if (ok) break
            kotlinx.coroutines.delay(800)
        }
        loading = false
        if (rows == null) error = "Einstellungen konnten nicht gelesen werden."
    }

    fun writeSetting(row: SettingRow, newValue: String, playAck: Boolean = true) {
        scanner.setSetting(device, row.area, row.name, newValue) { result ->
            if (result.isSuccess) {
                rows = rows?.map { if (it.area == row.area && it.name == row.name) it.copy(value = newValue) else it }
                if (row.name in modeSwitchingNames) {
                    Toast.makeText(
                        context,
                        "Modus umgestellt – Scanner trennt die Verbindung und koppelt ggf. neu",
                        Toast.LENGTH_LONG,
                    ).show()
                } else if (playAck) {
                    scanner.playAckBeep(device)
                }
            } else {
                Toast.makeText(context, "Schreiben fehlgeschlagen: ${labelFor(row.name)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun backupSettings() {
        val list = rows ?: return
        val n = ScannerBackup.save(context, list)
        Toast.makeText(context, "$n Einstellungen gesichert", Toast.LENGTH_LONG).show()
    }

    fun restoreSettings() {
        val current = rows.orEmpty()
        val saved = ScannerBackup.load(context)
        if (saved == null) {
            Toast.makeText(context, "Keine Sicherung vorhanden – zuerst \"Einstellungen sichern\"", Toast.LENGTH_LONG).show()
            return
        }
        val todo = ScannerBackup.differences(saved, current)
        if (todo.isEmpty()) {
            Toast.makeText(context, "Keine Abweichungen zur Sicherung", Toast.LENGTH_LONG).show()
            return
        }
        fun next(i: Int, ok: Int) {
            if (i >= todo.size) {
                Toast.makeText(context, "$ok von ${todo.size} Einstellungen wiederhergestellt", Toast.LENGTH_LONG).show()
                scanner.playAckBeep(device)
                return
            }
            val r = todo[i]
            scanner.setSetting(device, r.area, r.name, r.value) { res ->
                // Wert sofort in der Anzeige uebernehmen, ohne den Scanner erneut abzufragen.
                if (res.isSuccess) rows = rows?.map { if (it.name == r.name) it.copy(value = r.value) else it }
                next(i + 1, ok + if (res.isSuccess) 1 else 0)
            }
        }
        next(0, 0)
    }

    fun writeBulk(names: List<String>, newValue: String) {
        names.forEach { name -> rows?.firstOrNull { it.name == name }?.let { writeSetting(it, newValue, playAck = false) } }
        scanner.playAckBeep(device)
    }

    val title = when (val s = screen) {
        ConfigScreen.Root -> scanner.displayName(device)
        ConfigScreen.ScanModus -> "Scan-Modus"
        ConfigScreen.BarcodeTyp -> "Barcode-Typ"
        is ConfigScreen.BarcodeDetail -> s.symbology.displayName
        ConfigScreen.Datenverarbeitung -> "Datenverarbeitung"
        ConfigScreen.Codierung -> "Codierungseinstellungen"
        ConfigScreen.Cache -> "Cache-Verwaltung"
        ConfigScreen.Weitere -> "Weitere Einstellungen"
    }
    val onBack = when (screen) {
        ConfigScreen.Root -> onClose
        is ConfigScreen.BarcodeDetail -> { { screen = ConfigScreen.BarcodeTyp } }
        else -> { { screen = ConfigScreen.Root } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.size(8.dp))

        when {
            loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Einstellungen werden gelesen …")
            }
            error != null -> Column(modifier = Modifier.padding(12.dp)) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { reloadKey++ }) { Text("Erneut versuchen") }
            }
            else -> {
                val byName = rows.orEmpty().associateBy { it.name }
                when (val s = screen) {
                    ConfigScreen.Root -> RootScreen(
                        byName = byName,
                        onWrite = ::writeSetting,
                        onNavigate = { screen = it },
                        onReload = { reloadKey++ },
                        onBackup = ::backupSettings,
                        onRestore = ::restoreSettings,
                    )
                    ConfigScreen.ScanModus -> CategoryScreen(scanModusOrder, byName, ::writeSetting)
                    ConfigScreen.BarcodeTyp -> BarcodeTypScreen(
                        byName = byName,
                        onWrite = ::writeSetting,
                        onOpenDetail = { screen = ConfigScreen.BarcodeDetail(it) },
                        onBulk = ::writeBulk,
                    )
                    is ConfigScreen.BarcodeDetail -> CategoryScreen(
                        listOf(s.symbology.onSetting) + s.symbology.extraSettings,
                        byName,
                        ::writeSetting,
                    )
                    ConfigScreen.Datenverarbeitung -> CategoryScreen(datenverarbeitungOrder, byName, ::writeSetting)
                    ConfigScreen.Codierung -> CategoryScreen(codierungOrder, byName, ::writeSetting)
                    ConfigScreen.Cache -> CategoryScreen(cacheOrder, byName, ::writeSetting)
                    ConfigScreen.Weitere -> {
                        val others = rows.orEmpty().filter { it.name !in categorizedNames }.sortedBy { it.name }
                        WeitereScreen(others, ::writeSetting)
                    }
                }
            }
        }
    }
}

@Composable
private fun RootScreen(
    byName: Map<String, SettingRow>,
    onWrite: (SettingRow, String) -> Unit,
    onNavigate: (ConfigScreen) -> Unit,
    onReload: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                NavRow("Scan-Modus", byName["scan_mode"]?.let { settingOptions["scan_mode"]?.firstOrNull { o -> o.first == it.value }?.second }) { onNavigate(ConfigScreen.ScanModus) }
                RowDivider()
                NavRow("Barcode-Typ", null) { onNavigate(ConfigScreen.BarcodeTyp) }
                RowDivider()
                NavRow("Datenverarbeitung", null) { onNavigate(ConfigScreen.Datenverarbeitung) }
                RowDivider()
                NavRow("Codierungseinstellungen", null) { onNavigate(ConfigScreen.Codierung) }
                RowDivider()
                NavRow("Cache-Verwaltung", null) { onNavigate(ConfigScreen.Cache) }
            }
        }
        item {
            SectionCard {
                allgemeinOrder.forEachIndexed { index, name ->
                    byName[name]?.let { row ->
                        SettingRowContent(row, onWrite)
                        if (index != allgemeinOrder.lastIndex) RowDivider()
                    }
                }
            }
        }
        item {
            SectionCard { NavRow("Weitere Einstellungen", null) { onNavigate(ConfigScreen.Weitere) } }
        }
        item {
            Spacer(Modifier.size(4.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReload) { Text("Neu laden") }
                OutlinedButton(onClick = onBackup) { Text("Einstellungen sichern") }
                OutlinedButton(onClick = onRestore) { Text("Sicherung einspielen") }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun CategoryScreen(
    names: List<String>,
    byName: Map<String, SettingRow>,
    onWrite: (SettingRow, String) -> Unit,
) {
    val present = names.mapNotNull { byName[it] }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                present.forEachIndexed { index, row ->
                    SettingRowContent(row, onWrite)
                    if (index != present.lastIndex) RowDivider()
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun BarcodeTypScreen(
    byName: Map<String, SettingRow>,
    onWrite: (SettingRow, String) -> Unit,
    onOpenDetail: (SymbologyDef) -> Unit,
    onBulk: (List<String>, String) -> Unit,
) {
    val allOnNames = symbologies.map { it.onSetting }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard {
                Button(
                    onClick = { onBulk(allOnNames, "1") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Alle aktivieren") }
                RowDivider()
                OutlinedButton(
                    onClick = { onBulk(allOnNames, "0") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Alle deaktivieren") }
            }
        }
        item {
            SectionCard {
                symbologies.forEachIndexed { index, symbology ->
                    val on = byName[symbology.onSetting]?.value == "1"
                    NavRow(symbology.displayName, if (on) "Ein" else "Aus") { onOpenDetail(symbology) }
                    if (index != symbologies.lastIndex) RowDivider()
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun WeitereScreen(rows: List<SettingRow>, onWrite: (SettingRow, String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "Rohwerte des Scanners ohne kuratierte Beschriftung (Bedeutung siehe BCST-47-Handbuch).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionCard {
                rows.forEachIndexed { index, row ->
                    Column {
                        SettingRowContent(row, onWrite, showRawName = true)
                    }
                    if (index != rows.lastIndex) RowDivider()
                }
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Navigierbare Zeile: Titel links, optionaler aktueller Wert + Pfeil rechts. */
@Composable
private fun NavRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Stellt eine Einstellung passend zu ihrem Wertebereich dar: Auswahlliste, Schalter oder Rohwert. */
@Composable
private fun SettingRowContent(row: SettingRow, onWrite: (SettingRow, String) -> Unit, showRawName: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(labelFor(row.name), style = MaterialTheme.typography.bodyLarge)
            if (showRawName) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val options = settingOptions[row.name]
        when {
            options != null && options.any { it.first == row.value } -> PickerValue(row, options, onWrite)
            isBooleanSetting(row) -> Switch(
                checked = row.value == "1",
                onCheckedChange = { checked -> onWrite(row, if (checked) "1" else "0") },
            )
            else -> RawValueEditor(row, onWrite)
        }
    }
}

@Composable
private fun PickerValue(row: SettingRow, options: List<Pair<String, String>>, onWrite: (SettingRow, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                options.first { it.first == row.value }.second,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
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
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findMainActivity()
        activity?.scanCaptureEnabled = false
        onDispose { activity?.scanCaptureEnabled = true }
    }
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
