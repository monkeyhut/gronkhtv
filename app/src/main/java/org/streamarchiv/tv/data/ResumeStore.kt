package org.streamarchiv.tv.data

import android.content.Context

/** Merkt sich je Video, wo und wann zuletzt aufgehört wurde. Streams dauern oft 6 Stunden und mehr. */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    // Eigene Datei für die Zeit, damit die Positionen aus älteren Versionen ohne Umbau weiter gelten.
    private val watchedAt = context.getSharedPreferences("resume_time", Context.MODE_PRIVATE)

    fun position(videoId: String): Long = prefs.getLong(videoId, 0L)

    fun save(videoId: String, positionMs: Long, durationMs: Long) {
        // Die letzte Minute gilt als zu Ende geschaut, die ersten 30 Sekunden als nicht angefangen.
        val finished = durationMs > 0 && positionMs > durationMs - 60_000
        if (finished || positionMs < 30_000) {
            prefs.edit().remove(videoId).apply()
            watchedAt.edit().remove(videoId).apply()
        } else {
            prefs.edit().putLong(videoId, positionMs).apply()
            watchedAt.edit().putLong(videoId, System.currentTimeMillis()).apply()
        }
    }

    /** Angefangene Videos, zuletzt geschaute zuerst; Einträge aus älteren Versionen ohne Zeit stehen hinten. */
    fun startedIds(): List<String> = prefs.all.keys.sortedByDescending { watchedAt.getLong(it, 0L) }
}
