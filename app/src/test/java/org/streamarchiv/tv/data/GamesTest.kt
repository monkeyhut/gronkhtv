package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Kapitelfolgen echter Streams vom 21.10.2026, neueste zuerst. */
class GamesTest {
    private fun video(id: String, vararg chapters: Pair<String?, Long>): Video {
        var at = 0L
        return Video(
            id = id, title = id, createdAt = "2026-10-21T18:00:00+02:00", urls = Urls("p"),
            chapters = chapters.map { (game, seconds) ->
                Chapter(at, at + seconds, category = game?.let { Category(it, it.lowercase()) }).also { at += seconds }
            },
        )
    }

    private val streams = listOf(
        video("1126", "Just Chatting" to 15331, "Big Ambitions" to 20249, "Just Chatting" to 679),
        video("1124", "Just Chatting" to 5880, "Nivalis Nights" to 35880, "Just Chatting" to 596),
        video("1123", "Just Chatting" to 9540, "End of Abyss" to 8220, "Nivalis Nights" to 24899, "Nivalis Nights" to 600),
        video("1098", null to 1),
        video("1090", "Special Events" to 7200, "Big Ambitions" to 9000),
    )

    @Test fun `zählt Streams, nicht Kapitel, und summiert die Zeit`() {
        val nivalis = rankCategories(streams).single { it.category.title == "Nivalis Nights" }
        assertEquals(2, nivalis.streams)
        assertEquals(35880L + 24899 + 600, nivalis.seconds)
    }

    @Test fun `häufigste zuerst, bei Gleichstand die zuletzt gespielte`() {
        assertEquals(
            listOf("Just Chatting", "Big Ambitions", "Nivalis Nights", "End of Abyss", "Special Events"),
            rankCategories(streams).map { it.category.title },
        )
    }

    @Test fun `Kacheln zeigen Spiele vor Themen, ohne Just Chatting`() {
        assertEquals(
            listOf("Big Ambitions", "Nivalis Nights", "End of Abyss", "Special Events"),
            gameShelf(streams).map { it.category.title },
        )
    }

    @Test fun `aktuelle Spiele zählen nur im Zeitfenster und ohne Themen`() {
        assertEquals(listOf("Big Ambitions", "Nivalis Nights"), currentGames(streams, window = 2).map { it.title })
        assertEquals(listOf("Big Ambitions"), currentGames(streams, count = 1).map { it.title })
    }

    @Test fun `Sprungziel ist das erste Kapitel des Spiels`() {
        assertEquals(9540L + 8220, streams[2].chapterOf("nivalis nights")?.start)
    }
}
