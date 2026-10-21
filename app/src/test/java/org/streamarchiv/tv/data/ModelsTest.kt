package org.streamarchiv.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Hält fest, dass einzelne null-Werte der Schnittstelle nicht die ganze Liste scheitern lassen (21.10.2026). */
class ModelsTest {
    @Test fun `Spiel ohne Box-Art lässt die Liste nicht scheitern`() {
        val page = GronkhApi.json.decodeFromString<VideoPage>(
            """{"data":[{"id":"v","title":"t","created_at":"2026-10-21T18:00:00+02:00","urls":{"playlist":"p"},
               "chapters":[{"start_offset":0,"end_offset":60,"category":{"title":"Nivalis Nights","slug":"n",
               "urls":{"boxarts":null}}}]}]}""",
        )
        assertEquals(emptyMap<String, String>(), page.data.single().chapters.single().category!!.urls.boxarts)
    }

    @Test fun `ein kaputtes Video lässt die Seite nicht scheitern, das gute kommt an`() {
        val page = GronkhApi.json.decodeFromString<VideoPage>(
            """{"data":[{"id":"kaputt","title":null,"created_at":"2026-10-21T18:00:00+02:00","urls":{"playlist":"p"}},
               {"id":"gut","title":"t","created_at":"2026-10-21T18:00:00+02:00","urls":{"playlist":"p"},
               "chapters":[{"start_offset":0,"end_offset":60,"category":{"title":"Spiel","slug":"s",
               "urls":{"boxarts":{"xl":null,"lg":"bild"}}}}]}],"meta":{"current_page":1,"last_page":3}}""",
        )
        val video = page.data.single()
        assertEquals("gut", video.id)
        assertEquals(3, page.meta!!.lastPage)
        val boxarts = video.chapters.single().category!!.urls.boxarts
        assertEquals(null, boxarts["xl"])
        assertEquals("bild", boxarts["lg"])
    }

    @Test fun `Kapitel ohne Offset nimmt nur sein Video aus der Liste`() {
        val page = GronkhApi.json.decodeFromString<VideoPage>(
            """{"data":[{"id":"a","title":"t","created_at":"c","urls":{"playlist":"p"},"chapters":[{"start_offset":null,"end_offset":60}]},
               {"id":"b","title":"t","created_at":"c","urls":{"playlist":null}},
               {"id":"c","title":"t","created_at":"c","urls":{"playlist":"p"}}]}""",
        )
        assertEquals(listOf("c"), page.data.map { it.id })
    }
}
