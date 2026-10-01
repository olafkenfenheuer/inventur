package com.kenfenheuer.inventur

import android.app.Application
import android.util.Log
import com.inateck.scanner.ble.BleListManager

class InventurApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.kenfenheuer.inventur.scanner.ScanBus.init(this)
        com.kenfenheuer.inventur.scanner.ScannerManager.shared.init(this)
        try {
            // Initialisiert das Inateck BLE-SDK (FastBle) fuer die Scanner-Verwaltung.
            BleListManager.init(this)
        } catch (t: Throwable) {
            Log.e("InventurApp", "BleListManager.init fehlgeschlagen", t)
        }
    }
}
