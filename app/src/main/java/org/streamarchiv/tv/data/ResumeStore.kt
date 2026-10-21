package org.streamarchiv.tv.data

import android.content.Context

/** Merkt sich je Video, wo und wann zuletzt aufgehört wurde. Streams dauern oft 6 Stunden und mehr. */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("resume", Context.MODE_PRIVATE)
    // Eigene Datei für die Zeit, damit die Positionen aus älteren Versionen ohne Umbau weiter gelten.
    private val watchedAt = context.getSharedPreferences("resume_time", Context.MODE_PRIVATE)

    fun position(videoId: String): Long = prefs.getLong(videoId, 0L)

    fun save(videoId: String, positionMs: Long, durationMs: Long) {
        when (resumeAction(positionMs, durationMs)) {
            ResumeAction.CLEAR -> remove(videoId)
            ResumeAction.KEEP -> Unit
            ResumeAction.SAVE -> {
                prefs.edit().putLong(videoId, positionMs).apply()
                watchedAt.edit().putLong(videoId, System.currentTimeMillis()).apply()
            }
        }
    }

    fun remove(videoId: String) {
        prefs.edit().remove(videoId).apply()
        watchedAt.edit().remove(videoId).apply()
    }

    /** Angefangene Videos, zuletzt geschaute zuerst. Räumt dabei auf, was [staleResumeIds] nennt. */
    fun startedIds(nowMs: Long = System.currentTimeMillis()): List<String> {
        val times = watchedAt.all.mapNotNull { (id, at) -> (at as? Long)?.let { id to it } }.toMap()
        staleResumeIds(prefs.all.keys, times, nowMs).forEach(::remove)
        return prefs.all.keys.sortedByDescending { times[it] ?: 0L }
    }
}

private const val MAX_AGE_MS = 180 * 24 * 3_600_000L

/**
 * Einträge, die beim Laden wegfallen: seit [maxAgeMs] nicht geschaut, oder nur halb vorhanden. Seit 9.0 schreibt jedes
 * Speichern Stelle und Zeit; fehlt eins von beiden, ist ein Schreiben abgebrochen, und der Rest wüchse nur weiter.
 */
fun staleResumeIds(positions: Set<String>, watchedAt: Map<String, Long>, nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Set<String> =
    (positions + watchedAt.keys).filterTo(HashSet()) { id -> id !in positions || (watchedAt[id] ?: 0L) < nowMs - maxAgeMs }

enum class ResumeAction { SAVE, KEEP, CLEAR }

/**
 * Die letzte Minute gilt als zu Ende geschaut, dann fällt das Video aus Weiterschauen. Die ersten 30 Sekunden ändern
 * nichts: wer bei Stunde 5 stand, kurz von vorn reinschaut und wieder geht, behält seine Stelle.
 */
fun resumeAction(positionMs: Long, durationMs: Long): ResumeAction = when {
    durationMs > 0 && positionMs > durationMs - 60_000 -> ResumeAction.CLEAR
    positionMs < 30_000 -> ResumeAction.KEEP
    else -> ResumeAction.SAVE
}
