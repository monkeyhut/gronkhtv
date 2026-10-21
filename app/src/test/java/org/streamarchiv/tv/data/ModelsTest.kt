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
}
