package com.kenfenheuer.inventur.scanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.inateck.scanner.ble.BleScannerConnectState
import com.kenfenheuer.inventur.MainActivity
import com.kenfenheuer.inventur.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Haelt im Expertenmodus die Verbindung zum Scanner offen (auch bei ausgeschaltetem Bildschirm oder
 * wenn die App nicht im Vordergrund ist) und verbindet bei Abbruch neu. Die Scans selbst werden vom
 * Notify-Haken ([NotifyScanHook]) gelesen und ueber [ScanBus] an die App gegeben.
 */
class ScannerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastText = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Scanner-Empfang", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val n = notification("Verbinde mit dem Scanner …")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(ID, n)
        }
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun loop() {
        val sm = ScannerManager.shared.also { it.init(this) }
        while (scope.isActive) {
            try {
                val wanted = ScannerPrefs.lastMac(this)
                fun tail(m: String?) = m?.substringAfter(':', "").orEmpty()
                val dev = sm.devices.firstOrNull { it.mac == wanted }
                    ?: sm.devices.firstOrNull { wanted != null && tail(it.mac).isNotEmpty() && tail(it.mac) == tail(wanted) }
                    ?: sm.devices.singleOrNull()
                when {
                    dev == null -> {
                        update("Suche den Scanner …")
                        if (!sm.isScanning.value) sm.startScan()
                    }
                    sm.isConnected(dev) -> {
                        update("Scanner verbunden – Scans werden empfangen")
                        sm.ensureScanHook(dev) // nach einer Wiederverbindung erneut sicherstellen
                    }
                    sm.isStuck(dev) -> {
                        update("Verbindung hängt – neuer Versuch …")
                        sm.connect(sm.resetDevice(dev)) { }
                    }
                    dev.connectState == BleScannerConnectState.DISCONNECTED -> {
                        update("Verbinde mit dem Scanner …")
                        sm.connect(dev) { }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Schleife", t)
            }
            delay(8000)
        }
    }

    private fun update(text: String) {
        if (text == lastText) return
        lastText = text
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_barcode_reader)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "ScannerService"
        private const val CHANNEL = "scanner"
        private const val ID = 4711

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ScannerService::class.java))
            } catch (t: Throwable) {
                Log.e(TAG, "Start fehlgeschlagen", t)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScannerService::class.java))
        }
    }
}
