package com.kenfenheuer.inventur.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbCacheReaderTest {
    private fun rep(mod: Int, key: Int) = byteArrayOf(1, mod.toByte(), 0, key.toByte(), 0, 0, 0, 0, 0)

    @Test
    fun decodesShiftedLettersDigitsAndEnter() {
        // Aufgezeichnet vom BCST-47-USB: "INV0" (I, N, V mit Shift, 0 ohne) + Enter, danach ein zweiter Code.
        val reports = listOf(
            rep(2, 0x0c), rep(2, 0x11), rep(2, 0x19), rep(0, 0x27), rep(0, 0x28),
            rep(2, 0x16), rep(2, 0x06), rep(0, 0x1e), rep(0, 0x28),
            byteArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0), // Loslassen
        )
        assertEquals(listOf("INV0", "SC1"), UsbCacheReader.decodeKeyboardReports(reports))
    }

    @Test
    fun handlesReportsWithoutReportId() {
        val r = byteArrayOf(2, 0, 0x04, 0, 0, 0, 0, 0)
        assertEquals(listOf("A"), UsbCacheReader.decodeKeyboardReports(listOf(r)))
    }
}
