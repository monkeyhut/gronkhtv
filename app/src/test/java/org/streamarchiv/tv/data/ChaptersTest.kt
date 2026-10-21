package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Kapitel von #1123 vom 21.10.2026 (Sekunden), samt der Lücke von 1 s zwischen zwei Kapiteln. */
class ChaptersTest {
    private fun chapter(start: Long, end: Long, game: String?) =
        Chapter(start, end, category = game?.let { Category(it, it.lowercase()) })

    private val chapters = listOf(
        chapter(0, 9540, "Just Chatting"),
        chapter(9541, 17761, "End of Abyss"),
        chapter(17762, 18361, "Just Chatting"),
        chapter(18362, 43261, "Nivalis Nights"),
    )

    @Test fun `Kapitel an der Stelle`() {
        assertEquals(0, chapterAt(chapters, 0))
        assertEquals(1, chapterAt(chapters, 9_541_000))
        assertEquals(3, chapterAt(chapters, 50_000_000))
    }

    @Test fun `Lücke zwischen zwei Kapiteln gehört zum vorigen`() {
        assertEquals(0, chapterAt(chapters, 9_540_500))
    }

    @Test fun `vor dem ersten Kapitel und ohne Kapitel gibt es keins`() {
        assertNull(chapterAt(listOf(chapter(60, 120, "Spiel")), 30_000))
        assertNull(chapterAt(emptyList(), 0))
    }

    @Test fun `Reste ohne Spiel und Titel fallen raus, der Rest wird geordnet`() {
        val video = Video(
            id = "v", title = "t", createdAt = "2026-10-21T18:00:00+02:00", urls = Urls("p"),
            chapters = listOf(chapter(100, 200, "B"), chapter(0, 1, null), chapter(0, 100, "A")),
        )
        assertEquals(listOf("A", "B"), video.playerChapters().map { it.name })
    }

    @Test fun `gespeicherte Stelle im angesprungenen Kapitel gewinnt`() {
        assertEquals(20_000_000, startPosition(chapters, requestedMs = 18_362_000, savedMs = 20_000_000))
    }

    @Test fun `gespeicherte Stelle in einem anderen Kapitel zählt nicht`() {
        assertEquals(18_362_000, startPosition(chapters, requestedMs = 18_362_000, savedMs = 5_000_000))
        assertEquals(18_362_000, startPosition(chapters, requestedMs = 18_362_000, savedMs = 0))
    }

    @Test fun `ohne Sprungziel gilt die gespeicherte Stelle`() {
        assertEquals(5_000_000, startPosition(chapters, requestedMs = null, savedMs = 5_000_000))
    }
}
