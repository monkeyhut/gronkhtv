package org.streamarchiv.tv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.streamarchiv.tv.data.GronkhApi
import org.streamarchiv.tv.data.Urls
import org.streamarchiv.tv.data.Video
import java.io.IOException

/** Hält fest, dass nur ein echtes „Video gelöscht“ Einträge aus Weiterschauen und Merkliste nimmt (22.10.2026). */
class IdsToForgetTest {
    private val loaded = Result.success(Video("ok", "t", createdAt = "2026-10-22T18:00:00+02:00", urls = Urls("p")))
    private val deleted = Result.failure<Video>(GronkhApi.NotFound("/v3/videos/weg"))
    // So kommt ein 404 ohne „No query results“ (falscher Pfad) aus GronkhApi an: als gewöhnlicher Fehler.
    private val routeMissing = Result.failure<Video>(IOException("gronkh.tv antwortet 404 auf /v3/videos/x"))
    private val network = Result.failure<Video>(IOException("Netz weg"))
    private val tooMany = Result.failure<Video>(IOException("gronkh.tv antwortet 429 auf /v3/videos/x"))
    private val serverError = Result.failure<Video>(IOException("gronkh.tv antwortet 500 auf /v3/videos/x"))

    @Test fun `ein gelöschtes Video fliegt raus, wenn ein anderes geladen wurde`() {
        assertEquals(setOf("weg"), idsToForget(mapOf("ok" to loaded, "weg" to deleted)))
    }

    @Test fun `meldet die Schnittstelle alles als gelöscht, bleibt alles stehen`() {
        assertEquals(emptySet<String>(), idsToForget((1..30).associate { "v$it" to deleted }))
    }

    @Test fun `Routing-404, Netzfehler, 429 und 500 werfen nichts raus`() {
        val results = mapOf("ok" to loaded, "a" to routeMissing, "b" to network, "c" to tooMany, "d" to serverError)
        assertEquals(emptySet<String>(), idsToForget(results))
    }

    @Test fun `gemischt fliegen nur die gelöschten`() {
        val results = mapOf("ok" to loaded, "weg1" to deleted, "a" to routeMissing, "weg2" to deleted, "b" to network)
        assertEquals(setOf("weg1", "weg2"), idsToForget(results))
    }

    @Test fun `ohne Anfrage gibt es nichts zu löschen`() {
        assertEquals(emptySet<String>(), idsToForget(emptyMap()))
    }

    @Test fun `nur der Text des gelöschten Eintrags zählt als gelöscht`() {
        assertTrue(GronkhApi.isMissingModel("""{"message":"No query results for model [App\\Models\\Video] 1234"}"""))
        assertFalse(GronkhApi.isMissingModel("""{"message":"The route v3/videos/1234 could not be found."}"""))
        assertFalse(GronkhApi.isMissingModel(""))
    }
}
