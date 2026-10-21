package org.streamarchiv.tv.data

/**
 * Eine feste Sendereihe mit eigener Reihe auf der Startseite. [query] geht an die Suche, die nur im Titel sucht und
 * dabei unscharf ist; [belongsTo] sortiert danach aus, was nicht dazugehört.
 */
class ShowFormat(val title: String, val query: String, private val tag: String, private val titlePattern: Regex) {
    /**
     * Hat das Video Schlagwörter, entscheiden sie: so fallen „#FREiAB18 (HORDE wurde verschoben)“ und Kaffeekranz-Streams
     * mit HORDE im Titel heraus. Ein Viertel der FREiAB18-Folgen hat gar keine Schlagwörter, dort zählt der Titel, ohne
     * Chatbefehle wie „!horde“ (gemessen 21.10.2026).
     */
    fun belongsTo(video: Video): Boolean =
        if (video.tags.isNotEmpty()) video.tags.any { it.title == tag }
        else titlePattern.containsMatchIn(video.title.replace(CHAT_COMMAND, ""))
}

private val CHAT_COMMAND = Regex("""!\S+""")

/** Reihenfolge wie auf der Startseite. Suchbegriffe gemessen: HORDE 17, Friendly Fire 7, FREiAB18 96 Treffer. */
val SHOW_FORMATS = listOf(
    ShowFormat("HORDE", "HORDE", "H.O.R.D.E.", Regex("""\bH\.?O\.?R\.?D\.?E\b""", RegexOption.IGNORE_CASE)),
    ShowFormat("Friendly Fire", "Friendly Fire", "Friendly Fire", Regex("""FRIENDLY\s*FIRE""", RegexOption.IGNORE_CASE)),
    ShowFormat("#FREiAB18", "FREiAB18", "FREiAB18", Regex("""FREIAB18""", RegexOption.IGNORE_CASE)),
)
