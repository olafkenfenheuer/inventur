package com.kenfenheuer.inventur.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

enum class Screen { Inventory, Scanner }

@Composable
fun InventurRoot(viewModel: InventoryViewModel) {
    // Splash nur einmal pro Prozessstart zeigen, nicht bei Konfigurationswechseln.
    var showSplash by remember { mutableStateOf(true) }
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
        )
        Screen.Scanner -> ScannerScreen(
            onBack = { screen = Screen.Inventory },
        )
    }
}
