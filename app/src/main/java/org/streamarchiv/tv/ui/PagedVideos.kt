package org.streamarchiv.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.VideoPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Videoliste, die seitenweise nachlädt, sobald die Ansicht ans Ende kommt. */
class PagedVideos(
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
    private val fetch: suspend (page: Int) -> VideoPage,
) {
    val items = mutableStateListOf<Video>()
    var loading by mutableStateOf(false); private set
    private var page = 0
    private var lastPage = 1

    /** Holt Seite 1 neu und setzt nur die neuen Videos vorn an; geladene Seiten und Position bleiben. */
    fun refreshHead() {
        if (items.isEmpty()) return loadMore()
        scope.launch {
            runCatchingIo { fetch(1) }
                .onSuccess { result ->
                    val known = knownIds()
                    items.addAll(0, result.data.takeWhile { it.id !in known })
                }
                .onFailure(onError)
        }
    }

    fun loadMore() {
        if (loading || page >= lastPage) return
        loading = true
        scope.launch {
            runCatchingIo { fetch(page + 1) }
                .onSuccess { result ->
                    // Zwischen zwei Seiten verschiebt sich die Liste (neuer Stream, Aufrufe ändern sich); ein Video
                    // käme dann doppelt, und doppelte Schlüssel lassen die Reihe abstürzen.
                    val known = knownIds()
                    items += result.data.filter { it.id !in known }
                    page = result.meta?.currentPage ?: (page + 1)
                    lastPage = result.meta?.lastPage ?: page
                }
                .onFailure(onError)
            loading = false
        }
    }

    private fun knownIds(): Set<String> = items.mapTo(HashSet()) { it.id }
}
