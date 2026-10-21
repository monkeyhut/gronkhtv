package org.streamarchiv.tv.data

import android.content.Context

/** „Später ansehen“: nur die IDs mit dem Zeitpunkt des Merkens; Titel und Bild kommen frisch aus der Schnittstelle. */
class WatchlistStore(context: Context) {
    private val prefs = context.getSharedPreferences("watchlist", Context.MODE_PRIVATE)

    /** Merkt [videoId] oder nimmt es wieder runter; true heißt, es steht jetzt auf der Liste. */
    fun toggle(videoId: String): Boolean {
        val marked = !prefs.contains(videoId)
        prefs.edit().apply { if (marked) putLong(videoId, System.currentTimeMillis()) else remove(videoId) }.apply()
        return marked
    }

    /** Zuletzt gemerkte zuerst. */
    fun ids(): List<String> = prefs.all.keys.sortedByDescending { prefs.getLong(it, 0L) }
}
