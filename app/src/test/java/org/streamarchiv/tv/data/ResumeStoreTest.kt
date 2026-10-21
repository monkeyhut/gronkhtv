package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeStoreTest {
    private val sevenHours = 7 * 3_600_000L

    @Test fun `5 h gespeichert, nach 20 s verlassen, die Stelle bleibt`() {
        assertEquals(ResumeAction.KEEP, resumeAction(20_000, sevenHours))
    }

    @Test fun `mittendrin wird gespeichert`() {
        assertEquals(ResumeAction.SAVE, resumeAction(5 * 3_600_000L, sevenHours))
    }

    @Test fun `in der letzten Minute gilt es als zu Ende`() {
        assertEquals(ResumeAction.CLEAR, resumeAction(sevenHours - 30_000, sevenHours))
    }

    @Test fun `ein kurzes Video zu Ende geschaut wird gelöscht, auch unter 30 s`() {
        assertEquals(ResumeAction.CLEAR, resumeAction(25_000, 50_000))
    }

    @Test fun `beim Laden fallen alte und halbe Einträge weg`() {
        val day = 24 * 3_600_000L
        val now = 400 * day
        val stale = staleResumeIds(
            positions = setOf("frisch", "alt", "ohneZeit"),
            watchedAt = mapOf("frisch" to now - 10 * day, "alt" to now - 181 * day, "ohneStelle" to now),
            nowMs = now,
        )
        assertEquals(setOf("alt", "ohneZeit", "ohneStelle"), stale)
    }

    @Test fun `ohne bekannte Länge wird nur gespeichert, nie gelöscht`() {
        assertEquals(ResumeAction.SAVE, resumeAction(sevenHours, Long.MIN_VALUE + 1))
        assertEquals(ResumeAction.KEEP, resumeAction(0, Long.MIN_VALUE + 1))
    }
}
