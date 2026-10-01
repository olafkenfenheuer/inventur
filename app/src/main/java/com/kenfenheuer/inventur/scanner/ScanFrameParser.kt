package com.kenfenheuer.inventur.scanner

/**
 * Wertet die Bluetooth-Nachrichten des Scanners im Expertenmodus aus:
 * `C1 | Laenge | Barcode (ASCII) + 0A | Pruefsumme` (Summe aller vorherigen Bytes mod 256).
 * Nachrichten koennen in mehrere Pakete zerlegt oder aneinandergehaengt ankommen.
 */
class ScanFrameParser {
    private var buf = ByteArray(0)
    private var lastFeed = 0L

    @Synchronized
    fun feed(data: ByteArray, now: Long = System.currentTimeMillis()): List<String> {
        if (now - lastFeed > STALE_MS) buf = ByteArray(0)
        lastFeed = now
        buf += data
        val out = mutableListOf<String>()
        while (true) {
            val start = buf.indexOfFirst { it == HEADER }
            if (start < 0) { buf = ByteArray(0); break }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < 2) break
            val len = buf[1].toInt() and 0xff
            val total = 2 + len + 1
            if (buf.size < total) break
            var sum = 0
            for (i in 0 until total - 1) sum += buf[i].toInt() and 0xff
            if ((sum and 0xff) == (buf[total - 1].toInt() and 0xff)) {
                val text = String(buf, 2, len, Charsets.UTF_8).trimEnd('\n', '\r')
                if (text.isNotEmpty()) out.add(text)
                buf = buf.copyOfRange(total, buf.size)
            } else {
                buf = buf.copyOfRange(1, buf.size) // kein gueltiger Rahmen: weitersuchen
            }
        }
        return out
    }

    private companion object {
        const val HEADER: Byte = 0xC1.toByte()
        const val STALE_MS = 2000L
    }
}
