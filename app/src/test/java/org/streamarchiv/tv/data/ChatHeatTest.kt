package org.streamarchiv.tv.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChatHeatTest {
    /** Block i enthält eine Nachricht in Minute 5·i, wie im echten Replay ein Block je 5 Minuten. */
    private fun block(i: Int) = """{"messages":[{"offset":${i * 300 + 10}}]}""".encodeToByteArray()

    private fun count(seconds: Long, fetch: (Int) -> ByteArray?) = runBlocking {
        ChatHeat.count("chat/", seconds) { url -> fetch(url.removePrefix("chat/").toInt()) }
    }

    @Test fun `404 mitten im Viererblock beendet die Zählung, spätere Blöcke zählen nicht`() {
        // Block 7 hinter dem 404 hätte wieder eine Nachricht in Minute 0; sie darf nicht mitzählen.
        val result = count(1_500) { i -> if (i == 6) null else block(if (i == 7) 0 else i) }
        assertEquals(6, result.perMinute.sum())
        assertEquals(1, result.perMinute[0])
        assertTrue(result.complete)
        assertNull(result.failure)
    }

    @Test fun `ohne 404 hört es an der Obergrenze auf und gilt als vollständig`() {
        var calls = 0
        val result = count(600) { i -> calls++; block(i) }
        assertTrue("$calls Abrufe", calls <= 16) // 600 s + 1 h Puffer = 14 Blöcke, auf Viererblöcke aufgerundet
        assertTrue(result.complete)
    }

    @Test fun `ein gescheiterter Block behält die Zählung davor, kommt aber nicht in den Cache`() {
        val result = count(3_000) { i -> if (i == 5) throw IOException("Netz weg") else block(i) }
        assertEquals(5, result.perMinute.sum())
        assertFalse(result.complete)
        assertTrue(result.failure is IOException)
    }

    @Test(expected = IOException::class)
    fun `scheitert schon der erste Block, gibt es keine Zählung`() {
        count(3_000) { throw IOException("Netz weg") }
    }

    @Test fun `endet der Chat vor dem Video, wird beim nächsten Mal neu gezählt`() {
        val result = count(3_000) { i -> if (i < 2) block(i) else null }
        assertEquals(2, result.perMinute.sum())
        assertFalse(result.complete)
        assertNull(result.failure)
    }
}
