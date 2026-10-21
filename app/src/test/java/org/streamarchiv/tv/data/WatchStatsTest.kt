package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class WatchStatsTest {
    private val stored = mapOf(
        "m:2026-09" to 3_600L,
        "m:2026-10" to 9_000L,
        "m:2025-12" to 500L,
        "v:2026-09:a" to 3_600L,
        "v:2026-10:a" to 1_000L,
        "v:2026-10:b" to 8_000L,
        "v:2025-12:c" to 500L,
        "t:a" to "Horde #1127",
        "t:b" to "Streamsonntag #1126",
    )

    @Test fun `Jahr zählt alle Monate und sortiert das meistgeschaute nach vorn`() {
        val year = summarize(stored, "2026")
        assertEquals(12_600L, year.seconds)
        assertEquals(listOf("2026-09", "2026-10"), year.months.keys.toList())
        assertEquals(listOf(WatchedVideo("b", "Streamsonntag #1126", 8_000), WatchedVideo("a", "Horde #1127", 4_600)), year.videos)
    }

    @Test fun `Monat zählt nur sich selbst`() {
        val october = summarize(stored, "2026-10")
        assertEquals(9_000L, october.seconds)
        assertEquals(1_000L, october.videos.single { it.id == "a" }.seconds)
    }

    @Test fun `Video ohne Titel bleibt mit leerem Titel drin`() {
        assertEquals(WatchedVideo("c", "", 500), summarize(stored, "2025").videos.single())
    }

    @Test fun `Monat richtet sich nach der Ortszeit`() {
        val silvesterNacht = 1_798_758_000_000L // 2026-12-31T23:00:00Z, in Berlin schon 2027
        assertEquals("2027-01", monthOf(silvesterNacht, ZoneId.of("Europe/Berlin")))
        assertEquals("2026-12", monthOf(silvesterNacht, ZoneId.of("UTC")))
    }
}
