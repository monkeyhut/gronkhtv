package org.streamarchiv.tv.data

/** Kapitel für den Player, nach Beginn geordnet. Ohne Spiel und Titel sind es Reste (gemessen: zwei 1-s-Stücke). */
fun Video.playerChapters(): List<Chapter> =
    chapters.filter { it.category != null || it.title != null }.sortedBy { it.start }

val Chapter.name: String get() = title ?: category?.title ?: "Kapitel"
val Chapter.startMs: Long get() = start * 1000
val Chapter.endMs: Long get() = end * 1000

/** Das Kapitel, in dem [positionMs] liegt: das letzte, das davor beginnt. Lücken zwischen Kapiteln zählen zum vorigen. */
fun chapterAt(chapters: List<Chapter>, positionMs: Long): Int? =
    chapters.indexOfLast { it.startMs <= positionMs }.takeIf { it >= 0 }

/**
 * Startstelle, wenn ein Kapitel angesprungen wird ([requestedMs]): die gespeicherte Stelle gilt nur, wenn sie in
 * genau diesem Kapitel liegt; dort wurde es schon angefangen. Sonst geht es an den Kapitelanfang.
 */
fun startPosition(chapters: List<Chapter>, requestedMs: Long?, savedMs: Long): Long {
    if (requestedMs == null) return savedMs
    val chapter = chapterAt(chapters, requestedMs)?.let(chapters::get) ?: return requestedMs
    return if (savedMs >= chapter.startMs && savedMs < chapter.endMs) savedMs else requestedMs
}
