package org.streamarchiv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.streamarchiv.tv.data.GameStats
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.chapterOf
import org.streamarchiv.tv.data.startMs

/** Auf dem Handy so schmal, dass hochkant gut zwei Karten nebeneinander stehen und man sieht, dass die Reihe weitergeht. */
val CardWidth: Dp @Composable @ReadOnlyComposable get() = if (TouchUi) 168.dp else 260.dp

/** [progress] (0..1) zeichnet den Weiterschauen-Balken unten ins Bild; [onLongClick] ist langes OK. */
@Composable
fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String = video.subtitle(),
) {
    Column(modifier.width(CardWidth)) {
        // tv-material löst onClick ohnehin erst beim Loslassen aus; langes OK verzögert das kurze also nicht.
        Tile(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Box {
                AsyncImage(
                    model = video.urls.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                if (progress != null) {
                    Box(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        Text(
            video.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Stream eines Spiels: OK startet am ersten Kapitel des Spiels ([onOpen] bekommt die Stelle), die Unterzeile sagt wo und
 * wie lange. Fehlt das Kapitel (Liste und Video passen nicht zusammen), ist es eine gewöhnliche Karte.
 */
@Composable
fun GameVideoCard(video: Video, slug: String, onOpen: (Video, Long?) -> Unit, onMark: (Video) -> Unit, modifier: Modifier = Modifier) {
    val chapter = video.chapterOf(slug)
    VideoCard(
        video,
        onClick = { onOpen(video, chapter?.startMs) },
        onLongClick = { onMark(video) },
        modifier = modifier,
        subtitle = chapter?.subtitle() ?: video.subtitle(),
    )
}

/** Hochkant-Kachel eines Spiels mit Box-Art und der Zahl der Streams, in denen es vorkommt. */
@Composable
fun GameCard(game: GameStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(GameCardWidth)) {
        Tile(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AsyncImage(
                model = game.category.urls.boxarts["xl"],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            game.category.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            if (game.streams == 1) "1 Stream" else "${game.streams} Streams",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val GameCardWidth: Dp @Composable @ReadOnlyComposable get() = if (TouchUi) 112.dp else 150.dp
