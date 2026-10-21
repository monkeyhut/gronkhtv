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
        val monthKey = "$MONTH$month"
        val videoKey = "$VIDEO$month:${video.id}"
        val keys = prefs.all.keys
        prefs.edit().apply {
            staleStatKeys(keys, month, video.id).forEach(::remove)
            putLong(monthKey, prefs.getLong(monthKey, 0) + seconds)
            putLong(videoKey, prefs.getLong(videoKey, 0) + seconds)
            if (TITLE + video.id !in keys) putString(TITLE + video.id, video.title.take(TITLE_MAX))
        }.apply()
    }

    fun summary(month: YearMonth): WatchSummary = summarize(prefs.all, month)

    companion object {
        internal const val MONTH = "m:"
        internal const val VIDEO = "v:"
        internal const val TITLE = "t:"
        private const val TITLE_MAX = 80
    }
}

data class WatchedVideo(val id: String, val title: String, val seconds: Long)

/** [videos] absteigend nach Sekunden, das meistgeschaute zuerst. */
data class WatchSummary(val month: YearMonth, val seconds: Long, val videos: List<WatchedVideo>)

fun monthOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(zone))

/**
 * Was beim Buchen in [month] wegkann, damit die Datei nicht mit jedem geschauten Video wächst: Videozeiten anderer
 * Monate (der Rückblick zeigt nur den laufenden) und Titel von Videos ohne Zeit in [month] außer [playing].
 * Die Monatssummen bleiben, sie sind je Monat nur eine Zahl.
 */
fun staleStatKeys(keys: Set<String>, month: YearMonth, playing: String): List<String> {
    val current = "${WatchStats.VIDEO}$month:"
    val titled = keys.filter { it.startsWith(current) }.mapTo(hashSetOf(playing)) { it.removePrefix(current) }
    return keys.filter {
        (it.startsWith(WatchStats.VIDEO) && !it.startsWith(current)) ||
            (it.startsWith(WatchStats.TITLE) && it.removePrefix(WatchStats.TITLE) !in titled)
    }
}

/** Rein, damit ein Test die Zählung ohne Gerät prüft. Schlüssel: m:<Monat>, v:<Monat>:<Video-ID>, t:<Video-ID>. */
fun summarize(all: Map<String, *>, month: YearMonth): WatchSummary {
    val prefix = "${WatchStats.VIDEO}$month:"
    val videos = all.mapNotNull { (key, value) ->
        if (!key.startsWith(prefix) || value !is Long) return@mapNotNull null
        val id = key.removePrefix(prefix)
        WatchedVideo(id, all[WatchStats.TITLE + id] as? String ?: "", value)
    }.sortedByDescending { it.seconds }
    return WatchSummary(month, all["${WatchStats.MONTH}$month"] as? Long ?: 0L, videos)
}
