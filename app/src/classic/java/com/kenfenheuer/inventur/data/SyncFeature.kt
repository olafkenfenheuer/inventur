package com.kenfenheuer.inventur.data

/** Variante "classic": kein Server-Abgleich. */
object SyncFeature {
    const val available = false

    fun create(app: android.app.Application, host: SyncHost): SyncController = NoSync
}
