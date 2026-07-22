package com.kenfenheuer.inventur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kenfenheuer.inventur.data.InventoryRepository
import com.kenfenheuer.inventur.data.ScanEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InventoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = InventoryRepository(app)
    private val prefs = app.getSharedPreferences("settings", Application.MODE_PRIVATE)

    // Neueste Scans oben.
    private val _items = MutableStateFlow(repository.load().sortedByDescending { it.timestamp })
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

    fun setDeviceLabel(label: String) {
        val trimmed = label.trim()
        _deviceLabel.value = trimmed
        prefs.edit().putString(KEY_DEVICE_LABEL, trimmed).apply()
    }

    /**
     * Verarbeitet eine gescannte Inventarnummer. Jeder Scan wird als eigener
     * Eintrag mit Zeitstempel festgehalten – keine Mengen-Aggregation.
     */
    fun addScan(rawBarcode: String) {
        val barcode = rawBarcode.trim()
        if (barcode.isEmpty()) return
        val entry = ScanEntry(barcode = barcode, device = _deviceLabel.value.ifEmpty { null })
        val updated = listOf(entry) + _items.value
        _items.value = updated
        _lastScanned.value = barcode
        repository.save(updated)
    }

    fun setNote(id: String, note: String) {
        val updated = _items.value.map {
            if (it.id == id) it.copy(note = note.trim()) else it
        }
        _items.value = updated
        repository.save(updated)
    }

    fun remove(id: String) {
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        repository.save(updated)
    }

    fun clearAll() {
        _items.value = emptyList()
        _lastScanned.value = null
        repository.save(emptyList())
    }

    /** Inventarnummern, die mehr als einmal vorkommen (versehentliche Doppelscans). */
    fun duplicateBarcodes(items: List<ScanEntry>): Set<String> =
        items.groupingBy { it.barcode }.eachCount().filterValues { it > 1 }.keys

    private companion object {
        const val KEY_DEVICE_LABEL = "device_label"
    }
}
