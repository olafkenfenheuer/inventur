package com.kenfenheuer.inventur

import com.kenfenheuer.inventur.ui.InventoryViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerCommandTest {
    @Test
    fun recognizesSetupBarcodes() {
        assertTrue(InventoryViewModel.isScannerCommand("/*SetFun00*/"))
        assertTrue(InventoryViewModel.isScannerCommand("/*EnterSet*/"))
        assertTrue(InventoryViewModel.isScannerCommand("/*SwhToHID*/"))
    }

    @Test
    fun keepsNormalBarcodes() {
        assertFalse(InventoryViewModel.isScannerCommand("INV00NC0207"))
        assertFalse(InventoryViewModel.isScannerCommand("4015016000100"))
        assertFalse(InventoryViewModel.isScannerCommand("/*"))
    }
}
