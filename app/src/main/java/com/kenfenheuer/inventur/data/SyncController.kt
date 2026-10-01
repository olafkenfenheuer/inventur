package com.kenfenheuer.inventur.data

import kotlinx.coroutines.CoroutineScope

/** Zugriff einer Sync-Variante auf den Scan-Bestand der App (vom ViewModel bereitgestellt). */
interface SyncHost {
    val scope: CoroutineScope

    /** Gesamtbestand inkl. Loeschvermerke. */
    fun entries(): List<ScanEntry>

    /** Aendert den Gesamtbestand atomar und speichert ihn; [sync] = false loest keinen neuen Abgleich aus. */
    fun change(sync: Boolean = true, transform: (List<ScanEntry>) -> List<ScanEntry>)

    /** Geraete-/Benutzerkennung aus der App-Kopfzeile. */
    fun deviceLabel(): String
}

/**
 * Erweiterungspunkt fuer den Server-Abgleich. Die Variante "classic" nutzt [NoSync] (nur lokale Liste),
 * die Variante "pro" liefert in `src/pro` einen echten Abgleich (siehe `SyncFeature`).
 */
interface SyncController {
    /** Ist dieses Geraet mit einem Konto gekoppelt? */
    val isLinked: Boolean

    /** Startet den regelmaessigen Hintergrundabgleich (einmal beim Anlegen des ViewModels). */
    fun start() {}

    /** Nach jeder lokalen Aenderung (stoesst einen verzoegerten Abgleich an). */
    fun onLocalChange() {}

    /** Einzelnen Eintrag loeschen: mit Konto als Loeschvermerk, sonst entfernen. */
    fun removeEntry(list: List<ScanEntry>, id: String, now: Long): List<ScanEntry> = list.filterNot { it.id == id }

    /** "Liste leeren": mit Konto nur lokal ausblenden (Server behaelt die Daten), sonst alles entfernen. */
    fun clearList(list: List<ScanEntry>): List<ScanEntry> = emptyList()
}

/** Kein Server-Abgleich (Variante "classic"). */
object NoSync : SyncController {
    override val isLinked: Boolean = false
}
