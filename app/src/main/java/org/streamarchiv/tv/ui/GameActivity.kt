package org.streamarchiv.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.serialization.json.Json
import org.streamarchiv.tv.data.Category
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.WatchlistStore

/**
 * Spielseite: alle Streams, in denen das Spiel als Kapitel vorkommt. Eigene Activity statt Wechsel in der Startseite,
 * damit Zurück auf der Kachel landet, von der man kam; Fokus und Scrollstelle der Startseite bleiben so unberührt.
 */
class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val encoded = requireNotNull(intent.getStringExtra(EXTRA_CATEGORY)) { "GameActivity ohne Spiel gestartet" }
        val category = Json.decodeFromString(Category.serializer(), encoded)
        val watchlist = WatchlistStore(this)
        setAppContent {
            GameScreen(
                viewModel { GameViewModel(category) },
                onOpen = { video, startMs -> startActivity(PlayerActivity.intent(this, video, startMs)) },
                // Die Merkliste der Startseite liest den Speicher bei der Rückkehr neu.
                onMark = { showMarked(watchlist.toggle(it.id)) },
            )
        }
    }

    companion object {
        private const val EXTRA_CATEGORY = "category"
        fun intent(context: Context, category: Category): Intent =
            Intent(context, GameActivity::class.java).putExtra(EXTRA_CATEGORY, Json.encodeToString(Category.serializer(), category))
    }
}

@Composable
private fun GameScreen(vm: GameViewModel, onOpen: (Video, Long?) -> Unit, onMark: (Video) -> Unit) {
    val list = vm.videos
    val firstCard = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(list.items.isNotEmpty()) {
        if (!focused && list.items.isNotEmpty()) { firstCard.requestFocus(); focused = true }
    }

    Column(Modifier.padding(top = 32.dp)) {
        Row(
            Modifier.padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                vm.category.urls.boxarts["lg"],
                contentDescription = null,
                modifier = Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(vm.category.title, style = if (TouchUi) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
                Text(
                    if (TouchUi) "Tippen startet am Kapitelanfang  ·  lange drücken merkt" else "OK startet am Kapitelanfang  ·  langes OK merkt",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        vm.error?.let { ErrorBanner(it, onRetry = vm::retry) }
        if (list.items.isEmpty() && !list.loading && vm.error == null) {
            Text("Keine Streams gefunden", modifier = Modifier.padding(ScreenPadding))
            return@Column
        }
        val grid = rememberLazyGridState()
        LaunchedEffect(grid) {
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last -> if (list.items.isNotEmpty() && last >= list.items.size - 8) list.loadMore() }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CardWidth),
            state = grid,
            contentPadding = PaddingValues(ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            itemsIndexed(list.items, key = { _, video -> video.id }) { i, video ->
                GameVideoCard(video, vm.category.slug, onOpen, onMark, (if (i == 0) Modifier.focusRequester(firstCard) else Modifier).gridCell())
            }
        }
    }
}
