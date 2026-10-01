package com.kenfenheuer.inventur.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private const val CMD_MODE_HID = "/*SwhToHID*/"
private const val CMD_ENTER_SETUP = "/*EnterSet*/"
private const val CMD_FACTORY_RESET = "/*SetFun00*/"
private const val CMD_EXIT_SAVE = "/*ExitSave*/"

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

/**
 * Dialog "Scanner zuruecksetzen (Werkseinstellungen)" mit den vier Barcodes in der getesteten Reihenfolge:
 * 1. Bluetooth-Kopplung zuruecksetzen (`/*SwhToHID*/`, Handbuch: Verbindung zuruecksetzen), 2. Einstellungen aufrufen,
 * 3. Werkseinstellungen wiederherstellen, 4. Beenden und speichern (Handbuch Kap. 3.5). Am Geraet getestet: nur mit allen vier
 * Scans (und anschliessendem Aus-/Einschalten) steht der Scanner danach sauber im Werkszustand.
 */
@Composable
fun BluetoothPairingBarcodeDialog(onOpenBluetoothSettings: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scanner zuruecksetzen (Werkseinstellungen)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Achtung: Setzt ALLE Einstellungen des Scanners auf Werkswerte zurueck (Verbindungsmodus, Ton, Suffix/Enter, " +
                        "Barcode-Typen, Cache-Modus …). Vorher in der Konfiguration \"Einstellungen sichern\" und den Scanner " +
                        "in den Android-Bluetooth-Einstellungen entfernen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Die vier Barcodes nacheinander in dieser Reihenfolge mit dem Scanner vom Display abscannen " +
                        "(Display moeglichst hell, nach jedem Scan piept der Scanner).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BarcodeCard("1. Bluetooth-Verbindung zuruecksetzen", CMD_MODE_HID)
                BarcodeCard("2. Einstellungen aufrufen", CMD_ENTER_SETUP)
                BarcodeCard("3. Werkseinstellungen wiederherstellen", CMD_FACTORY_RESET)
                BarcodeCard("4. Beenden und speichern", CMD_EXIT_SAVE)
                Text(
                    "Danach: Scanner aus- und wieder einschalten. Die App verbindet ihn (Name \"Nano …\", Tastaturmodus) und " +
                        "bietet die Umstellung auf den Expertenmodus an; anschliessend in der Konfiguration \"Sicherung einspielen\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onOpenBluetoothSettings) { Text("Bluetooth-Einstellungen") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Schliessen") } },
    )
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
