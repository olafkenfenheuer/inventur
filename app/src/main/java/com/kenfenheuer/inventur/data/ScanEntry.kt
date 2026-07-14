package com.kenfenheuer.inventur.data

import java.util.UUID

/**
 * Ein einzelner Scanvorgang. Jeder Scan wird als eigene Zeile mit Zeitstempel
 * festgehalten; optional kann nachtraeglich eine [note] (Bemerkung) ergaenzt werden.
 */
data class ScanEntry(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
)
