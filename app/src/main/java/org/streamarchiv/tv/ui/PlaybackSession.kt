package org.streamarchiv.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.streamarchiv.tv.data.Chapter
import org.streamarchiv.tv.data.PreviewSprites
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.playerChapters

/**
 * Alles, was zu einem Video im Player gehört. Jedes neue Video bekommt ein neues Objekt, das alte wird geschlossen;
 * so bleibt nichts vom vorigen stehen (Kapitel, Höhepunkte, Vorschau, offenes Optionsfenster, Countdown), ohne dass
 * jemand Feld für Feld zurücksetzen muss. Der Einschlaf-Timer gehört bewusst nicht dazu, er gilt für den Abend.
 */
class PlaybackSession(val video: Video, parent: CoroutineScope) {
    /** Was nebenher lädt oder zählt; [close] bricht alles ab, ein spätes Ergebnis landet so nie beim nächsten Video. */
    val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))
    val chapters: List<Chapter> = video.playerChapters()

    /** Das Kapitel an der aktuellen Stelle; aktualisiert beim Einblenden, Springen und im Sicherungstakt. */
    var chapter by mutableStateOf<Chapter?>(null)
    /** Auf dem Handy vorab gesucht: der nächste Teil für die Videoseite im Hochformat. */
    var next by mutableStateOf<Video?>(null)
    /** Karte „Als Nächstes“ am Ende, mit dem Countdown in [upNextJob]. */
    var upNext by mutableStateOf<UpNext?>(null)
    var upNextJob: Job? = null
    /** Markierte Wahl im Optionsfenster; null = Fenster zu. */
    var optionsCursor by mutableStateOf<OptionsCursor?>(null)
    var preview by mutableStateOf<SeekPreview?>(null)
    var previewJob: Job? = null
    var previews: PreviewSprites? = null
    /** Höhepunkte in ms; null, solange der Chat noch gezählt wird oder (Mobilfunk) noch niemand danach gefragt hat. */
    var highlights: List<Long>? = null
    var highlightsJob: Job? = null

    fun close() = scope.cancel()
}
