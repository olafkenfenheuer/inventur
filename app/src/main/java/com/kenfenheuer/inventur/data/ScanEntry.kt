package com.kenfenheuer.inventur.data

import java.util.UUID

/**
 * Ein einzelner Scanvorgang. Jeder Scan wird als eigene Zeile mit Zeitstempel
 * festgehalten; optional kann nachtraeglich eine [note] (Bemerkung) ergaenzt werden.
 *
 * [device] haelt die Geraete-/Benutzerkennung zum Zeitpunkt der Erfassung fest –
 * wichtig, wenn mehrere Personen mit mehreren Geraeten dieselbe Inventur machen
 * und die CSV-Dateien spaeter zusammengefuehrt werden. Nullable, weil Eintraege
 * aus aelteren App-Versionen das Feld nicht haben (Gson liefert dann null).
 */
data class ScanEntry(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val device: String? = null,
)
