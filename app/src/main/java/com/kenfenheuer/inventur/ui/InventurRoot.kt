package com.kenfenheuer.inventur.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

enum class Screen { Inventory, Scanner, CameraScan }

@Composable
fun InventurRoot(viewModel: InventoryViewModel) {
    // Splash nur einmal pro Prozessstart zeigen, nicht bei Konfigurationswechseln.
    // rememberSaveable ueberlebt die Activity-Neuerstellung (z. B. bei Drehung),
    // damit der Splash nach dem ersten Anzeigen nicht erneut auftaucht.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
        return
    }

    var screen by rememberSaveable { mutableStateOf(Screen.Inventory) }

    when (screen) {
        Screen.Inventory -> InventoryScreen(
            viewModel = viewModel,
            onOpenScanner = { screen = Screen.Scanner },
            onOpenCameraScan = { screen = Screen.CameraScan },
        )
        Screen.Scanner -> ScannerScreen(
            onBack = { screen = Screen.Inventory },
        )
        Screen.CameraScan -> CameraScanScreen(
            onBack = { screen = Screen.Inventory },
            onBarcode = { viewModel.addScan(it) },
        )
    }
}
