package com.kenfenheuer.inventur.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.inateck.scanner.ble.BleScannerConnectState
import com.inateck.scanner.ble.BleScannerDevice
import com.kenfenheuer.inventur.scanner.ScannerManager

/**
 * Name der ersten externen HID-Tastatur (= Scanner im Tastaturmodus), sonst null.
 * Nur ueber diesen Weg kommen Scans in der App an – die BLE-Verbindung hier im
 * Screen dient ausschliesslich der Konfiguration.
 */
private fun connectedHidKeyboardName(context: Context): String? {
    val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    return inputManager.inputDeviceIds
        .asSequence()
        .mapNotNull { inputManager.getInputDevice(it) }
        .firstOrNull { device ->
            val isKeyboard = device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            // isExternal gibt es erst ab API 29; davor genuegt "echte, nicht-virtuelle Volltastatur",
            // denn Telefone haben keine eingebaute alphabetische Hardware-Tastatur.
            val isExternal = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || device.isExternal
            isKeyboard && !device.isVirtual && isExternal
        }
        ?.name
}

/** Beobachtet die Eingabegeraete und liefert den HID-Tastatur-Namen live (null = keine verbunden). */
@Composable
private fun rememberHidKeyboardName(): String? {
    val context = LocalContext.current
    var name by remember { mutableStateOf(connectedHidKeyboardName(context)) }
    DisposableEffect(Unit) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) { name = connectedHidKeyboardName(context) }
            override fun onInputDeviceRemoved(deviceId: Int) { name = connectedHidKeyboardName(context) }
            override fun onInputDeviceChanged(deviceId: Int) { name = connectedHidKeyboardName(context) }
        }
        inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { inputManager.unregisterInputDeviceListener(listener) }
    }
    return name
}

private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scanner = remember { ScannerManager() }

    var hasPermission by remember {
        mutableStateOf(
            requiredBlePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermission = result.values.all { it }
        if (hasPermission) scanner.startScan()
        else Toast.makeText(context, "Bluetooth-Berechtigung wird benoetigt", Toast.LENGTH_LONG).show()
    }

    // Ergebnisse (Akku/Version) je Geraet.
    val info = remember { mutableStateMapOf<String, String>() }

    // Geraet, dessen Konfiguration gerade angezeigt wird (null = Geraeteliste).
    var configDevice by remember { mutableStateOf<BleScannerDevice?>(null) }

    // Moduswechsel-Barcodes (HID <-> GATT) als Vollbild-Ansicht.
    var showModeBarcodes by remember { mutableStateOf(false) }

    // Das SDK aendert connectState ausserhalb von Compose – regelmaessig nachziehen,
    // damit Gerätekarten (Verbinden/Trennen/Konfiguration) den echten Stand zeigen.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            scanner.refreshState()
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Scannereinstellungen") },
                navigationIcon = {
                    IconButton(onClick = {
                        scanner.stopScan()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            Text(
                "Der eigentliche Scan-Empfang laeuft ueber den HID-Tastaturmodus. " +
                    "Hier kannst du den BCST-47 verbinden, seinen Status pruefen und ihn konfigurieren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            val keyboardName = rememberHidKeyboardName()
            HidStatusBanner(keyboardName)
            Spacer(Modifier.size(12.dp))

            // Per HID-Tastatur gekoppelter Scanner ist per BLE-Suche unsichtbar –
            // trotzdem als (bonded) Geraet in die Liste aufnehmen, damit er
            // konfigurierbar bleibt, ohne erst den Modus zu wechseln.
            LaunchedEffect(keyboardName) {
                keyboardName?.let { scanner.ensureBondedKeyboardDevice(context, it) }
            }

            // Die Modus-Barcodes brauchen kein Bluetooth – immer zugaenglich.
            if (showModeBarcodes) {
                ModeBarcodesPanel(onClose = { showModeBarcodes = false })
                return@Column
            }

            if (!hasPermission) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { permissionLauncher.launch(requiredBlePermissions()) }) {
                        Text("Bluetooth-Berechtigung erteilen")
                    }
                    OutlinedButton(onClick = { showModeBarcodes = true }) {
                        Text("Modus-Barcodes")
                    }
                }
                return@Column
            }

            configDevice?.let { device ->
                ScannerConfigPanel(
                    device = device,
                    scanner = scanner,
                    onClose = { configDevice = null },
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { if (scanner.isScanning.value) scanner.stopScan() else scanner.startScan() },
                ) {
                    Text(if (scanner.isScanning.value) "Suche stoppen" else "Geraete suchen")
                }
                OutlinedButton(onClick = { showModeBarcodes = true }) {
                    Text("Modus-Barcodes")
                }
                if (scanner.isScanning.value) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.size(12.dp))

            // revision lesen, damit Statuswechsel eine Recomposition ausloesen.
            @Suppress("UNUSED_EXPRESSION")
            scanner.revision.value

            if (scanner.devices.isEmpty()) {
                Text(
                    "Keine Geraete gefunden. Im HID-Tastaturmodus ist der Scanner fuer die " +
                        "Suche unsichtbar – wechsle ihn ueber \"Modus-Barcodes\" in den " +
                        "GATT-Modus und starte die Suche erneut.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scanner.devices, key = { it.mac ?: it.identifier }) { device ->
                        DeviceCard(
                            device = device,
                            scanner = scanner,
                            info = info[device.mac],
                            onInfoChange = { text -> device.mac?.let { info[it] = text } },
                            onConfigure = { configDevice = device },
                            context = context,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Zeigt, ob der Scanner als HID-Tastatur gekoppelt ist. Ohne diese Kopplung kommen
 * keine Scans an – das "Verbinden" hier im Screen (BLE/GATT) reicht dafuer nicht.
 */
@Composable
private fun HidStatusBanner(keyboardName: String?) {
    val context = LocalContext.current

    if (keyboardName != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Text(
                "Scanner-Tastatur verbunden: $keyboardName – Scans kommen an.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Keine Scanner-Tastatur gekoppelt!",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Scans kommen so NICHT in der App an. Der Scanner muss in den " +
                        "Android-Bluetooth-Einstellungen als Tastatur (HID) gekoppelt werden – " +
                        "das \"Verbinden\" hier im Screen dient nur der Konfiguration. " +
                        "Der BCST-47 erscheint dort z. B. als \"Nano …\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("Bluetooth-Einstellungen oeffnen") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    device: BleScannerDevice,
    scanner: ScannerManager,
    info: String?,
    onInfoChange: (String) -> Unit,
    onConfigure: () -> Unit,
    context: android.content.Context,
) {
    var busy by remember(device.mac) { mutableStateOf(false) }

    // revision lesen, damit jede Statusaenderung diese Karte neu zusammensetzt.
    @Suppress("UNUSED_EXPRESSION")
    scanner.revision.value

    // Zustand genau EINMAL lesen – das SDK aendert ihn nebenlaeufig, zwei Reads
    // koennen sonst widerspruechliche UI ergeben (Status "verbunden" + Button "Verbinden").
    val state = device.connectState
    val connected = state == BleScannerConnectState.CONNECTED

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.name ?: "Unbekanntes Geraet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = device.mac ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Status: " + connectStateLabel(state),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.size(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!connected) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scanner.connect(device) { result ->
                                busy = false
                                if (result.isFailure) {
                                    Toast.makeText(context, "Verbindung fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    ) { Text("Verbinden") }
                } else {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scanner.disconnect(device) { busy = false }
                        },
                    ) { Text("Trennen") }
                }
                // Auch bei einem nur per HID-Tastatur gekoppelten (noch nicht GATT-
                // verbundenen) Scanner anzeigen – Klick verbindet bei Bedarf zuerst.
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        if (connected) {
                            onConfigure()
                        } else {
                            busy = true
                            scanner.connect(device) { result ->
                                busy = false
                                if (result.isSuccess) {
                                    onConfigure()
                                } else {
                                    Toast.makeText(context, "Verbindung fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                ) { Text("Konfiguration") }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            if (connected) {
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scanner.getBattery(device) { r ->
                            onInfoChange(r.fold({ "Akku: $it %" }, { "Akku: Fehler" }))
                        }
                    }) { Text("Akku") }
                    OutlinedButton(onClick = {
                        scanner.getVersion(device) { r ->
                            onInfoChange(r.fold({ "Version: $it" }, { "Version: Fehler" }))
                        }
                    }) { Text("Version") }
                    OutlinedButton(onClick = {
                        scanner.setVolume(device, 4) { r ->
                            Toast.makeText(
                                context,
                                if (r.isSuccess) "Lautstaerke gesetzt" else "Fehler",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) { Text("Ton an") }
                    Button(onClick = {
                        scanner.setHidModeWithEnter(device) { r ->
                            Toast.makeText(
                                context,
                                if (r.isSuccess) "HID-Modus gesetzt – Scanner koppelt neu als Tastatur"
                                else "Fehler beim Umschalten",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }) { Text("HID + Enter") }
                }
                if (info != null) {
                    Spacer(Modifier.size(6.dp))
                    Text(info, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun connectStateLabel(state: BleScannerConnectState): String = when (state) {
    BleScannerConnectState.CONNECTED -> "verbunden"
    BleScannerConnectState.CONNECTING -> "verbindet …"
    BleScannerConnectState.DISCONNECTING -> "trennt …"
    BleScannerConnectState.DISCONNECTED -> "getrennt"
    BleScannerConnectState.UNKNOWN -> "unbekannt"
}
