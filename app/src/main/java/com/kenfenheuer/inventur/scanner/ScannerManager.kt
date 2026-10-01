package com.kenfenheuer.inventur.scanner

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.clj.fastble.BleManager
import com.clj.fastble.callback.BleReadCallback
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import com.inateck.scanner.ble.BleListManager
import com.inateck.scanner.ble.BleMessager
import com.inateck.scanner.ble.BleScannerConnectState
import com.inateck.scanner.ble.BleScannerDevice
import com.inateck.scanner.ble.callback.BleScanResultCallBack

/**
 * Duenne Huelle um das Inateck BLE-SDK. Verwaltet Geraetesuche, Verbindung und
 * Konfiguration des BCST-47. Der eigentliche Barcode-Empfang laeuft NICHT hierueber,
 * sondern per HID-Tastatur (siehe MainActivity) – das SDK liefert keine Live-Scans.
 */
class ScannerManager {

    private var appContext: Context? = null

    fun init(context: Context) { appContext = context.applicationContext }

    private val main = Handler(Looper.getMainLooper())

    /** Aktuell gefundene Geraete (nur solche, die wie ein Inateck-Scanner aussehen). */
    val devices = mutableStateListOf<BleScannerDevice>()

    val isScanning = mutableStateOf(false)

    /** Wird nach jeder Statusaenderung erhoeht, um Recomposition auszuloesen. */
    val revision = mutableStateOf(0)

    private fun bump() {
        revision.value = revision.value + 1
    }

    /**
     * Stoesst eine Recomposition an. Noetig, weil das SDK seinen connectState
     * ausserhalb von Compose aendert (z.B. wenn Android die GATT-Verbindung
     * selbst aufbaut) – die UI pollt darueber den aktuellen Stand.
     */
    fun refreshState() = bump()

    private fun refreshDevices() {
        main.post {
            devices.clear()
            devices.addAll(BleListManager.scannerDevices.distinctBy { it.mac ?: it.identifier })
            bump()
        }
    }

    /**
     * Nimmt einen per HID-Tastatur gekoppelten Scanner (Name aus dem InputManager,
     * z.B. "Nano …") in die Geraeteliste auf, sofern er ueber die normale Bluetooth-
     * Kopplung (bonded) bekannt ist. Im HID-Tastaturmodus taucht der Scanner sonst
     * in keiner BLE-Suche auf – so bleibt er trotzdem ueber GATT ansprechbar und
     * damit konfigurierbar, ohne dass man ihn erst per Modus-Barcode umschalten muss.
     */
    fun ensureBondedKeyboardDevice(context: Context, keyboardName: String) {
        if (devices.any { it.name == keyboardName }) return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: return
            val bonded = adapter.bondedDevices ?: return
            // Der InputManager-Name traegt oft einen Zusatz wie " Keyboard"
            // (z.B. "Nano 160D-0DEE-HID Keyboard"), der Bluetooth-Bond-Name aber
            // nicht ("Nano 160D-0DEE-HID") – daher Praefix- statt Exaktvergleich.
            val match = bonded.firstOrNull { bt ->
                val btName = bt.name ?: return@firstOrNull false
                keyboardName == btName || keyboardName.startsWith(btName)
            } ?: return
            val scannerDevice = BleScannerDevice(BleDevice(match))
            main.post {
                if (devices.none { it.mac == scannerDevice.mac }) {
                    devices.add(scannerDevice)
                    bump()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "ensureBondedKeyboardDevice fehlgeschlagen", t)
        }
    }

    /** Sucht den gekoppelten Scanner (Name "Nano …", "BCST …" oder "Inateck …") ohne laufende Suche. */
    @android.annotation.SuppressLint("MissingPermission")
    fun findBondedScanner(context: Context): BleScannerDevice? = try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.bondedDevices
            ?.firstOrNull { bt ->
                val n = bt.name.orEmpty()
                n.startsWith("Nano") || n.contains("BCST", true) || n.contains("Inateck", true)
            }
            ?.let { bt -> devices.firstOrNull { it.mac == bt.address } ?: BleScannerDevice(BleDevice(bt)) }
    } catch (t: Throwable) {
        Log.e(TAG, "findBondedScanner fehlgeschlagen", t)
        null
    }

    /** Nimmt den gekoppelten Scanner (auch ohne HID-Tastatur, z. B. im Expertenmodus) in die Geraeteliste auf. */
    fun addBondedScanner(context: Context) {
        val d = findBondedScanner(context) ?: return
        main.post {
            if (devices.none { it.mac == d.mac }) {
                devices.add(d)
                bump()
            }
        }
    }

    fun startScan() {
        try {
            isScanning.value = true
            BleListManager.scan(object : BleScanResultCallBack {
                override fun onScanStarted(scanResultList: List<BleScannerDevice>) = refreshDevices()
                override fun onScanning(device: BleScannerDevice) = refreshDevices()
                override fun onScanFinished(scanResultList: List<BleScannerDevice>) {
                    main.post { isScanning.value = false }
                    refreshDevices()
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "startScan fehlgeschlagen", t)
            main.post { isScanning.value = false }
        }
    }

    fun stopScan() {
        try {
            BleListManager.stopScan()
        } catch (t: Throwable) {
            Log.e(TAG, "stopScan fehlgeschlagen", t)
        }
        isScanning.value = false
    }

    fun connect(device: BleScannerDevice, onResult: (Result<Unit>) -> Unit) {
        try {
            device.connect { result ->
                main.post {
                    bump()
                    if (result.isSuccess) onConnected(device)
                    onResult(result)
                }
            }
            bump()
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    /** Nach erfolgreicher Verbindung: Scans des Expertenmodus mitlesen (Haken) und Adresse merken. */
    private fun onConnected(device: BleScannerDevice) {
        appContext?.let { c -> device.mac?.let { ScannerPrefs.setLastMac(c, it) } }
        // Das SDK registriert seinen Notify-Callback erst im Verlauf des Verbindens; kurz warten, notfalls wiederholen.
        fun attempt(n: Int) {
            if (device.connectState != BleScannerConnectState.CONNECTED) return
            if (NotifyScanHook.install(device) { code -> ScanBus.post(code) } || n >= 30) return
            main.postDelayed({ attempt(n + 1) }, 1000)
        }
        main.postDelayed({ attempt(0) }, 600)
    }

    /** Haken (erneut) setzen, falls noetig; harmlos, wenn bereits gesetzt. */
    fun ensureScanHook(device: BleScannerDevice): Boolean =
        NotifyScanHook.install(device) { code -> ScanBus.post(code) }

    private val stateSince = HashMap<String, Pair<BleScannerConnectState, Long>>()

    /** true, wenn der Verbindungsstatus des Geraets laenger als [maxMs] in "verbindet"/"trennt" haengt. */
    fun isStuck(device: BleScannerDevice, maxMs: Long = 25_000): Boolean {
        val mac = device.mac ?: return false
        val st = device.connectState
        if (st != BleScannerConnectState.CONNECTING && st != BleScannerConnectState.DISCONNECTING) {
            stateSince.remove(mac)
            return false
        }
        val prev = stateSince[mac]
        if (prev == null || prev.first != st) { stateSince[mac] = st to System.currentTimeMillis(); return false }
        return System.currentTimeMillis() - prev.second > maxMs
    }

    /** Verwirft das haengende SDK-Geraet und liefert ein frisches (Status "getrennt") fuer dieselbe Adresse. */
    fun resetDevice(device: BleScannerDevice): BleScannerDevice {
        try {
            BleManager.getInstance().allConnectedDevice.firstOrNull { it.mac == device.mac }
                ?.let { BleManager.getInstance().disconnect(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "disconnect beim Zuruecksetzen: $t")
        }
        val ble = device.javaClass.getMethod("getDevice\$ble_release").invoke(device) as BleDevice
        val fresh = BleScannerDevice(ble)
        stateSince.remove(device.mac)
        main.post {
            val i = devices.indexOfFirst { it.mac == device.mac }
            if (i >= 0) devices[i] = fresh else devices.add(fresh)
            bump()
        }
        Log.i(TAG, "Geraet zurueckgesetzt: ${device.mac}")
        return fresh
    }

    fun disconnect(device: BleScannerDevice, onResult: (Result<Unit>) -> Unit) {
        try {
            device.disconnect { result ->
                main.post {
                    bump()
                    onResult(result)
                }
            }
            bump()
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    fun isConnected(device: BleScannerDevice): Boolean =
        device.connectState == BleScannerConnectState.CONNECTED

    fun getBattery(device: BleScannerDevice, onResult: (Result<Int>) -> Unit) {
        try {
            device.messager.getBatteryInfo { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    fun getVersion(device: BleScannerDevice, onResult: (Result<String>) -> Unit) {
        try {
            device.messager.getVersion { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    /**
     * Verbindungsmodus des Scanners: Einstellung `bt_mode_low` (area 1) – 0 = Expertenmodus (Scans als
     * Bluetooth-Nachricht), 1 = Einfacher Ausgabemodus (Bluetooth-Tastatur). Per Vergleich der
     * Einstellungen beider Modi ermittelt; der Scanner startet seine Bluetooth-Verbindung danach neu.
     */
    fun setExpertMode(device: BleScannerDevice, area: String, expert: Boolean, onResult: (Result<*>) -> Unit) {
        setSetting(device, area, "bt_mode_low", if (expert) "0" else "1") { r ->
            if (r.isSuccess) {
                // Der Scanner uebernimmt den neuen Modus erst nach einem Neustart.
                main.postDelayed({
                    try {
                        device.messager.setRestart { rr -> Log.i(TAG, "Scanner-Neustart: $rr") }
                    } catch (t: Throwable) {
                        Log.e(TAG, "setRestart fehlgeschlagen", t)
                    }
                }, 800)
            }
            onResult(r)
        }
    }

    /** Setzt die Signal-Lautstaerke des Scanners (0 = aus, 4 = mittel). */
    fun setVolume(device: BleScannerDevice, value: Int, onResult: (Result<*>) -> Unit) {
        val cmd = """[{"area":"3","value":"$value","name":"volume"}]"""
        sendSetting(device, cmd, onResult)
    }

    /**
     * Liest alle Einstellungen des Scanners. Jeder Eintrag ist eine Map mit
     * "area", "name" und "value" – dieselben Schluessel, die setSettingInfo erwartet.
     */
    fun getSettings(
        device: BleScannerDevice,
        onResult: (Result<List<Map<String, String>>>) -> Unit,
    ) {
        try {
            device.messager.getSettingInfo { result ->
                // Diagnose: jede Einstellung als eigene Logzeile (das SDK-Log wird abgeschnitten).
                result.getOrNull()?.sortedBy { it["name"] }?.forEach {
                    Log.i("ScannerSettings", "${it["name"]}=${it["value"]} (area ${it["area"]})")
                }
                main.post { onResult(result) }
            }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    /** Schreibt eine einzelne Einstellung (area/name wie von getSettings geliefert). */
    fun setSetting(
        device: BleScannerDevice,
        area: String,
        name: String,
        value: String,
        onResult: (Result<*>) -> Unit,
    ) {
        val cmd = """[{"area":"$area","value":"$value","name":"$name"}]"""
        sendSetting(device, cmd, onResult)
    }

    /**
     * Loest den hoerbaren Quittungston des Scanners aus. Per BLE-Mitschnitt (btsnoop) ermittelt:
     * Die offizielle Inateck-App liest nach jeder Einstellungsaenderung zusaetzlich das Merkmal
     * 0000ff03, das in dieser SDK-Version (2.0.0) nicht deklariert/genutzt wird – erst dieser
     * Lesezugriff laesst den Scanner piepen, das reine setSettingInfo() allein tut es nicht.
     */
    fun playAckBeep(device: BleScannerDevice) {
        try {
            val bleDevice = BleManager.getInstance().allConnectedDevice.firstOrNull { it.mac == device.mac } ?: return
            BleManager.getInstance().read(
                bleDevice,
                BleMessager.serviceUUID,
                ACK_CHARACTERISTIC_UUID,
                object : BleReadCallback() {
                    override fun onReadSuccess(data: ByteArray) {}
                    override fun onReadFailure(exception: BleException) {}
                },
            )
        } catch (t: Throwable) {
            Log.e(TAG, "playAckBeep fehlgeschlagen", t)
        }
    }

    /** Anzahl der im Scanner-Cache (Inventurmodus) gespeicherten Scans. */
    fun getCacheCount(device: BleScannerDevice, onResult: (Result<Int>) -> Unit) {
        try {
            device.messager.getInventoryNum { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    /** Scanner sendet den Cache als Tastatureingabe; MainActivity faengt die Barcodes wie normale Scans ab. */
    fun uploadCache(device: BleScannerDevice, onResult: (Result<Unit>) -> Unit) {
        try {
            device.messager.inventoryUploadCache { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    fun clearCache(device: BleScannerDevice, onResult: (Result<Unit>) -> Unit) {
        try {
            device.messager.inventoryClearCache { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
    }

    private fun sendSetting(device: BleScannerDevice, cmd: String, onResult: (Result<*>) -> Unit) {
        try {
            device.messager.setSettingInfo(cmd) { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure<Any>(t))
        }
    }

    companion object {
        /** Gemeinsame Instanz fuer Oberflaeche und Hintergrunddienst (eine SDK-Verbindung je Scanner). */
        val shared: ScannerManager by lazy { ScannerManager() }

        private const val TAG = "ScannerManager"

        /** Nicht im SDK deklariertes Merkmal, dessen Lesezugriff den Quittungston ausloest. */
        private const val ACK_CHARACTERISTIC_UUID = "0000ff03-0000-1000-8000-00805f9b34fb"
    }
}
