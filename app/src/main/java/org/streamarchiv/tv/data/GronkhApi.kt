package org.streamarchiv.tv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

/**
 * Inoffizielle Schnittstelle, die gronkh.tv selbst nutzt (Stand 19.10.2026).
 * Die alte api.gronkh.tv/v1 ist abgeschaltet. Server erlaubt 180 Anfragen pro Minute.
 */
object GronkhApi {
    private const val BASE = "https://backend.gronkh.tv/v3"
    // coerceInputValues: Die Schnittstelle schickt gelegentlich null statt eines Werts (z. B. "boxarts": null bei einem
    // neuen Spiel). Ohne das scheiterte daran die ganze Liste; so gilt dann der Standardwert des Felds.
    internal val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonType = "application/json".toMediaType()
    private val http = OkHttpClient()

    /** 404 der Schnittstelle: das Video (oder die Kategorie) gibt es nicht mehr; anders als ein Netzfehler endgültig. */
    class NotFound(path: String) : IOException("gronkh.tv kennt $path nicht")

    /**
     * Nur dieser Text heißt „Eintrag gelöscht“ (gemessen 22.10.2026). Ein falscher oder umgezogener Pfad antwortet
     * ebenfalls 404, aber mit „The route … could not be found“; der darf nichts aus den eigenen Listen werfen.
     */
    internal fun isMissingModel(body: String): Boolean = "No query results for model" in body

    enum class Discovery(val path: String) { NEWEST("newest"), HOT("hot"), RANDOM("random") }
    enum class Order(val field: String) { NEWEST("created_at"), VIEWS("views") }

    suspend fun discovery(kind: Discovery): List<Video> =
        get<VideoPage>("/videos/discovery/${kind.path}").data

    suspend fun videos(order: Order, page: Int): VideoPage =
        post("/videos/search?page=$page", buildJsonObject {
            put("order", order.field)
            put("dir", "desc")
        }.toString())

    suspend fun search(query: String, page: Int): VideoPage =
        post("/videos/search?page=$page", buildJsonObject { put("query", query) }.toString())

    suspend fun video(id: String): Video = get<VideoDetail>("/videos/$id").data

    /** Streams, in denen [slug] als Kapitel vorkommt, neueste zuerst, 20 je Seite (gemessen 21.10.2026). */
    suspend fun categoryVideos(slug: String, page: Int): VideoPage =
        get("/categories/${URLEncoder.encode(slug, "UTF-8")}/videos?page=$page")

    /** Der nächste Teil per Suche nach „#n+1“; die Suche ist unscharf, deshalb wählt [pickNextEpisode] genau aus. */
    suspend fun nextEpisode(current: Video): Video? {
        val n = current.episode ?: return null
        return pickNextEpisode(current, search("#${n + 1}", 1).data)
    }

    /** Gronkhs eigener Kanal, falls er gerade live ist. Der 24/7-Kanal zählt bewusst nicht. */
    suspend fun gronkhLive(): LiveStream? =
        json.decodeFromJsonElement(SkipBroken(LiveStream.serializer()), get<JsonArray>("/promoted/streams"))
            .firstOrNull { it.login == "gronkh" && it.type == "live" }

    /** Datei vom Video-CDN (Chat-Replay, Vorschauspur, Sprite); null bei 404, das beim Chat-Replay das Ende anzeigt. */
    suspend fun cdnFile(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val target = url.toHttpUrlOrNull() ?: throw IOException("Ungültige CDN-Adresse: $url")
        http.newCall(Request.Builder().url(target).build()).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("CDN antwortet ${response.code} auf ${target.encodedPath}")
                else -> response.body.bytes()
            }
        }
    }

    private suspend inline fun <reified T> get(path: String): T =
        call(Request.Builder().url(BASE + path).build())

    private suspend inline fun <reified T> post(path: String, body: String): T =
        call(Request.Builder().url(BASE + path).post(body.toRequestBody(jsonType)).build())

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val withAccept = request.newBuilder().header("Accept", "application/json").build()
        http.newCall(withAccept).execute().use { response ->
            if (response.code == 404 && isMissingModel(response.body.string())) throw NotFound(request.url.encodedPath)
            if (!response.isSuccessful) {
                throw IOException("gronkh.tv antwortet ${response.code} auf ${request.url.encodedPath}")
            }
            json.decodeFromString<T>(response.body.string())
        }
    }
}

/**
 * Liest eine Liste Eintrag für Eintrag: ein einzelnes kaputtes Video (null in einem Pflichtfeld) fällt mit Hinweis im
 * Log heraus, statt die ganze Seite scheitern zu lassen.
 */
internal class SkipBroken<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val list = ListSerializer(element)
    override val descriptor = list.descriptor
    override fun serialize(encoder: Encoder, value: List<T>) = list.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<T> {
        val json = (decoder as JsonDecoder).json
        return decoder.decodeJsonElement().jsonArray.mapNotNull { item ->
            try {
                json.decodeFromJsonElement(element, item)
            } catch (e: SerializationException) {
                Log.w("GronkhApi", "Eintrag ${(item as? JsonObject)?.get("id")} übersprungen", e)
                null
            }
        }
    }
}

internal object VideoList : KSerializer<List<Video>> by SkipBroken(Video.serializer())
