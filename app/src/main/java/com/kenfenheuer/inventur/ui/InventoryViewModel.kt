package com.kenfenheuer.inventur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kenfenheuer.inventur.data.InventoryRepository
import com.kenfenheuer.inventur.data.ScanEntry
import com.kenfenheuer.inventur.data.SyncController
import com.kenfenheuer.inventur.data.SyncFeature
import com.kenfenheuer.inventur.data.SyncHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InventoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = InventoryRepository(app)
    private val prefs = app.getSharedPreferences("settings", Application.MODE_PRIVATE)

    // Alle Eintraege inkl. Loeschvermerke, neueste zuerst.
    private val all = MutableStateFlow(repository.load().sortedByDescending { it.timestamp })

    // Sichtbare Liste fuer die Oberflaeche (ohne Loeschvermerke).
    private val _items = MutableStateFlow(all.value.filterNot { it.deleted || it.cleared })
    val items: StateFlow<List<ScanEntry>> = _items.asStateFlow()

    private val _lastScanned = MutableStateFlow<String?>(null)
    val lastScanned: StateFlow<String?> = _lastScanned.asStateFlow()

    /**
     * Geraete-/Benutzerkennung dieses Geraets. Wird jedem neuen Scan mitgegeben,
     * damit beim Zusammenfuehren mehrerer CSV-Exporte nachvollziehbar bleibt,
     * wer bzw. welches Geraet einen Eintrag erfasst hat.
     */
    private val _deviceLabel = MutableStateFlow(prefs.getString(KEY_DEVICE_LABEL, "").orEmpty())
    val deviceLabel: StateFlow<String> = _deviceLabel.asStateFlow()

    /** Bildschirm anlassen (Standard: an), damit Scans des Bluetooth-Scanners nicht bei Bildschirmsperre verloren gehen. */
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
    }

    /** Server-Abgleich der jeweiligen Variante ("pro") bzw. kein Abgleich ("classic"). */
    val sync: SyncController = SyncFeature.create(app, object : SyncHost {
        override val scope get() = viewModelScope
        override fun entries() = all.value
        override fun change(sync: Boolean, transform: (List<ScanEntry>) -> List<ScanEntry>) = this@InventoryViewModel.change(sync, transform)
        override fun deviceLabel() = _deviceLabel.value
    })

    init {
        // Scans aus dem Expertenmodus (Bluetooth-Nachrichten, Hintergrunddienst) uebernehmen.
        com.kenfenheuer.inventur.scanner.ScanBus.attach { code, ts -> addScan(code, ts) }
        sync.start()
    }

    override fun onCleared() {
        com.kenfenheuer.inventur.scanner.ScanBus.detach()
        super.onCleared()
    }

    fun setDeviceLabel(label: String) {
        val trimmed = label.trim()
        _deviceLabel.value = trimmed
        prefs.edit().putString(KEY_DEVICE_LABEL, trimmed).apply()
    }

    /**
     * Verarbeitet eine gescannte Inventarnummer. Jeder Scan wird als eigener
     * Eintrag mit Zeitstempel festgehalten – keine Mengen-Aggregation.
     */
    fun addScan(rawBarcode: String, timestamp: Long = System.currentTimeMillis()) {
        val barcode = rawBarcode.trim()
        if (barcode.isEmpty()) return
        val entry = ScanEntry(barcode = barcode, timestamp = timestamp, device = _deviceLabel.value.ifEmpty { null }, dirty = true)
        change { listOf(entry) + it }
        _lastScanned.value = barcode
    }

    fun setNote(id: String, note: String) {
        val now = System.currentTimeMillis()
        change { list ->
            list.map { if (it.id == id) it.copy(note = note.trim(), updatedAt = now, dirty = true) else it }
        }
    }

    fun remove(id: String) {
        val now = System.currentTimeMillis()
        change { list -> sync.removeEntry(list, id, now) }
    }

    fun isSyncLinked(): Boolean = sync.isLinked

    /** Leert nur die lokale Liste; die Daten auf dem Server bleiben erhalten. */
    fun clearAll() {
        change { list -> sync.clearList(list) }
        _lastScanned.value = null
    }

    /** Inventarnummern, die mehr als einmal vorkommen (versehentliche Doppelscans). */
    fun duplicateBarcodes(items: List<ScanEntry>): Set<String> =
        items.groupingBy { it.barcode }.eachCount().filterValues { it > 1 }.keys

    /** Aendert den Gesamtbestand atomar, speichert ihn und stoesst ggf. einen Abgleich an. */
    private fun change(sync: Boolean = true, transform: (List<ScanEntry>) -> List<ScanEntry>) {
        val updated = all.updateAndGet(transform)
        _items.value = updated.filterNot { it.deleted || it.cleared }
        repository.save(updated)
        if (sync) this.sync.onLocalChange()
    }

    private fun <T> MutableStateFlow<T>.updateAndGet(f: (T) -> T): T {
        update(f)
        return value
    }

    private companion object {
        const val KEY_DEVICE_LABEL = "device_label"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
