package com.kenfenheuer.inventur.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Zeigt die Inateck-Setup-Barcodes als Code128 zum Abscannen vom Display.
 * Quelle der Kommando-Strings: offizielle Inateck-Handbuecher (dieselbe
 * Firmware-Familie wie BCST-47). Damit laesst sich der Scanner zwischen
 * HID-Tastaturmodus und GATT-Konfigurationsmodus umschalten, ohne das
 * Papierhandbuch zu benoetigen.
 */
private const val CMD_ENTER_SETUP = "/*EnterSet*/"
private const val CMD_EXIT_SAVE = "/*ExitSave*/"
private const val CMD_MODE_GATT = "/*BLE_GATT*/"
private const val CMD_MODE_HID = "/*SwhToHID*/"

private fun qrBitmap(content: String, size: Int = 400): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 2)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] =
                if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.RGB_565)
}

@Composable
fun ModeBarcodesPanel(onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
            }
            Text("Modus-Barcodes", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Die Barcodes nacheinander mit dem Scanner vom Display abscannen. " +
                "Display dafuer moeglichst hell stellen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))

        // Auf dem Tablet stehen die QR-Codes genau 2-spaltig nebeneinander,
        // auf dem Telefon untereinander.
        BoxWithConstraints {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "In den Konfigurationsmodus (GATT) wechseln",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Danach findet die Geraetesuche den Scanner; Scans als Tastatur " +
                            "kommen in diesem Modus NICHT an.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { BarcodeCard("1. Einstellungen aufrufen", CMD_ENTER_SETUP) }
            item { BarcodeCard("2. Bluetooth-GATT-Modus", CMD_MODE_GATT) }
            item { BarcodeCard("3. Speichern und Beenden", CMD_EXIT_SAVE) }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Zurueck in den Tastaturmodus (HID)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Alternativ stellt der Button \"HID + Enter\" bei bestehender " +
                            "GATT-Verbindung den Modus per App um.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { BarcodeCard("1. Einstellungen aufrufen", CMD_ENTER_SETUP) }
            item { BarcodeCard("2. Bluetooth-HID-Modus", CMD_MODE_HID) }
            item { BarcodeCard("3. Speichern und Beenden", CMD_EXIT_SAVE) }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.size(16.dp)) }
        }
        }
    }
}

@Composable
private fun BarcodeCard(label: String, content: String) {
    val bitmap = remember(content) { qrBitmap(content) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(6.dp))
            // QR-Code braucht weissen Hintergrund samt Ruhezone – auch im Dark Mode.
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = content,
                modifier = Modifier
                    .size(220.dp)
                    .align(Alignment.CenterHorizontally)
                    .background(Color.White)
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
