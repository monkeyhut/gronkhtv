package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewTrackTest {
    /** Auszug aus der echten Spur von #1124 (21.10.2026). */
    private val vtt = """
        WEBVTT

        00:00:00.000 --> 00:01:01.000
        ad66f668_sprite.jpg#xywh=0,0,160,90

        00:01:01.000 --> 00:02:02.000
        ad66f668_sprite.jpg#xywh=160,0,160,90

        11:45:34.000 --> 11:46:35.000
        ad66f668_sprite.jpg#xywh=640,6210,160,90
    """.trimIndent()

    @Test fun `liest Startzeit und Ausschnitt`() {
        val cues = parsePreviewVtt(vtt)
        assertEquals(3, cues.size)
        assertEquals(PreviewCue(61_000, 160, 0, 160, 90), cues[1])
        assertEquals(42_334_000L, cues[2].startMs)
        assertEquals(6210, cues[2].y)
    }

    @Test fun `Zeit ohne Stunden geht auch`() {
        val cues = parsePreviewVtt("WEBVTT\n\n01:05.500 --> 02:00.000\nx.jpg#xywh=1,2,3,4\n")
        assertEquals(PreviewCue(65_500, 1, 2, 3, 4), cues.single())
    }

    @Test fun `findet das Bild zur Stelle`() {
        val cues = parsePreviewVtt(vtt)
        assertEquals(0, cues.at(60_999)?.x)
        assertEquals(160, cues.at(61_000)?.x)
        assertEquals(640, cues.at(50_000_000)?.x)
        assertNull(emptyList<PreviewCue>().at(0))
    }

    @Test fun `Einträge ohne Ausschnitt fallen weg`() {
        assertEquals(emptyList<PreviewCue>(), parsePreviewVtt("WEBVTT\n\n00:00:00.000 --> 00:01:00.000\nnur Text\n"))
    }
}
