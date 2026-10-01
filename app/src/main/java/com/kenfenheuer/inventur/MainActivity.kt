package com.kenfenheuer.inventur

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kenfenheuer.inventur.ui.InventoryViewModel
import com.kenfenheuer.inventur.ui.InventurRoot
import com.kenfenheuer.inventur.ui.theme.InventurTheme

/**
 * Host-Activity. Faengt zusaetzlich die Tastatur-Eingaben des als Bluetooth-HID
 * gekoppelten BCST-47 ab: Der Scanner "tippt" jeden Barcode gefolgt von Enter.
 * Diese Zeichen werden hier gesammelt und beim Enter als kompletter Barcode
 * an das ViewModel uebergeben.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: InventoryViewModel by viewModels()

    private val scanBuffer = StringBuilder()
    private var lastKeyTime = 0L

    /**
     * Wird waehrend einer Texteingabe (z. B. Bemerkung erfassen) auf false gesetzt,
     * damit Tastatur-Events dann das Eingabefeld erreichen statt als Scan zu gelten.
     */
    var scanCaptureEnabled: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Scanner-Empfang im Hintergrund (Expertenmodus), falls aktiviert.
        if (com.kenfenheuer.inventur.scanner.ScannerPrefs.backgroundEnabled(this)) {
            com.kenfenheuer.inventur.scanner.ScannerService.start(this)
        }
        // Bildschirm anlassen, solange die App offen ist (Einstellung im Menue, Standard: an).
        lifecycleScope.launch {
            viewModel.keepScreenOn.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        setContent {
            InventurTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    InventurRoot(viewModel = viewModel)
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Waehrend einer Texteingabe keine Scans abfangen.
        if (!scanCaptureEnabled) return super.dispatchKeyEvent(event)

        // Manche Scanner liefern mehrere Zeichen in einem ACTION_MULTIPLE-Event.
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.characters != null) {
            appendChars(event.characters)
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_TAB -> {
                    finishBarcode()
                    return true
                }
                else -> {
                    val ch = event.unicodeChar
                    if (ch != 0) {
                        // Timeout: liegen zwischen zwei Zeichen mehrere Sekunden,
                        // stammen sie vermutlich nicht aus demselben Scanvorgang.
                        val now = SystemClock.uptimeMillis()
                        if (now - lastKeyTime > 1000) scanBuffer.setLength(0)
                        lastKeyTime = now
                        scanBuffer.append(ch.toChar())
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun appendChars(chars: String) {
        for (c in chars) {
            if (c == '\n' || c == '\r' || c == '\t') {
                finishBarcode()
            } else {
                scanBuffer.append(c)
            }
        }
    }

    private fun finishBarcode() {
        val code = scanBuffer.toString().trim()
        scanBuffer.setLength(0)
        if (code.isNotEmpty()) {
            viewModel.addScan(code)
        }
    }
}
