package org.streamarchiv.tv.data

/** Ein Vorschaubild der Spur: ab wann es gilt und sein Ausschnitt im Sprite (Pixel). Es gilt bis zum nächsten. */
data class PreviewCue(val startMs: Long, val x: Int, val y: Int, val width: Int, val height: Int)

private val time = """(?:(\d+):)?(\d{2}):(\d{2})\.(\d{3})"""
private val cueTimes = Regex("""$time\s+-->\s+$time.*""")
private val region = Regex("""#xywh=(\d+),(\d+),(\d+),(\d+)""")

/**
 * Liest die WebVTT-Vorschauspur von gronkh.tv (gemessen 21.10.2026): je Eintrag eine Zeitzeile, darunter
 * `<hash>_sprite.jpg#xywh=x,y,w,h`. Bilder sind 160×90, der Abstand hängt von der Länge ab (61 s bis 150 s).
 * Einträge ohne Ausschnitt werden übergangen.
 */
fun parsePreviewVtt(text: String): List<PreviewCue> =
    text.lines().map(String::trim).zipWithNext().mapNotNull { (timing, target) ->
        val t = cueTimes.matchEntire(timing)?.groupValues ?: return@mapNotNull null
        val r = region.find(target)?.groupValues?.drop(1)?.map(String::toInt) ?: return@mapNotNull null
        PreviewCue(millis(t), r[0], r[1], r[2], r[3])
    }

/** Die Startzeit aus den ersten vier Gruppen; das Ende braucht niemand, das nächste Bild löst ab. */
private fun millis(groups: List<String>): Long {
    val (h, m, s, ms) = groups.subList(1, 5).map { it.toLongOrNull() ?: 0L }
    return ((h * 60 + m) * 60 + s) * 1000 + ms
}

/** Das Bild für [positionMs]; hinter dem letzten Eintrag bleibt es beim letzten. */
fun List<PreviewCue>.at(positionMs: Long): PreviewCue? = lastOrNull { it.startMs <= positionMs }
