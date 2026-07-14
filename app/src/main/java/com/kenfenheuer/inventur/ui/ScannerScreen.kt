package com.kenfenheuer.inventur.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Scanner verbinden") },
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

            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(requiredBlePermissions()) }) {
                    Text("Bluetooth-Berechtigung erteilen")
                }
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
                    "Keine Geraete gefunden. Stelle sicher, dass der Scanner im GATT-Modus " +
                        "und eingeschaltet ist, und starte die Suche.",
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
                            context = context,
                        )
                    }
                }
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
    context: android.content.Context,
) {
    var busy by remember(device.mac) { mutableStateOf(false) }
    val connected = device.connectState == BleScannerConnectState.CONNECTED

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
                text = "Status: " + connectStateLabel(device.connectState),
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
