package org.streamarchiv.tv.data

import android.content.Context
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Tatsächlich gespielte Sekunden je Monat und je Video im Monat, dazu der Titel je Video. Grundlage für den
 * Rückblick auf der Startseite („so viel geschaut, am meisten …“).
 */
class WatchStats(context: Context) {
    private val prefs = context.getSharedPreferences("watch_stats", Context.MODE_PRIVATE)

    fun add(video: Video, seconds: Long, nowMs: Long = System.currentTimeMillis()) {
        if (seconds <= 0) return
        val month = monthOf(nowMs)
        val monthKey = MONTH + month
        val videoKey = "$VIDEO$month:${video.id}"
        prefs.edit()
            .putLong(monthKey, prefs.getLong(monthKey, 0) + seconds)
            .putLong(videoKey, prefs.getLong(videoKey, 0) + seconds)
            .putString(TITLE + video.id, video.title.take(TITLE_MAX))
            .apply()
    }

    /** Summen für einen Zeitraum: „2026“ für ein Jahr, „2026-10“ für einen Monat. */
    fun summary(period: String): WatchSummary = summarize(prefs.all, period)

    companion object {
        internal const val MONTH = "m:"
        internal const val VIDEO = "v:"
        internal const val TITLE = "t:"
        private const val TITLE_MAX = 80
    }
}

data class WatchedVideo(val id: String, val title: String, val seconds: Long)

/** [videos] absteigend nach Sekunden, das meistgeschaute zuerst. */
data class WatchSummary(val seconds: Long, val months: Map<String, Long>, val videos: List<WatchedVideo>)

fun monthOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(zone)).toString()

/** Rein, damit ein Test die Zählung ohne Gerät prüft. Schlüssel: m:<Monat>, v:<Monat>:<Video-ID>, t:<Video-ID>. */
fun summarize(all: Map<String, *>, period: String): WatchSummary {
    val months = all.mapNotNull { (key, value) ->
        val month = key.removePrefix(WatchStats.MONTH)
        if (key.startsWith(WatchStats.MONTH) && month.startsWith(period) && value is Long) month to value else null
    }.toMap().toSortedMap()
    val videos = all.mapNotNull { (key, value) ->
        if (!key.startsWith(WatchStats.VIDEO) || value !is Long) return@mapNotNull null
        val (month, id) = key.removePrefix(WatchStats.VIDEO).split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        if (month.startsWith(period)) id to value else null
    }.groupBy({ it.first }, { it.second })
        .map { (id, parts) -> WatchedVideo(id, all[WatchStats.TITLE + id] as? String ?: "", parts.sum()) }
        .sortedByDescending { it.seconds }
    return WatchSummary(months.values.sum(), months, videos)
}
