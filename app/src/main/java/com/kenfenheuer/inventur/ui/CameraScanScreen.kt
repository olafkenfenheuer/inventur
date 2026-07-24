package com.kenfenheuer.inventur.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.BeepManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Live-Kamera-Scan als Alternative zum HID-Handscanner. Dekodiert Barcodes
 * kontinuierlich und uebergibt jede erkannte Nummer an [onBarcode]. Gleiche
 * Codes werden kurzzeitig entprellt, damit ein im Bild verbleibender Barcode
 * nicht mehrfach erfasst wird.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    onBack: () -> Unit,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current

    // System-Zurück soll zur Inventurliste fuehren, nicht die App schliessen.
    BackHandler(onBack = onBack)

    // Orientierung waehrend des Kamera-Scans auf Hochformat sperren. Ohne diese
    // Sperre wuerde ein Geraetedreh die Activity neu erstellen – dabei erschiene
    // kurz erneut der Splashscreen und das Sucherbild wuerde neu aufgebaut.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, "Kamera-Berechtigung wird benoetigt", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by remember { mutableStateOf(false) }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kamera-Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
                actions = {
                    if (hasPermission) {
                        IconButton(onClick = { torchOn = !torchOn }) {
                            Icon(
                                if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                contentDescription = if (torchOn) "Blitz aus" else "Blitz an",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Fuer den Kamera-Scan wird der Zugriff auf die Kamera benoetigt.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Kamera-Berechtigung erteilen")
                    }
                }
                return@Box
            }

            BarcodeScannerView(
                modifier = Modifier.fillMaxSize(),
                torchOn = torchOn,
                onBarcode = { code ->
                    lastScanned = code
                    count++
                    onBarcode(code)
                },
            )

            // Statusanzeige mit letztem Treffer und Zaehler ueber dem Sucherbild.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC000000),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "In dieser Sitzung erfasst: $count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = if (lastScanned != null) "Zuletzt gescannt:" else "Barcode in den Rahmen halten …",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    // Den Barcode-Inhalt gross und monospaced anzeigen, damit der
                    // Nutzer direkt pruefen kann, was tatsaechlich erfasst wurde.
                    if (lastScanned != null) {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = lastScanned!!,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeScannerView(
    modifier: Modifier,
    torchOn: Boolean,
    onBarcode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)

    val beepManager = remember {
        context.findActivity()?.let { BeepManager(it) }
    }

    // Entprellung: denselben Code fuer kurze Zeit nur einmal erfassen.
    val debounce = remember { LastScan() }

    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            // Kontinuierlichen Autofokus aktivieren: die Kamera stellt laufend
            // automatisch scharf (statt ZXings periodischem Einzel-Autofokus) –
            // Barcodes werden dadurch schneller und zuverlaessiger erfasst.
            barcodeView.cameraSettings.isContinuousFocusEnabled = true
            setStatusText("")
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) {
                    val text = result.text ?: return
                    if (text.isBlank()) return
                    val now = SystemClock.uptimeMillis()
                    if (text == debounce.code && now - debounce.timeMs < DEBOUNCE_MS) return
                    debounce.code = text
                    debounce.timeMs = now
                    beepManager?.playBeepSoundAndVibrate()
                    currentOnBarcode(text)
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
            })
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    LaunchedEffect(torchOn) {
        if (torchOn) barcodeView.setTorchOn() else barcodeView.setTorchOff()
    }

    AndroidView(
        factory = { barcodeView.apply { resume() } },
        modifier = modifier,
    )
}

private const val DEBOUNCE_MS = 5000L

private class LastScan {
    var code: String? = null
    var timeMs: Long = 0L
}
