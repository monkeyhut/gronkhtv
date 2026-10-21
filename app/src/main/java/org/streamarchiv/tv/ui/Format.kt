package org.streamarchiv.tv.ui

import org.streamarchiv.tv.data.Chapter
import org.streamarchiv.tv.data.Video
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val german = Locale.GERMANY
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", german)

fun Video.subtitle(): String = listOfNotNull(
    episode?.let { "#$it" },
    formatDuration(meta.duration),
    "${NumberFormat.getIntegerInstance(german).format(views)} Aufrufe",
    localDate(createdAt),
).joinToString("  ·  ")

/** Ein Datum, das die Schnittstelle anders schreibt, kostet nur die Angabe; in der Composition risse es die App mit. */
private fun localDate(iso: String): String? = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(dateFormat)
} catch (e: DateTimeParseException) {
    null
}

/** Wo und wie lange ein Spiel im Stream läuft: „ab 2:26 h  ·  1:09 h lang“. */
fun Chapter.subtitle(): String {
    val from = if (start < 60) "von Anfang an" else "ab ${formatDuration(start)}"
    return "$from  ·  ${formatDuration(end - start)} lang"
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "%d:%02d h".format(h, m) else "$m min"
}

/** Stelle im Video wie in der Zeitleiste: 1:02:03 bzw. 2:03. */
fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
