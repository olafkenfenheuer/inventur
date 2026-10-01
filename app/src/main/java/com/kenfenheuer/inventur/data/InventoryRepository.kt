package com.kenfenheuer.inventur.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Persistiert die Scan-Liste als JSON-Datei im internen App-Speicher, damit
 * erfasste Scans einen Neustart der App ueberleben.
 */
class InventoryRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "scans.json")

    @Synchronized
    fun load(): List<ScanEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<ScanEntry>>() {}.type
            gson.fromJson<List<ScanEntry>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(items: List<ScanEntry>) {
        try {
            file.writeText(gson.toJson(items))
        } catch (e: Exception) {
            // Speichern fehlgeschlagen – Liste bleibt trotzdem im Speicher erhalten.
        }
    }
}
