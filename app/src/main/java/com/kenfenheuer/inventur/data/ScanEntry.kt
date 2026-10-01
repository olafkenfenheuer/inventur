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
 *
 * Fuer den Server-Abgleich: [updatedAt] ist der Zeitpunkt der letzten Aenderung
 * (die juengere Aenderung gewinnt), [deleted] markiert einen geloeschten Eintrag
 * als Loeschvermerk, und [dirty] heisst "noch nicht zum Server hochgeladen".
 * Aeltere Eintraege haben diese Felder nicht (Gson liefert 0/false).
 *
 * [cleared]: Die Liste wurde geleert ("Liste leeren"). Das ist nur lokal – der Eintrag
 * bleibt auf dem Server. Ein noch nicht hochgeladener Eintrag wird daher ausgeblendet,
 * aber noch zum Server gesendet und danach lokal entfernt.
 */
data class ScanEntry(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = "",
    val device: String? = null,
    val updatedAt: Long = timestamp,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
    val cleared: Boolean = false,
)
