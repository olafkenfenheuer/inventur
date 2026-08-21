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
            devices.addAll(BleListManager.scannerDevices)
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
                    onResult(result)
                }
            }
            bump()
        } catch (t: Throwable) {
            onResult(Result.failure(t))
        }
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
     * Stellt den Scanner auf HID-Tastaturmodus mit Enter als Suffix um – die von
     * dieser App benoetigte Betriebsart. Achtung: Nach dem Umschalten trennt der
     * Scanner die GATT-Verbindung und meldet sich als Bluetooth-Tastatur.
     */
    fun setHidModeWithEnter(device: BleScannerDevice, onResult: (Result<*>) -> Unit) {
        val cmd = """
            [
              {"area":"1","value":"0","name":"bt_mode_low"},
              {"area":"31","value":"0","name":"bt_mode_high"},
              {"area":"34","value":"0","name":"subfix_add_tab"},
              {"area":"15","value":"1","name":"suffix_add_enter"}
            ]
        """.trimIndent()
        sendSetting(device, cmd, onResult)
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
            device.messager.getSettingInfo { result -> main.post { onResult(result) } }
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

    private fun sendSetting(device: BleScannerDevice, cmd: String, onResult: (Result<*>) -> Unit) {
        try {
            device.messager.setSettingInfo(cmd) { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure<Any>(t))
        }
    }

    companion object {
        private const val TAG = "ScannerManager"

        /** Nicht im SDK deklariertes Merkmal, dessen Lesezugriff den Quittungston ausloest. */
        private const val ACK_CHARACTERISTIC_UUID = "0000ff03-0000-1000-8000-00805f9b34fb"
    }
}
