package org.streamarchiv.tv.data

/**
 * Der nächste Teil ist genau Folge n+1. Die Suche nach „#n+1“ trifft sie, liefert daneben aber auch Videos, in deren
 * Titel die Zahl steht (gemessen 21.10.2026). Fehlt die Folge im Archiv (dort gibt es Lücken, etwa 3 bis 12), kommt nichts.
 */
fun pickNextEpisode(current: Video, candidates: List<Video>): Video? {
    val n = current.episode ?: return null
    return candidates.firstOrNull { it.episode == n + 1 && it.id != current.id }
}
