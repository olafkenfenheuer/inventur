package com.kenfenheuer.inventur

import com.kenfenheuer.inventur.ui.ScannerBackup
import com.kenfenheuer.inventur.ui.SettingRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerBackupTest {
    @Test
    fun differencesSkipsConnectionModeAndUsesCurrentArea() {
        val saved = listOf(
            SettingRow("3", "volume", "2"),
            SettingRow("1", "bt_mode_low", "0"),
            SettingRow("5", "same", "1"),
            SettingRow("9", "unknown_now", "1"),
        )
        val current = listOf(
            SettingRow("3", "volume", "4"),
            SettingRow("1", "bt_mode_low", "1"),
            SettingRow("5", "same", "1"),
        )
        assertEquals(listOf(SettingRow("3", "volume", "2")), ScannerBackup.differences(saved, current))
    }
}
