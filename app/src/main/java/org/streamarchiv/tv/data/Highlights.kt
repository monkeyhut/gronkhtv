package org.streamarchiv.tv.data

import kotlin.math.abs

private const val WINDOW_MIN = 15
private const val MIN_RATIO = 1.5
private const val MIN_MESSAGES = 10
private const val MIN_GAP_MIN = 5

/**
 * Höhepunkte eines Streams aus der Chat-Aktivität je Minute: Minuten, in denen der Chat deutlich lauter ist als in der
 * halben Stunde drumherum, je Höhepunkt mindestens 5 Minuten Abstand, etwa einer je 30 Minuten Länge.
 * Gewertet wird das Verhältnis zum gleitenden Mittel, nicht der Abstand: sonst gewinnt immer die volle Anfangsphase
 * mit den Begrüßungen, und ruhige Nachtstunden kämen nie vor (an #1124 geprüft, 21.10.2026).
 */
fun highlightMinutes(perMinute: IntArray, count: Int = perMinute.size / 30): List<Int> {
    val ranked = perMinute.indices.mapNotNull { i ->
        val from = (i - WINDOW_MIN).coerceAtLeast(0)
        val to = (i + WINDOW_MIN).coerceAtMost(perMinute.lastIndex)
        val mean = (from..to).sumOf { perMinute[it] }.toDouble() / (to - from + 1)
        val ratio = perMinute[i] / mean
        if (perMinute[i] >= MIN_MESSAGES && ratio >= MIN_RATIO) i to ratio else null
    }.sortedByDescending { it.second }
    val picked = mutableListOf<Int>()
    for ((minute, _) in ranked) {
        if (picked.size >= count) break
        if (picked.none { abs(it - minute) < MIN_GAP_MIN }) picked += minute
    }
    return picked.sorted()
}

/**
 * Sprungziel für „hoch“: der nächste Höhepunkt hinter [positionMs], mit [leadMs] Vorlauf, damit man den Anlass
 * mitbekommt. Die 2 s Toleranz halten einen zweiten Druck davon ab, am eben angesprungenen Ziel hängen zu bleiben,
 * falls der Player beim Springen ein Stück vor dem Ziel landet.
 */
fun nextHighlightTarget(highlightsMs: List<Long>, positionMs: Long, leadMs: Long = 20_000): Long? =
    highlightsMs.map { (it - leadMs).coerceAtLeast(0) }.firstOrNull { it > positionMs + 2_000 }
