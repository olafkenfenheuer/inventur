package com.kenfenheuer.inventur.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.inateck.scanner.ble.BleScannerDevice
import com.kenfenheuer.inventur.scanner.ScannerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "UsbScannerPrompt"

private fun UsbDevice.describe(): String {
    val names = (0 until interfaceCount).joinToString(", ") { i ->
        val itf = getInterface(i)
        val cls = when (itf.interfaceClass) {
            3 -> "HID"; 2, 10 -> "seriell"; 8 -> "Speicher"; 255 -> "herstellerspez."; else -> "Klasse ${itf.interfaceClass}"
        }
        if (itf.interfaceClass == 3) "$cls(${itf.interfaceProtocol.let { if (it == 1) "Tastatur" else "Protokoll $it" }})" else cls
    }
    return "USB ${"%04x".format(vendorId)}:${"%04x".format(productId)} ${productName.orEmpty()} – $names"
}

private fun UsbDevice.looksLikeScanner(): Boolean {
    @SuppressLint("NewApi")
    val names = listOfNotNull(
        productName,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) manufacturerName else null,
    ).joinToString(" ")
    return vendorId == SCANNER_VENDOR_ID ||
        names.contains("inateck", ignoreCase = true) ||
        names.contains("BCST", ignoreCase = true)
}

/**
 * Erkennt, wenn der Scanner per USB-Kabel angeschlossen wird, und bietet an, den Scanner-Cache
 * (Inventurmodus) hochzuladen bzw. zu leeren. Der Scanner tippt den Cache dabei ueber das Kabel
 * ein; MainActivity faengt die Barcodes wie normale Scans ab. Befehle laufen per Bluetooth (SDK).
 */
@Composable
fun UsbScannerPrompt(onScan: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scanner = remember { ScannerManager.shared.also { it.init(context) } }
    var showPrompt by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    var cacheCount by remember { mutableStateOf<Int?>(null) }
    var device by remember { mutableStateOf<BleScannerDevice?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var usbInfo by remember { mutableStateOf("") }
    var usbDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var importedCount by remember { mutableStateOf(0) }
    var received by remember { mutableStateOf<List<ByteArray>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val usb: UsbDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                Log.i(TAG, "USB angeschlossen: vid=${usb?.vendorId} pid=${usb?.productId} name=${usb?.productName}")
                if (usb != null && usb.looksLikeScanner()) { usbInfo = usb.describe(); usbDevice = usb; showPrompt = true }
            }
        }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Bluetooth-Verbindung zum Scanner herstellen, sobald das Popup erscheint, und Anzahl lesen.
    LaunchedEffect(showPrompt) {
        if (!showPrompt) return@LaunchedEffect
        cacheCount = null
        status = null
        connected = false
        importedCount = 0
        val d = scanner.findBondedScanner(context)
        device = d
        Log.i(TAG, "gekoppelter Scanner: ${d?.name} ${d?.mac}")
        if (d == null) return@LaunchedEffect
        if (!scanner.isConnected(d)) {
            scanner.connect(d) { r ->
                Log.i(TAG, "connect: $r")
                r.exceptionOrNull()?.let { status = "Bluetooth-Verbindung fehlgeschlagen: ${it.message ?: it.javaClass.simpleName}" }
            }
        }
        repeat(30) {
            delay(1000)
            if (scanner.isConnected(d)) {
                Log.i(TAG, "verbunden")
                connected = true
                // Scanner braucht nach dem Verbinden einen Moment (Autorisierung); mehrfach versuchen.
                repeat(5) { attempt ->
                    delay(if (attempt == 0) 2000L else 2500L)
                    var done = false
                    scanner.getCacheCount(d) { r ->
                        Log.i(TAG, "cacheCount (Versuch ${attempt + 1}): $r")
                        r.getOrNull()?.let { cacheCount = it; status = null; done = true }
                        if (r.isFailure && attempt == 4) status = "Cache-Anzahl nicht lesbar: ${r.exceptionOrNull()?.message}"
                    }
                    delay(1500)
                    if (done || cacheCount != null) return@LaunchedEffect
                }
                return@LaunchedEffect
            }
            if (status != null) return@LaunchedEffect
        }
        status = "Keine Bluetooth-Verbindung zum Scanner (Status: ${d.connectState}). Ist Bluetooth am Scanner im Kabelbetrieb aktiv?"
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    if (showPrompt) {
        val d = device
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("Scanner per Kabel angeschlossen") },
            text = {
                Text(
                    when {
                        d == null -> "Der Scanner ist nicht per Bluetooth gekoppelt, daher kann der Cache nicht gesteuert werden."
                        cacheCount != null -> "Im Scanner-Cache: ${cacheCount} Scans. Hochladen überträgt sie in die Liste."
                        status != null -> status!!
                        connected -> "Verbunden. Hochladen überträgt den Scanner-Cache in die Liste."
                        else -> "Verbinde mit dem Scanner …"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = d != null && connected,
                    onClick = {
                        showPrompt = false
                        val usb = usbDevice
                        scope.launch {
                            if (usb != null && !com.kenfenheuer.inventur.data.UsbCacheReader.ensurePermission(context, usb)) {
                                toast("USB-Berechtigung abgelehnt"); return@launch
                            }
                            // Erst lesen, dann den Upload anstossen; der Scanner sendet ueber das USB-Kabel.
                            val reader = usb?.let {
                                async {
                                    com.kenfenheuer.inventur.data.UsbCacheReader.read(context, it, 8000) {
                                        d?.let { dev ->
                                            scanner.uploadCache(dev) { r ->
                                                toast(if (r.isSuccess) "Cache wird hochgeladen …" else "Upload fehlgeschlagen")
                                            }
                                        }
                                    }
                                }
                            }
                            received = reader?.await() ?: emptyList()
                        }
                    },
                ) { Text("Scans vom Cache hochladen") }
            },
            dismissButton = {
                Row2(
                    onClear = { askClear = true; showPrompt = false },
                    onClose = { showPrompt = false },
                    clearEnabled = d != null && connected,
                )
            },
        )
    }

    // Empfangene Barcodes sofort uebernehmen; danach Rueckfrage zum Leeren des Scanner-Caches.
    LaunchedEffect(received) {
        val reports = received ?: return@LaunchedEffect
        val texts = com.kenfenheuer.inventur.data.UsbCacheReader.decodeKeyboardReports(reports)
        texts.forEach(onScan)
        importedCount = texts.size
        received = null
        if (texts.isNotEmpty()) askClear = true
        else toast("Keine Scans im Scanner-Cache empfangen")
    }

    // Nach dem Upload: Cache leeren? (eigener Dialog, damit die eingetippten Scans nicht im Dialog landen)
    if (askClear && !showPrompt) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Scanner-Cache leeren?") },
            text = { Text((if (importedCount > 0) "$importedCount Scans aus dem Scanner-Cache wurden in die Liste übernommen. Cache im Scanner jetzt löschen?" else "Alle Scans im Scanner-Cache löschen?") + " Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    device?.let { scanner.clearCache(it) { r -> toast(if (r.isSuccess) "Cache geleert" else "Fehler beim Leeren") } }
                }) { Text("Cache leeren") }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Behalten") } },
        )
    }
}

@Composable
private fun Row2(onClear: () -> Unit, onClose: () -> Unit, clearEnabled: Boolean) {
    androidx.compose.foundation.layout.Row {
        TextButton(enabled = clearEnabled, onClick = onClear) { Text("Cache leeren …") }
        TextButton(onClick = onClose) { Text("Schließen") }
    }
}
