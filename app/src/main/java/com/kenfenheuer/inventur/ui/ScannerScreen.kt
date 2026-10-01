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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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

/** USB-HID Vendor/Product-ID des Inateck BCST-47 (per sysfs-uhid-Pfad ermittelt). */
const val SCANNER_VENDOR_ID = 0x3373
private const val SCANNER_PRODUCT_ID = 0xB34C

/**
 * Liefert den Namen des verbundenen BCST-47 im HID-Tastaturmodus, sonst null. Nur
 * ueber diesen Weg kommen Scans in der App an – die BLE-Verbindung hier im Screen
 * dient ausschliesslich der Konfiguration.
 */
private fun connectedHidKeyboardName(context: Context): String? {
    val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    val keyboards = inputManager.inputDeviceIds
        .asSequence()
        .mapNotNull { inputManager.getInputDevice(it) }
        .filter { device ->
            val isKeyboard = device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            // isExternal gibt es erst ab API 29; davor genuegt "echte, nicht-virtuelle Volltastatur",
            // denn Telefone haben keine eingebaute alphabetische Hardware-Tastatur.
            val isExternal = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || device.isExternal
            isKeyboard && !device.isVirtual && isExternal
        }
        .toList()
    // Bevorzugt den BCST-47 anhand VID/PID, damit eine zufaellig gleichzeitig
    // verbundene andere Bluetooth-Tastatur nicht faelschlich als Scanner gilt.
    // Falls keine Uebereinstimmung dabei ist (z.B. ein anderes Scanner-Modell),
    // auf die erste externe Tastatur zurueckfallen.
    return (
        keyboards.firstOrNull { it.vendorId == SCANNER_VENDOR_ID && it.productId == SCANNER_PRODUCT_ID }
            ?: keyboards.firstOrNull()
        )?.name
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
    val scanner = remember { ScannerManager.shared.also { it.init(context) } }

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

    // Beim ersten Oeffnen die Bluetooth-Berechtigung direkt anfragen, statt erst auf
    // den Button zu warten (der Button bleibt fuer eine zuvor abgelehnte Anfrage).
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(requiredBlePermissions())
    }

    // Ergebnisse (Akku/Version) je Geraet.
    val info = remember { mutableStateMapOf<String, String>() }

    // Geraet, dessen Konfiguration gerade angezeigt wird (null = Geraeteliste).
    var configDevice by remember { mutableStateOf<BleScannerDevice?>(null) }

    // Ohne Benachrichtigungs-Berechtigung (Android 13+) zeigt Android die Dienst-Benachrichtigung samt Symbol nicht.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Hintergrund-Empfang (Dienst) ein/aus; wird auch vom Reset-Ablauf ausgeschaltet.
    var backgroundOn by remember { mutableStateOf(com.kenfenheuer.inventur.scanner.ScannerPrefs.backgroundEnabled(context)) }
    LaunchedEffect(Unit) { if (backgroundOn) ensureNotificationPermission() }

    // Scanner zuruecksetzen (Werkseinstellungen): erst Hinweis/Abfrage, weil dabei der Hintergrunddienst gestoppt und der Scanner getrennt wird.
    var showResetConfirm by remember { mutableStateOf(false) }
    var showPairingBarcode by remember { mutableStateOf(false) }
    fun startReset() {
        if (backgroundOn || scanner.devices.any { scanner.isConnected(it) }) showResetConfirm = true else showPairingBarcode = true
    }
    if (showResetConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Scanner zuruecksetzen?") },
            text = {
                Text(
                    "Fuer den Reset wird der Hintergrund-Empfang (Dienst) gestoppt und die Verbindung zum Scanner getrennt, damit " +
                        "der Ablauf nicht gestoert wird. Danach den Scanner in den Android-Bluetooth-Einstellungen entfernen und die " +
                        "Barcodes scannen. Nach dem Reset die Umstellung auf den Expertenmodus bestaetigen; sie schaltet den " +
                        "Hintergrund-Empfang wieder ein.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showResetConfirm = false
                    backgroundOn = false
                    com.kenfenheuer.inventur.scanner.ScannerPrefs.setBackgroundEnabled(context, false)
                    com.kenfenheuer.inventur.scanner.ScannerService.stop(context)
                    scanner.devices.filter { scanner.isConnected(it) }.forEach { scanner.disconnect(it) { } }
                    showPairingBarcode = true
                }) { Text("Stoppen und weiter") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showResetConfirm = false }) { Text("Abbrechen") } },
        )
    }
    // Barcodes zum Zuruecksetzen des Scanners (Werkseinstellungen).
    if (showPairingBarcode) {
        BluetoothPairingBarcodeDialog(
            onOpenBluetoothSettings = {
                showPairingBarcode = false
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            onDismiss = { showPairingBarcode = false },
        )
    }

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
                "Scans kommen entweder als Bluetooth-Tastatur (Einfacher Ausgabemodus) oder als Bluetooth-Nachricht " +
                    "(Expertenmodus, zuverlaessiger und auch im Hintergrund). Hier verbindest du den BCST-47, " +
                    "pruefst seinen Status und konfigurierst ihn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            val keyboardName = rememberHidKeyboardName()
            // Im Expertenmodus braucht der Scanner keine Tastatur: Hinweis nur, wenn weder Tastatur noch Scanner verbunden sind.
            val sdkConnected = scanner.devices.any { scanner.isConnected(it) }
            scanner.revision.value
            if (!(keyboardName == null && sdkConnected)) {
                HidStatusBanner(keyboardName, onShowBarcodes = { startReset() })
                Spacer(Modifier.size(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scans im Hintergrund empfangen", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (backgroundOn) "An (Expertenmodus des Scanners): Die App hält die Bluetooth-Verbindung auch bei ausgeschaltetem Bildschirm und zeigt dazu eine Benachrichtigung."
                        else "Aus: Scans kommen nur über die Bluetooth-Tastatur (Einfacher Ausgabemodus).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = backgroundOn,
                    onCheckedChange = { on ->
                        backgroundOn = on
                        com.kenfenheuer.inventur.scanner.ScannerPrefs.setBackgroundEnabled(context, on)
                        if (on) {
                            ensureNotificationPermission()
                            com.kenfenheuer.inventur.scanner.ScannerService.start(context)
                        } else {
                            com.kenfenheuer.inventur.scanner.ScannerService.stop(context)
                        }
                    },
                )
            }
            Spacer(Modifier.size(12.dp))

            // Per HID-Tastatur gekoppelter Scanner ist per BLE-Suche unsichtbar –
            // trotzdem als (bonded) Geraet in die Liste aufnehmen, damit er
            // konfigurierbar bleibt, ohne erst den Modus zu wechseln.
            LaunchedEffect(keyboardName) {
                if (keyboardName != null) {
                    scanner.ensureBondedKeyboardDevice(context, keyboardName)
                } else if (hasPermission) {
                    // Expertenmodus: keine HID-Tastatur, der gekoppelte Scanner ist trotzdem per Adresse verbindbar.
                    scanner.addBondedScanner(context)
                }
            }

            if (!hasPermission) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = { permissionLauncher.launch(requiredBlePermissions()) }) {
                        Text("Bluetooth-Berechtigung erteilen")
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
                    "Keine Geraete gefunden. Scanner mit der Ausloesertaste wecken und \"Geraete suchen\" erneut druecken. " +
                        "Die Inateck-App darf den Scanner nicht belegen. Im Einfachen Ausgabemodus ist der Scanner fuer die " +
                        "Suche unsichtbar: dort zuerst in den Android-Bluetooth-Einstellungen als Tastatur koppeln.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // Derselbe Scanner kann doppelt erscheinen (HID "Nano …" AC:… und Expertenmodus "HPRT-…" AB:…, gleiche
                // Adresse bis auf das erste Byte): die Karte des verbundenen bzw. zuletzt verbundenen Geraets genuegt.
                val lastMac = com.kenfenheuer.inventur.scanner.ScannerPrefs.lastMac(context)
                fun tail(mac: String?) = mac?.substringAfter(':', "")
                val visible = scanner.devices.toList().filter { d ->
                    scanner.devices.none { o ->
                        o.mac != d.mac && tail(o.mac) == tail(d.mac) && tail(d.mac)?.isNotEmpty() == true &&
                            (scanner.isConnected(o) || (o.mac == lastMac && !scanner.isConnected(d)))
                    }
                }.distinctBy { it.mac ?: it.identifier } // gleiche Adresse nie doppelt (Compose-Schluessel muss eindeutig sein)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.mac ?: it.identifier }) { device ->
                        DeviceCard(
                            device = device,
                            scanner = scanner,
                            info = info[device.mac],
                            onInfoChange = { text -> device.mac?.let { info[it] = text } },
                            onConfigure = { configDevice = device },
                            onEnableBackground = { on ->
                                backgroundOn = on
                                com.kenfenheuer.inventur.scanner.ScannerPrefs.setBackgroundEnabled(context, on)
                                if (on) {
                                    ensureNotificationPermission()
                                    com.kenfenheuer.inventur.scanner.ScannerService.start(context)
                                } else com.kenfenheuer.inventur.scanner.ScannerService.stop(context)
                            },
                            onReset = { startReset() },
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
private fun HidStatusBanner(keyboardName: String?, onShowBarcodes: () -> Unit) {
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
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Kein Scanner verbunden",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "Einfacher Ausgabemodus: Den Scanner in den Android-Bluetooth-Einstellungen als Tastatur koppeln " +
                        "(Name z. B. \"Nano …\"), notfalls vorher den Scanner per Barcode zuruecksetzen (Werkseinstellungen). " +
                        "Expertenmodus: unten \"Geraete suchen\" und \"Scans im Hintergrund empfangen\" einschalten; den " +
                        "Scanner dort nicht in Android koppeln.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) { Text("Bluetooth-Einstellungen oeffnen") }
                    OutlinedButton(onClick = onShowBarcodes) { Text("Scanner zuruecksetzen (Werkseinstellungen)") }
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
    onConfigure: () -> Unit,
    onEnableBackground: (Boolean) -> Unit,
    onReset: () -> Unit,
    context: android.content.Context,
) {
    var busy by remember(device.mac) { mutableStateOf(false) }
    var confirmClearCache by remember(device.mac) { mutableStateOf(false) }
    // Einstellung "inventory_mode" (Scannen per Cache = an, online = aus); null = noch nicht gelesen.
    var inventoryRow by remember(device.mac) { mutableStateOf<SettingRow?>(null) }
    // bt_mode_low: 0 = Expertenmodus, 1 = Einfacher Ausgabemodus.
    var modeRow by remember(device.mac) { mutableStateOf<SettingRow?>(null) }
    var offerExpert by remember(device.mac) { mutableStateOf(false) }

    // revision lesen, damit jede Statusaenderung diese Karte neu zusammensetzt.
    @Suppress("UNUSED_EXPRESSION")
    scanner.revision.value

    // Zustand genau EINMAL lesen – das SDK aendert ihn nebenlaeufig, zwei Reads
    // koennen sonst widerspruechliche UI ergeben (Status "verbunden" + Button "Verbinden").
    val state = device.connectState
    val connected = state == BleScannerConnectState.CONNECTED

    // Beim Oeffnen einmal automatisch verbinden (nicht erneut nach "Trennen").
    var autoConnectTried by remember(device.mac) { mutableStateOf(false) }
    LaunchedEffect(device.mac, state) {
        if (!autoConnectTried && state == BleScannerConnectState.DISCONNECTED) {
            autoConnectTried = true
            busy = true
            scanner.connect(device) { result ->
                busy = false
                if (result.isFailure) Toast.makeText(context, "Verbindung fehlgeschlagen", Toast.LENGTH_SHORT).show()
            }
        } else if (state == BleScannerConnectState.CONNECTED) {
            autoConnectTried = true
        }
    }

    LaunchedEffect(connected, device.mac) {
        if (!connected) { inventoryRow = null; modeRow = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(1500) // Scanner braucht nach dem Verbinden einen Moment
        repeat(3) {
            if (inventoryRow != null && modeRow != null) return@LaunchedEffect
            scanner.getSettings(device) { r ->
                val list = r.getOrNull() ?: return@getSettings
                list.firstOrNull { it["name"] == "inventory_mode" }?.let { m ->
                    inventoryRow = SettingRow(m["area"].orEmpty(), "inventory_mode", m["value"].orEmpty())
                }
                list.firstOrNull { it["name"] == "bt_mode_low" }?.let { m ->
                    modeRow = SettingRow(m["area"].orEmpty(), "bt_mode_low", m["value"].orEmpty())
                    // Einfacher Ausgabemodus erkannt: einmalig die Umstellung auf den Expertenmodus anbieten.
                    if (m["value"] == "1" && !com.kenfenheuer.inventur.scanner.ScannerPrefs.expertOfferDismissed(context) &&
                        !com.kenfenheuer.inventur.scanner.ScannerPrefs.backgroundEnabled(context)
                    ) offerExpert = true
                }
            }
            kotlinx.coroutines.delay(2500)
        }
    }

    if (offerExpert) {
        AlertDialog(
            onDismissRequest = { offerExpert = false },
            title = { Text("Auf Expertenmodus umstellen?") },
            text = {
                Text(
                    "Im Expertenmodus kommen Scans als Bluetooth-Nachricht an: zuverlässiger, auch im Hintergrund und bei " +
                        "ausgeschaltetem Bildschirm (die App zeigt dazu eine Benachrichtigung). Der Scanner trennt sich kurz " +
                        "und verbindet sich neu. Die Inateck-App darf dabei nicht mit dem Scanner verbunden sein.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    offerExpert = false
                    val row = modeRow ?: return@TextButton
                    scanner.setExpertMode(device, row.area, true) { r ->
                        if (r.isSuccess) {
                            modeRow = row.copy(value = "0")
                            onEnableBackground(true)
                            Toast.makeText(context, "Expertenmodus – Scanner verbindet neu", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(context, "Umstellen fehlgeschlagen", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Umstellen") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        offerExpert = false
                        com.kenfenheuer.inventur.scanner.ScannerPrefs.setExpertOfferDismissed(context, true)
                    }) { Text("Nicht mehr fragen") }
                    TextButton(onClick = { offerExpert = false }) { Text("Später") }
                }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = scanner.displayName(device),
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

            // FlowRow: auf schmalen Bildschirmen (Handy) brechen die Knoepfe um, statt zu schrumpfen.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                            // Der Hintergrunddienst haelt die Verbindung und baut sie sonst nach wenigen Sekunden wieder auf:
                            // zuerst den Dienst stoppen (Hintergrund-Empfang aus), dann trennen – erst dann ist der Scanner frei.
                            if (com.kenfenheuer.inventur.scanner.ScannerPrefs.backgroundEnabled(context)) {
                                onEnableBackground(false)
                                Toast.makeText(context, "Hintergrund-Empfang ausgeschaltet – Scanner ist frei", Toast.LENGTH_LONG).show()
                            }
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
                OutlinedButton(onClick = onReset) { Text("Zuruecksetzen …") }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            if (connected) {
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Expertenmodus", style = MaterialTheme.typography.titleSmall)
                        Text(
                            when (modeRow?.value) {
                                null -> "Einstellung wird gelesen …"
                                "0" -> "An: Scans kommen als Bluetooth-Nachricht (zuverlässig, auch im Hintergrund)."
                                else -> "Aus: Einfacher Ausgabemodus, der Scanner arbeitet als Bluetooth-Tastatur."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(
                        enabled = modeRow != null,
                        checked = modeRow?.value == "0",
                        onCheckedChange = { on ->
                            val row = modeRow ?: return@Switch
                            if (on) offerExpert = true else scanner.setExpertMode(device, row.area, false) { r ->
                                if (r.isSuccess) {
                                    modeRow = row.copy(value = "1")
                                    onEnableBackground(false)
                                    Toast.makeText(context, "Einfacher Ausgabemodus – Scanner verbindet neu als Tastatur", Toast.LENGTH_LONG).show()
                                } else Toast.makeText(context, "Umstellen fehlgeschlagen", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
                Spacer(Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Scannen per Cache", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (inventoryRow == null) "Einstellung wird gelesen …"
                            else if (inventoryRow?.value == "1") "An: Scans bleiben im Scanner und werden später per USB-Kabel hochgeladen."
                            else "Aus: Scans kommen sofort per Bluetooth in die App (online).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(
                        enabled = inventoryRow != null,
                        checked = inventoryRow?.value == "1",
                        onCheckedChange = { on ->
                            val row = inventoryRow ?: return@Switch
                            val v = if (on) "1" else "0"
                            scanner.setSetting(device, row.area, row.name, v) { r ->
                                if (r.isSuccess) {
                                    inventoryRow = row.copy(value = v)
                                    scanner.playAckBeep(device)
                                } else {
                                    Toast.makeText(context, "Umschalten fehlgeschlagen", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                }
                // Cache-Optionen nur im Modus "Scannen per Cache".
                if (inventoryRow?.value == "1") {
                    Spacer(Modifier.size(8.dp))
                    Text("Scanner-Cache (Inventurmodus)", style = MaterialTheme.typography.titleSmall)
                    Text("Hochladen: Scanner per USB-Kabel anschließen, dann erscheint ein Popup.", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scanner.getCacheCount(device) { r ->
                                onInfoChange(r.fold({ "Im Scanner-Cache: $it Scans" }, { "Cache: Fehler" }))
                            }
                        }) { Text("Anzahl") }
                        OutlinedButton(onClick = { confirmClearCache = true }) { Text("Cache leeren") }
                    }
                    if (confirmClearCache) {
                        AlertDialog(
                            onDismissRequest = { confirmClearCache = false },
                            title = { Text("Scanner-Cache leeren?") },
                            text = { Text("Alle im Scanner gespeicherten Scans werden gelöscht. Vorher hochladen, sonst sind sie weg.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmClearCache = false
                                    scanner.clearCache(device) { r ->
                                        Toast.makeText(
                                            context,
                                            if (r.isSuccess) "Cache geleert" else "Fehler beim Leeren",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }) { Text("Leeren") }
                            },
                            dismissButton = { TextButton(onClick = { confirmClearCache = false }) { Text("Abbrechen") } },
                        )
                    }
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
