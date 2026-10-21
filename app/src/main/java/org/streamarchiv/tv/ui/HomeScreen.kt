package org.streamarchiv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.streamarchiv.tv.data.Category
import org.streamarchiv.tv.data.LiveStream
import org.streamarchiv.tv.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenVideo: (Video) -> Unit,
    onOpenAt: (Video, Long?) -> Unit,
    onMark: (Video) -> Unit,
    onOpenLive: (LiveStream) -> Unit,
    onOpenGame: (Category) -> Unit,
    onSearch: () -> Unit,
) {
    // Neu laden, solange die Startseite sichtbar ist: sofort beim Zurückkehren, danach im Takt.
    // Der Live-Status braucht einen kürzeren Takt als das Archiv.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                while (true) {
                    delay(HomeViewModel.LIVE_INTERVAL)
                    vm.refreshLive()
                }
            }
            while (true) {
                vm.refresh()
                delay(HomeViewModel.REFRESH_INTERVAL)
            }
        }
    }

    // Beim Start liegt der Fokus auf der ersten Karte der obersten Videoreihe, damit die Fernbedienung sofort greift.
    // Bewusst nicht auf der Live-Karte: ein versehentliches OK risse sonst aus der App nach Twitch. Nur einmal, sonst
    // spränge der Fokus nach oben, sobald man etwas merkt oder zum ersten Mal ein Video anfängt.
    val firstCard = remember { FocusRequester() }
    val topRow = when {
        vm.continueWatching.isNotEmpty() -> TopRow.CONTINUE
        vm.watchlist.isNotEmpty() -> TopRow.WATCHLIST
        vm.newest.isNotEmpty() -> TopRow.NEWEST
        else -> null
    }
    var focusedAtStart by remember { mutableStateOf(false) }
    LaunchedEffect(topRow, vm.localRowsLoaded) {
        if (!focusedAtStart && vm.localRowsLoaded && topRow != null) {
            firstCard.requestFocus()
            focusedAtStart = true
        }
    }
    val startFocus = { row: TopRow -> if (row == topRow) Modifier.focusRequester(firstCard) else Modifier }

    LazyColumn(
        contentPadding = PaddingValues(vertical = if (TouchUi) 16.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(if (TouchUi) 24.dp else 36.dp),
    ) {
        // Feste Schlüssel: taucht eine Reihe neu auf (Fehler, Live, erste Merkung), bleiben Fokus und Scrollstelle stehen.
        item(key = "header") { Header(onSearch) }
        vm.error?.let { message -> item(key = "error") { ErrorBanner(message, onRetry = vm::refresh) } }
        vm.live?.let { stream -> item(key = "live") { LiveCard(stream, onClick = { onOpenLive(stream) }) } }
        if (vm.continueWatching.isNotEmpty()) item(key = "weiterschauen") {
            Shelf("Weiterschauen", vm.continueWatching, key = { it.video.id }, firstItemModifier = startFocus(TopRow.CONTINUE)) { started, mod ->
                val durationMs = started.video.meta.duration * 1000
                VideoCard(
                    started.video,
                    onClick = { onOpenVideo(started.video) },
                    onLongClick = { onMark(started.video) },
                    modifier = mod,
                    progress = if (durationMs > 0) started.positionMs.toFloat() / durationMs else null,
                    subtitle = "noch " + formatDuration(((durationMs - started.positionMs) / 1000).coerceAtLeast(0)),
                )
            }
        }
        if (vm.watchlist.isNotEmpty()) item(key = "merkliste") {
            VideoShelf("Merkliste", vm.watchlist, onOpenVideo, onMark, firstItemModifier = startFocus(TopRow.WATCHLIST))
        }
        item(key = "neueste") { VideoShelf("Neueste Streams", vm.newest, onOpenVideo, onMark, firstItemModifier = startFocus(TopRow.NEWEST)) }
        if (vm.games.isNotEmpty()) item(key = "spiele") {
            Shelf("Spiele", vm.games, key = { it.category.slug }) { game, mod -> GameCard(game, onClick = { onOpenGame(game.category) }, modifier = mod) }
        }
        vm.currentGames.forEach { (category, list) ->
            if (list.items.isNotEmpty()) item(key = "spiel-${category.slug}") {
                Shelf(category.title, list.items, key = { it.id }, onNearEnd = list::loadMore) { video, mod ->
                    GameVideoCard(video, category.slug, onOpenAt, onMark, mod)
                }
            }
        }
        vm.formats.forEach { (title, list) -> item(key = "format-$title") { VideoShelf(title, list.items, onOpenVideo, onMark, onNearEnd = list::loadMore) } }
        item(key = "beliebt") { VideoShelf("Gerade beliebt", vm.hot, onOpenVideo, onMark) }
        item(key = "alle") { VideoShelf("Alle Streams", vm.all.items, onOpenVideo, onMark, onNearEnd = vm.all::loadMore) }
        item(key = "meistgesehen") { VideoShelf("Meistgesehen", vm.mostViewed.items, onOpenVideo, onMark, onNearEnd = vm.mostViewed::loadMore) }
        item(key = "zufall") { VideoShelf("Zufallsfund", vm.random, onOpenVideo, onMark) }
        vm.watchedThisMonth?.let { month -> item(key = "rueckblick") { MonthReviewCard(month) } }
    }
}

private enum class TopRow { CONTINUE, WATCHLIST, NEWEST }

@Composable
private fun VideoShelf(
    title: String,
    videos: List<Video>,
    onOpenVideo: (Video) -> Unit,
    onMark: (Video) -> Unit,
    onNearEnd: (() -> Unit)? = null,
    firstItemModifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    Shelf(title, videos, key = { it.id }, onNearEnd = onNearEnd, firstItemModifier = firstItemModifier) { video, mod ->
        VideoCard(video, onClick = { onOpenVideo(video) }, onLongClick = { onMark(video) }, modifier = mod)
    }
}

@Composable
private fun LiveCard(stream: LiveStream, onClick: () -> Unit) {
    Tile(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = ScreenPadding).fillMaxWidth().height(LiveCardHeight),
    ) {
        Box {
            AsyncImage(
                model = stream.thumbnails["lg"] ?: stream.thumbnails["md"],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.65f to Color.Transparent)))
            Column(Modifier.align(Alignment.BottomStart).padding(if (TouchUi) 16.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "●  LIVE auf Twitch",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.background(LiveRed, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 4.dp),
                )
                Text(stream.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(stream.game, "${NumberFormat.getIntegerInstance(Locale.GERMANY).format(stream.viewers)} Zuschauer")
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(if (TouchUi) "Tippen öffnet Twitch" else "OK öffnet Twitch", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

private val LiveCardHeight: Dp @Composable @ReadOnlyComposable get() = if (TouchUi) 220.dp else 300.dp
private val LiveRed = Color(0xFFE5303A)

@Composable
private fun Header(onSearch: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
    ) {
        Text(
            "gronkh.tv",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline(),
        )
        // Die App stammt nicht von gronkh.tv; das soll auf den ersten Blick klar sein.
        Text(
            "inoffizielle App",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp).alignByBaseline(),
        )
        Spacer(Modifier.weight(1f))
        AppButton(onClick = onSearch) { Text("Suche") }
    }
}
