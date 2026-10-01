package com.kenfenheuer.inventur.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanFrameParserTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // Aufgezeichnet vom BCST-47 im Expertenmodus.
    private val f1 = b(-63, 14, 52, 48, 49, 53, 48, 49, 54, 48, 48, 48, 49, 48, 48, 10, 91) // 4015016000100
    private val f2 = b(-63, 11, 88, 48, 48, 50, 71, 69, 90, 72, 83, 49, 10, 114) // X002GEZHS1

    @Test fun parsesSingleFrame() = assertEquals(listOf("4015016000100"), ScanFrameParser().feed(f1))

    @Test fun parsesConcatenatedFrames() = assertEquals(listOf("4015016000100", "X002GEZHS1"), ScanFrameParser().feed(f1 + f2))

    @Test fun reassemblesSplitFrame() {
        val p = ScanFrameParser()
        assertEquals(emptyList<String>(), p.feed(f2.copyOfRange(0, 6), 1000))
        assertEquals(listOf("X002GEZHS1"), p.feed(f2.copyOfRange(6, f2.size), 1100))
    }

    @Test fun ignoresBadChecksumAndResyncs() {
        val bad = f1.copyOf().also { it[it.size - 1] = 0 }
        assertEquals(listOf("X002GEZHS1"), ScanFrameParser().feed(bad + f2))
    }

    @Test fun dropsStalePartialData() {
        val p = ScanFrameParser()
        p.feed(f1.copyOfRange(0, 5), 1000)
        assertEquals(listOf("X002GEZHS1"), p.feed(f2, 10_000))
    }
}
