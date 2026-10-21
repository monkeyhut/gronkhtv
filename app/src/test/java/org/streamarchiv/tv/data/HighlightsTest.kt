package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightsTest {
    /** Eine Stunde ruhiger Chat (20 je Minute) mit Ausschlägen an den genannten Minuten. */
    private fun chat(vararg spikes: Pair<Int, Int>) = IntArray(60) { 20 }.also { c -> spikes.forEach { (m, n) -> c[m] = n } }

    @Test fun `gleichmäßiger Chat hat keine Höhepunkte`() {
        assertEquals(emptyList<Int>(), highlightMinutes(IntArray(60) { 20 }))
    }

    @Test fun `ein Ausschlag ist ein Höhepunkt`() {
        assertEquals(listOf(40), highlightMinutes(chat(40 to 80)))
    }

    @Test fun `zwei Ausschläge dicht beieinander zählen einmal, der stärkere gewinnt`() {
        assertEquals(listOf(12), highlightMinutes(chat(10 to 60, 12 to 90), count = 5))
    }

    @Test fun `es werden höchstens so viele wie verlangt, zeitlich sortiert`() {
        val c = chat(5 to 50, 20 to 90, 35 to 70, 50 to 80)
        assertEquals(listOf(20, 50), highlightMinutes(c, count = 2))
    }

    @Test fun `vereinzelte Nachrichten in stillen Minuten zählen nicht`() {
        assertEquals(emptyList<Int>(), highlightMinutes(IntArray(60).also { it[30] = 4 }, count = 5))
    }

    @Test fun `hoch springt zum nächsten Höhepunkt mit Vorlauf`() {
        val marks = listOf(600_000L, 1_800_000L)
        assertEquals(580_000L, nextHighlightTarget(marks, positionMs = 0))
        assertEquals(1_780_000L, nextHighlightTarget(marks, positionMs = 580_000))
        assertNull(nextHighlightTarget(marks, positionMs = 1_780_000))
    }

    @Test fun `knapp vor dem Ziel gelandet springt trotzdem weiter`() {
        assertEquals(1_780_000L, nextHighlightTarget(listOf(600_000L, 1_800_000L), positionMs = 579_000))
    }
}
