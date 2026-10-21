package org.streamarchiv.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.streamarchiv.tv.data.Video

/**
 * Hochformat auf dem Handy: unter dem 16:9-Bild eine Videoseite mit Titel, „Als Nächstes“, Kapiteln und Einschlaf-Timer.
 * Dieselben Werte und Taps wie im Optionsfenster des Vollbilds; am Fernseher gibt es sie nicht.
 */
@Composable
fun PlayerDetails(state: PlayerOverlayState, taps: OverlayTaps) = MaterialTheme(colorScheme = Colors) {
    val playback = state.playback ?: return@MaterialTheme
    val video = playback.video
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // tv-material färbt Text ohne Surface schwarz; deshalb die Farbe überall ausdrücklich.
                Text(video.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
                Text(video.subtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        (playback.upNext?.video ?: playback.next)?.let { next ->
            item { NextRow(next, playback.upNext?.secondsLeft, Modifier.fillMaxWidth().onTap(taps.startNext)) }
        }
        if (playback.chapters.isNotEmpty()) {
            item { Heading("Kapitel") }
            itemsIndexed(playback.chapters) { i, chapter ->
                ChapterChoice(
                    chapter,
                    focused = false,
                    playing = chapter == playback.chapter,
                    Modifier.fillMaxWidth().onTap { taps.pick(OptionsCursor(OptionsRow.CHAPTERS, chapter = i, sleep = 0)) },
                )
            }
        }
        item { Heading("Einschlaf-Timer") }
        item {
            SleepChoices(
                focused = SLEEP_CHOICES.indexOf(state.sleepChoice),
                active = state.sleepChoice,
                onPick = { taps.pick(OptionsCursor(OptionsRow.SLEEP, chapter = 0, sleep = it)) },
                fillWidth = true,
            )
        }
    }
}

@Composable
private fun Heading(text: String) = Text(
    text,
    Modifier.padding(top = 12.dp, bottom = 4.dp),
    color = MaterialTheme.colorScheme.onSurface,
    style = MaterialTheme.typography.titleMedium,
)

/** Der nächste Teil; [secondsLeft] zählt am Ende des Videos herunter, bis er von selbst startet. */
@Composable
private fun NextRow(next: Video, secondsLeft: Int?, modifier: Modifier) = Row(
    modifier.padding(top = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    AsyncImage(
        next.urls.thumbnail,
        contentDescription = null,
        modifier = Modifier.size(128.dp, 72.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
    )
    Column {
        Text(
            secondsLeft?.let { "Als Nächstes  ·  in $it s" } ?: "Als Nächstes",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            next.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
