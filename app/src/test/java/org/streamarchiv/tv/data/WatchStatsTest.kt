package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class WatchStatsTest {
    private val october = YearMonth.of(2026, 10)
    private val stored = mapOf(
        "m:2026-09" to 3_600L,
        "m:2026-10" to 9_000L,
        "v:2026-09:a" to 3_600L,
        "v:2026-10:a" to 1_000L,
        "v:2026-10:b" to 8_000L,
        "t:a" to "Horde #1127",
        "t:b" to "Streamsonntag #1126",
    )

    @Test fun `Monat zählt nur sich selbst und sortiert das meistgeschaute nach vorn`() {
        val summary = summarize(stored, october)
        assertEquals(october, summary.month)
        assertEquals(9_000L, summary.seconds)
        assertEquals(listOf(WatchedVideo("b", "Streamsonntag #1126", 8_000), WatchedVideo("a", "Horde #1127", 1_000)), summary.videos)
    }

    @Test fun `Video ohne Titel bleibt mit leerem Titel drin`() {
        val summary = summarize(mapOf("m:2025-12" to 500L, "v:2025-12:c" to 500L), YearMonth.of(2025, 12))
        assertEquals(WatchedVideo("c", "", 500), summary.videos.single())
    }

    @Test fun `beim Buchen fallen fremde Monate und verwaiste Titel weg, die Summen bleiben`() {
        val keys = stored.keys + setOf("v:2026-08:c", "t:c", "t:d")
        assertEquals(setOf("v:2026-09:a", "v:2026-08:c", "t:c"), staleStatKeys(keys, october, playing = "d").toSet())
    }

    @Test fun `Monat richtet sich nach der Ortszeit`() {
        val silvesterNacht = 1_798_758_000_000L // 2026-12-31T23:00:00Z, in Berlin schon 2027
        assertEquals(YearMonth.of(2027, 1), monthOf(silvesterNacht, ZoneId.of("Europe/Berlin")))
        assertEquals(YearMonth.of(2026, 12), monthOf(silvesterNacht, ZoneId.of("UTC")))
    }
}
