package com.kenfenheuer.inventur.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Erzeugt aus der Scan-Liste eine CSV-Datei und liefert einen Teilen-Intent.
 *
 * Format: Semikolon-getrennt (deutsches Excel), UTF-8 mit BOM fuer Umlaute.
 * Eine Zeile pro gescannter Inventarnummer.
 */
object CsvExporter {

    private val timeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
    private val fileTimeFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.GERMANY)

    /** Schreibt die CSV-Datei in den Cache und gibt sie zurueck. */
    fun writeCsv(context: Context, items: List<ScanEntry>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "inventur_${fileTimeFormat.format(Date())}.csv")

        // In der Exportdatei chronologisch (aeltester Scan zuerst).
        val ordered = items.sortedBy { it.timestamp }

        val sb = StringBuilder()
        sb.append('﻿') // UTF-8 BOM, damit Excel Umlaute korrekt anzeigt
        sb.append("Nr;Inventarnummer;Zeitpunkt;Bemerkung\r\n")
        ordered.forEachIndexed { index, item ->
            sb.append(index + 1).append(';')
            sb.append(escape(item.barcode)).append(';')
            sb.append(escape(timeFormat.format(Date(item.timestamp)))).append(';')
            sb.append(escape(item.note)).append("\r\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /** Erzeugt einen Chooser-Intent zum Teilen der CSV-Datei. */
    fun shareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Inventurliste teilen")
    }

    private fun escape(value: String): String {
        // CSV-Feld nur quoten, wenn Sonderzeichen enthalten sind.
        return if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
