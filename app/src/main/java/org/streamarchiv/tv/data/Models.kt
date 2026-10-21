package org.streamarchiv.tv.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Die Schnittstelle liefert dasselbe Video manchmal zweimal in einer Antwort; doppelte Schlüssel lassen eine
 * LazyRow abstürzen. Deshalb läuft jede Liste vor der Anzeige hier durch.
 */
fun List<Video>.distinctById(): List<Video> = distinctBy { it.id }

@Serializable
data class Video(
    val id: String,
    val title: String,
    val episode: Int? = null,
    val views: Long = 0,
    @SerialName("created_at") val createdAt: String,
    val meta: Meta = Meta(),
    val urls: Urls,
    val tags: List<Tag> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

/**
 * Abschnitt eines Streams, in Sekunden ab Videobeginn. [category] ist das Spiel oder Thema („Just Chatting“) und fehlt
 * selten ganz; [title] steht meist nicht drin (gemessen 21.10.2026).
 */
@Serializable
data class Chapter(
    @SerialName("start_offset") val start: Long,
    @SerialName("end_offset") val end: Long,
    val title: String? = null,
    val category: Category? = null,
)

/** Spiel oder Thema wie auf Twitch; [slug] führt zu categories/<slug>/videos. Box-Art in xs, sm, md, lg, xl. */
@Serializable
data class Category(val title: String, val slug: String, val urls: CategoryUrls = CategoryUrls())

@Serializable
data class CategoryUrls(
    // Wie thumbnail_urls: eine Größe kann null sein; nachgeschlagen gleicht sie einer fehlenden, die Liste scheitert nicht.
    val boxarts: Map<String, String?> = emptyMap(),
)

/** Schlagwort der Redaktion, etwa „FREiAB18“ oder „H.O.R.D.E.“; fehlt bei älteren Videos oft. */
@Serializable
data class Tag(val title: String)

@Serializable
data class Meta(val duration: Long = 0)

/** chat ist der Ordner des Chat-Replays, vtt die Vorschauspur, sprites das Bild mit allen Vorschaubildern. */
@Serializable
data class Urls(
    val playlist: String,
    val thumbnail: String? = null,
    val chat: String? = null,
    val vtt: String? = null,
    val sprites: String? = null,
)

@Serializable
data class VideoDetail(val data: Video)

/** Ein laufender Twitch-Stream aus promoted/streams; dort steht auch der 24/7-Kanal „gronkhtv“. */
@Serializable
data class LiveStream(
    @SerialName("user_login") val login: String,
    val type: String,
    val title: String,
    @SerialName("game_name") val game: String = "",
    @SerialName("viewer_count") val viewers: Int = 0,
    @SerialName("thumbnail_urls") val thumbnails: Map<String, String?> = emptyMap(),
)

/** Antwort von discovery (ohne meta) und search (mit Seitenangaben). */
@Serializable
data class VideoPage(@Serializable(with = VideoList::class) val data: List<Video>, val meta: PageMeta? = null)

@Serializable
data class PageMeta(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)
