package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {
    private fun video(id: String, episode: Int?) =
        Video(id = id, title = "Stream $id", episode = episode, createdAt = "2026-10-21T18:00:00+02:00", urls = Urls("p"))

    @Test fun `nimmt genau die nächste Folge, nicht Titeltreffer`() {
        val hits = listOf(video("a", 786), video("b", 1125), video("c", 1124))
        assertEquals("b", pickNextEpisode(video("x", 1124), hits)?.id)
    }

    @Test fun `ohne nächste Folge kommt nichts`() {
        assertNull(pickNextEpisode(video("x", 2), listOf(video("a", 13), video("b", 1))))
    }

    @Test fun `ohne Folgennummer kommt nichts`() {
        assertNull(pickNextEpisode(video("x", null), listOf(video("a", 1))))
    }
}
