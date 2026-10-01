package com.kenfenheuer.inventur.scanner

import android.util.Log
import com.clj.fastble.BleManager
import com.clj.fastble.bluetooth.BleBluetooth
import com.clj.fastble.callback.BleNotifyCallback
import com.clj.fastble.exception.BleException
import com.inateck.scanner.ble.BleScannerDevice

/**
 * Das Inateck-SDK registriert genau einen Notify-Callback und wertet Scan-Nachrichten (Expertenmodus)
 * nicht aus. Dieser Haken ersetzt den Callback im FastBle-Eintrag durch einen Wrapper, der Scans
 * herausliest und jede Nachricht unveraendert an den SDK-Callback weitergibt.
 */
object NotifyScanHook {
    private const val TAG = "NotifyScanHook"

    private class Tap(
        private val delegate: BleNotifyCallback,
        private val parser: ScanFrameParser,
        private val onCode: (String) -> Unit,
    ) : BleNotifyCallback() {
        override fun onNotifySuccess() = delegate.onNotifySuccess()
        override fun onNotifyFailure(exception: BleException?) = delegate.onNotifyFailure(exception)
        override fun onCharacteristicChanged(data: ByteArray) {
            try {
                parser.feed(data).forEach { code ->
                    Log.i(TAG, "Scan empfangen (${code.length} Zeichen)")
                    onCode(code)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Auswertung fehlgeschlagen", t)
            }
            delegate.onCharacteristicChanged(data)
        }
    }

    /** @return true, wenn mindestens ein Callback umgehaengt wurde oder bereits umgehaengt war. */
    @Suppress("UNCHECKED_CAST")
    fun install(device: BleScannerDevice, onCode: (String) -> Unit): Boolean = try {
        val ble = BleManager.getInstance().allConnectedDevice.firstOrNull { it.mac == device.mac }
        val bt: BleBluetooth? = ble?.let { BleManager.getInstance().getBleBluetooth(it) }
        if (bt == null) {
            Log.w(TAG, "keine verbundene BleBluetooth fuer ${device.mac}")
            false
        } else {
            val field = BleBluetooth::class.java.getDeclaredField("bleNotifyCallbackHashMap").apply { isAccessible = true }
            val map = field.get(bt) as HashMap<String, BleNotifyCallback>
            var found = false
            synchronized(bt) {
                val parser = ScanFrameParser()
                for ((key, cb) in map.entries.toList()) {
                    found = true
                    if (cb is Tap) continue
                    val tap = Tap(cb, parser, onCode)
                    tap.key = cb.key
                    tap.handler = cb.handler
                    map[key] = tap
                    Log.i(TAG, "Notify-Callback umgehaengt: $key")
                }
            }
            found
        }
    } catch (t: Throwable) {
        Log.e(TAG, "install fehlgeschlagen", t)
        false
    }
}
