package com.kenfenheuer.inventur.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Liest die HID-Berichte des per Kabel angeschlossenen Scanners (BCST-47-USB, generisches HID,
 * keine Tastatur), waehrend der Scanner-Cache hochgeladen wird.
 */
object UsbCacheReader {
    private const val TAG = "UsbCacheReader"
    private const val ACTION_PERMISSION = "com.kenfenheuer.inventurpro.USB_PERMISSION"

    suspend fun ensurePermission(context: Context, device: UsbDevice): Boolean {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usb.hasPermission(device)) return true
        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    context.unregisterReceiver(this)
                    if (cont.isActive) cont.resume(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
            val filter = IntentFilter(ACTION_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
            val flags = PendingIntent.FLAG_MUTABLE or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_UPDATE_CURRENT else 0)
            val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
            usb.requestPermission(device, pi)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    /** Sammelt fuer [millis] Millisekunden alle eingehenden Berichte. [onStarted] laeuft, sobald gelesen wird. */
    suspend fun read(context: Context, device: UsbDevice, millis: Long, onStarted: () -> Unit): List<ByteArray> =
        withContext(Dispatchers.IO) {
            val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val out = mutableListOf<ByteArray>()
            val conn = usb.openDevice(device) ?: run { Log.e(TAG, "openDevice null"); return@withContext out }
            try {
                val itf = (0 until device.interfaceCount).map { device.getInterface(it) }
                    .firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_HID } ?: return@withContext out
                val epIn = (0 until itf.endpointCount).map { itf.getEndpoint(it) }
                    .firstOrNull { it.direction == UsbConstants.USB_DIR_IN } ?: return@withContext out
                if (!conn.claimInterface(itf, true)) { Log.e(TAG, "claimInterface fehlgeschlagen"); return@withContext out }
                Log.i(TAG, "lese von Endpoint ${epIn.address}, maxPacket=${epIn.maxPacketSize}")
                onStarted()
                val buf = ByteArray(maxOf(epIn.maxPacketSize, 64))
                val end = System.currentTimeMillis() + millis
                while (System.currentTimeMillis() < end) {
                    val n = conn.bulkTransfer(epIn, buf, buf.size, 300)
                    if (n > 0) {
                        val chunk = buf.copyOf(n)
                        Log.i(TAG, "Bericht ($n): " + chunk.joinToString(" ") { "%02x".format(it) })
                        out.add(chunk)
                    }
                }
                conn.releaseInterface(itf)
            } finally {
                conn.close()
            }
            out
        }

    private const val NORMAL = "abcdefghijklmnopqrstuvwxyz1234567890"
    private const val SHIFTED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()"

    private fun usageToChar(usage: Int, shift: Boolean): Char? = when (usage) {
        in 0x04..0x27 -> (if (shift) SHIFTED else NORMAL)[usage - 0x04]
        0x2c -> ' '
        0x2d -> if (shift) '_' else '-'
        0x2e -> if (shift) '+' else '='
        0x2f -> if (shift) '{' else '['
        0x30 -> if (shift) '}' else ']'
        0x31 -> if (shift) '|' else '\\'
        0x33 -> if (shift) ':' else ';'
        0x34 -> if (shift) '"' else '\''
        0x36 -> if (shift) '<' else ','
        0x37 -> if (shift) '>' else '.'
        0x38 -> if (shift) '?' else '/'
        else -> null
    }

    /**
     * Wandelt HID-Tastaturberichte ([Report-ID 1] Modifier, reserviert, Tasten 1-6; US-Belegung) in
     * Barcodes um. Enter/Tab beendet einen Barcode; Berichte ohne Taste (Loslassen) werden uebersprungen.
     */
    fun decodeKeyboardReports(reports: List<ByteArray>): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) result.add(cur.toString()); cur.setLength(0) }
        for (r in reports) {
            val off = if (r.size >= 9 && r[0].toInt() == 1) 1 else 0
            if (r.size < off + 8) continue
            val shift = (r[off].toInt() and 0x22) != 0
            for (i in off + 2 until off + 8) {
                val k = r[i].toInt() and 0xff
                if (k == 0) continue
                if (k == 0x28 || k == 0x58 || k == 0x2b) flush() else usageToChar(k, shift)?.let { cur.append(it) }
            }
        }
        flush()
        return result
    }
}
