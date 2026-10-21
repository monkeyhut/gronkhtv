package org.streamarchiv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.streamarchiv.tv.data.Video

@Composable
fun SearchScreen(vm: SearchViewModel, onOpenVideo: (Video) -> Unit, onMark: (Video) -> Unit) {
    var text by rememberSaveable { mutableStateOf(vm.query) }
    var fieldFocused by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val voice = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (vm.results == null) { field.requestFocus(); keyboard?.show() } }

    Column(Modifier.padding(top = 32.dp)) {
        // Das Textfeld behält „rechts“ für den Cursor; am Gerät kam man so nie zum Knopf. Deshalb führt rechts direkt hin.
        Row(
            Modifier.padding(horizontal = ScreenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search(text) }),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text("Spiel, Titel oder Folge suchen", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(field)
                    .onPreviewKeyEvent {
                        val right = it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight
                        if (right) voice.requestFocus()
                        right
                    }
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        if (fieldFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
            VoiceSearchButton(
                onResult = { spoken -> text = spoken; keyboard?.hide(); vm.search(spoken) },
                modifier = Modifier.focusRequester(voice),
            )
        }

        vm.error?.let { ErrorBanner(it, onRetry = { vm.search(text) }) }

        val results = vm.results ?: return@Column
        if (results.items.isEmpty() && !results.loading && vm.error == null) {
            Text("Keine Treffer für „${vm.query}“", modifier = Modifier.padding(ScreenPadding))
            return@Column
        }
        val grid = rememberLazyGridState()
        LaunchedEffect(grid, results) {
            snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .collect { last -> if (last >= results.items.size - 8) results.loadMore() }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CardWidth),
            state = grid,
            contentPadding = PaddingValues(ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(results.items, key = { it.id }) { video ->
                VideoCard(video, onClick = { onOpenVideo(video) }, onLongClick = { onMark(video) }, modifier = Modifier.gridCell())
            }
        }
    }
}
