package com.kenfenheuer.inventur.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Variante "classic": keine zusaetzlichen Bildschirme (Konto & Sync gibt es nur in "pro"). */
@Composable
fun FeatureScreens(screen: Screen, viewModel: InventoryViewModel, go: (Screen) -> Unit) {
    LaunchedEffect(screen) { go(Screen.Inventory) }
}
