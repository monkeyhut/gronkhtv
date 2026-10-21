package org.streamarchiv.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.streamarchiv.tv.data.Chapter
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.name
import org.streamarchiv.tv.data.startMs

/** Vorschaubild beim Spulen; [centerX] und [bottomY] in Pixeln des Overlays (Zielstelle auf der Zeitleiste). */
class SeekPreview(val frame: ImageBitmap, val time: String, val centerX: Int, val bottomY: Int)

/** Karte „Als Nächstes“ am Ende eines Videos. */
class UpNext(val video: Video, val secondsLeft: Int)

/** Wahl des Einschlaf-Timers in Minuten; 0 = aus. */
val SLEEP_CHOICES = listOf(0, 30, 60, 90)

/**
 * Was der Player über dem Bild einblendet. Die Activity schreibt die Werte, Compose zeichnet nur;
 * die Tasten bleiben bei der Activity, damit die abgestimmte Belegung an einer Stelle steht.
 */
class PlayerOverlayState {
    var barShown by mutableStateOf(false)
    var hint by mutableStateOf<String?>(null)
    var preview by mutableStateOf<SeekPreview?>(null)
    /** Gewählter Einschlaf-Timer (aus [SLEEP_CHOICES]) und Restzeit in Minuten, solange er läuft. */
    var sleepChoice by mutableStateOf(0)
    var sleepLeftMin by mutableStateOf<Int?>(null)
    /** Markierte Wahl im Optionsfenster; null = Fenster zu. */
    var optionsCursor by mutableStateOf<OptionsCursor?>(null)
    var upNext by mutableStateOf<UpNext?>(null)
    /** Kapitel des laufenden Videos und das an der aktuellen Stelle; aktualisiert beim Einblenden und Springen. */
    var chapters by mutableStateOf<List<Chapter>>(emptyList())
    var chapter by mutableStateOf<Chapter?>(null)
    /** Das laufende Video und, auf dem Handy vorab gesucht, der nächste Teil für die Videoseite im Hochformat. */
    var video by mutableStateOf<Video?>(null)
    var next by mutableStateOf<Video?>(null)
    /** Hochformat auf dem Handy: das Bild steht klein über der Videoseite, Optionen und „Als Nächstes“ stehen dort. */
    var inline by mutableStateOf(false)
}

/**
 * Was ein Finger im Overlay auslöst. Am Fernseher gibt es diese Flächen nicht, dort laufen alle Eingaben über die
 * Tasten der Activity; [pick] übernimmt eine Wahl im Optionsfenster wie OK auf der Markierung.
 */
class OverlayTaps(
    val openOptions: () -> Unit,
    val pick: (OptionsCursor) -> Unit,
    val closeOptions: () -> Unit,
    val startNext: () -> Unit,
)

@Composable
fun PlayerOverlay(state: PlayerOverlayState, taps: OverlayTaps) = MaterialTheme(colorScheme = Colors) {
    Box(Modifier.fillMaxSize()) {
        state.preview?.let { SeekPreviewFrame(it) }
        state.hint?.let { Hint(it, Modifier.align(Alignment.TopCenter).padding(top = 48.dp)) }
        // Dezent mit der Leiste, damit man den Timer nicht vergisst, aber nicht dauernd im Bild hat.
        // Auf dem Handy steht dort der Knopf zu Kapiteln und Timer und trägt die Restzeit mit.
        if (state.barShown && TouchUi && !state.inline) {
            AppButton(onClick = taps.openOptions, modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 48.dp)) {
                Text(
                    state.sleepLeftMin?.let { "Optionen  ·  aus in $it min" } ?: "Optionen",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        } else if (state.barShown && !TouchUi) state.sleepLeftMin?.let {
            Text(
                "Aus in $it min",
                Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 48.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (state.barShown) state.chapter?.let { ChapterLabel(it, Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 48.dp)) }
        // Über der Zeitleiste, die am Ende offen steht.
        if (!state.inline) state.upNext?.let { UpNextCard(it, Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 140.dp).onTap(taps.startNext)) }
        state.optionsCursor?.let {
            // Tippen daneben schließt; ohne diese Fläche ginge der Tipp an den Player und blendete die Leiste ein.
            if (TouchUi) Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = taps.closeOptions))
            Options(it, state.chapters, state.chapter, state.sleepChoice, taps.pick, Modifier.align(Alignment.Center))
        }
    }
}

/** Antippbar nur auf dem Handy; am Fernseher bliebe sonst eine fokussierbare Fläche im Overlay. */
@Composable
internal fun Modifier.onTap(action: () -> Unit): Modifier = if (TouchUi) clickable(onClick = action) else this

@Composable
private fun ChapterLabel(chapter: Chapter, modifier: Modifier) = Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    BoxArt(chapter, "sm", Modifier.size(30.dp, 40.dp))
    Text(
        "Kapitel: ${chapter.name}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Box-Art des Kapitelspiels; ohne Spiel bleibt der Platz leer, damit die Kapitel in einer Flucht stehen. */
@Composable
private fun BoxArt(chapter: Chapter, size: String, modifier: Modifier) = AsyncImage(
    chapter.category?.urls?.boxarts?.get(size),
    contentDescription = null,
    modifier = modifier.clip(RoundedCornerShape(4.dp)),
    contentScale = ContentScale.Crop,
)

@Composable
private fun SeekPreviewFrame(preview: SeekPreview) = Column(
    Modifier
        // Über der Zielstelle zentriert, am Bildrand eingefangen.
        .layout { measurable, constraints ->
            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(p.width, p.height) {
                val x = (preview.centerX - p.width / 2).coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                p.place(x, preview.bottomY - p.height - 12.dp.roundToPx())
            }
        }
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        .padding(4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Image(preview.frame, contentDescription = null, Modifier.size(192.dp, 108.dp).clip(RoundedCornerShape(6.dp)))
    Text(preview.time, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun UpNextCard(next: UpNext, modifier: Modifier) = Row(
    modifier
        .width(520.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
        .padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    AsyncImage(
        next.video.urls.thumbnail,
        contentDescription = null,
        modifier = Modifier.size(176.dp, 99.dp).clip(RoundedCornerShape(10.dp)),
        contentScale = ContentScale.Crop,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Als Nächstes  ·  in ${next.secondsLeft} s",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            next.video.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(if (TouchUi) "Tippen startet  ·  Zurück bricht ab" else "OK startet  ·  Zurück bricht ab", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Options(
    cursor: OptionsCursor,
    chapters: List<Chapter>,
    current: Chapter?,
    active: Int,
    pick: (OptionsCursor) -> Unit,
    modifier: Modifier,
) = Column(
    modifier
        .widthIn(max = 1280.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
        // Ein Tipp auf den Rand des Fensters darf es nicht über die Fläche dahinter schließen.
        .then(if (TouchUi) Modifier.clickable(interactionSource = null, indication = null) {} else Modifier)
        .padding(horizontal = 32.dp, vertical = if (TouchUi) 20.dp else 28.dp),
    verticalArrangement = Arrangement.spacedBy(if (TouchUi) 14.dp else 20.dp),
) {
    if (chapters.isNotEmpty()) {
        Text("Kapitel", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
        val list = rememberLazyListState()
        // Eins davor bleibt sichtbar, damit man sieht, dass links noch etwas kommt.
        LaunchedEffect(cursor.chapter) { list.animateScrollToItem((cursor.chapter - 1).coerceAtLeast(0)) }
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(chapters) { i, chapter ->
                ChapterChoice(
                    chapter,
                    focused = cursor.row == OptionsRow.CHAPTERS && i == cursor.chapter,
                    playing = chapter == current,
                    Modifier.onTap { pick(cursor.copy(row = OptionsRow.CHAPTERS, chapter = i)) },
                )
            }
        }
    }
    Text("Einschlaf-Timer", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
    SleepChoices(
        focused = cursor.sleep.takeIf { cursor.row == OptionsRow.SLEEP },
        active = active,
        onPick = { pick(cursor.copy(row = OptionsRow.SLEEP, sleep = it)) },
    )
    Text(
        when {
            TouchUi -> "Tippen übernimmt  ·  daneben tippen schließt"
            chapters.isEmpty() -> "OK übernimmt  ·  Zurück schließt"
            else -> "Hoch/runter wechselt die Zeile  ·  OK übernimmt  ·  Zurück schließt"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * Die Stufen des Einschlaf-Timers; [focused] ist die markierte (Index), [active] die gewählte (Minuten). Mit [fillWidth]
 * teilen sich die Stufen die Breite, damit sie auf dem schmalen Handy in eine Zeile passen.
 */
@Composable
internal fun SleepChoices(focused: Int?, active: Int, onPick: (Int) -> Unit, fillWidth: Boolean = false) = Row(
    if (fillWidth) Modifier.fillMaxWidth() else Modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    SLEEP_CHOICES.forEachIndexed { i, minutes ->
        val isFocused = i == focused
        Text(
            if (minutes == 0) "Aus" else "$minutes min",
            Modifier
                .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                .clip(RoundedCornerShape(50))
                .background(choiceColor(isFocused))
                .onTap { onPick(i) }
                .padding(horizontal = if (fillWidth) 8.dp else 22.dp, vertical = if (TouchUi) 12.dp else 10.dp),
            color = textColor(isFocused, minutes == active),
            fontWeight = if (minutes == active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Ein Kapitel zur Wahl; [playing] ist das, in dem man gerade steht. */
@Composable
internal fun ChapterChoice(chapter: Chapter, focused: Boolean, playing: Boolean, modifier: Modifier) = Row(
    modifier.clip(RoundedCornerShape(12.dp)).background(choiceColor(focused)).padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    BoxArt(chapter, "md", Modifier.size(36.dp, 48.dp))
    Column(Modifier.widthIn(max = 220.dp).padding(end = 8.dp)) {
        Text(
            chapter.name,
            color = textColor(focused, playing),
            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(formatClock(chapter.startMs), color = textColor(focused, false), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun choiceColor(focused: Boolean) = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface

@Composable
private fun textColor(focused: Boolean, active: Boolean) = when {
    focused -> MaterialTheme.colorScheme.onPrimary
    active -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun Hint(text: String, modifier: Modifier) = Text(
    text,
    modifier
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), RoundedCornerShape(50))
        .padding(horizontal = 24.dp, vertical = 12.dp),
    // tv-material färbt Text ohne Surface schwarz; deshalb die Farbe überall ausdrücklich.
    color = MaterialTheme.colorScheme.onSurface,
    style = MaterialTheme.typography.titleMedium,
)
