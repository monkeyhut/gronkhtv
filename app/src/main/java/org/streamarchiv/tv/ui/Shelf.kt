package org.streamarchiv.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Seitenrand: am Fernseher gegen Overscan großzügig, auf dem Handy der übliche Rand. */
val ScreenPadding: Dp @Composable @ReadOnlyComposable get() = if (TouchUi) 16.dp else 48.dp

/** Eine Reihe Karten mit Überschrift; [onNearEnd] lädt nach, kurz bevor die Reihe zu Ende ist. */
@Composable
fun <T> Shelf(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    onNearEnd: (() -> Unit)? = null,
    firstItemModifier: Modifier = Modifier,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    if (onNearEnd != null) {
        LaunchedEffect(state, items.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last -> if (items.isNotEmpty() && last >= items.size - 6) onNearEnd() }
        }
    }
    Column {
        Text(
            title,
            style = if (TouchUi) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = ScreenPadding, bottom = 12.dp),
        )
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (TouchUi) 12.dp else 20.dp),
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                card(item, if (index == 0) firstItemModifier else Modifier)
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.padding(horizontal = ScreenPadding),
    ) {
        Text(
            "Laden fehlgeschlagen: $message",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        // Einmal je Banner: der Neuversuch blendet es aus, ein zweiter Tipp im selben Moment lüde alles doppelt.
        var retried by remember { mutableStateOf(false) }
        AppButton(onClick = { if (!retried) { retried = true; onRetry() } }) { Text("Nochmal") }
    }
}
