package com.kenfenheuer.inventur.scanner

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.inateck.scanner.ble.BleListManager
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

    private fun refreshDevices() {
        main.post {
            devices.clear()
            devices.addAll(BleListManager.scannerDevices)
            bump()
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

    private fun sendSetting(device: BleScannerDevice, cmd: String, onResult: (Result<*>) -> Unit) {
        try {
            device.messager.setSettingInfo(cmd) { result -> main.post { onResult(result) } }
        } catch (t: Throwable) {
            onResult(Result.failure<Any>(t))
        }
    }

    companion object {
        private const val TAG = "ScannerManager"
    }
}
