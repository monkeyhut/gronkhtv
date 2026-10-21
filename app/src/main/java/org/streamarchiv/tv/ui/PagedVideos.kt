package org.streamarchiv.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.VideoPage
import org.streamarchiv.tv.data.distinctById
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
        scope.launch { runCatchingIo { fetch(1) }.onSuccess(::showHead).onFailure(onError) }
    }

    /** Wie [refreshHead] mit einer Seite 1, die schon geholt ist; die Startseite braucht sie auch für die Spiele. */
    fun showHead(first: VideoPage) {
        if (items.isEmpty()) return append(first)
        val known = knownIds()
        items.addAll(0, first.data.distinctById().takeWhile { it.id !in known })
    }

    fun loadMore() {
        if (loading || page >= lastPage) return
        loading = true
        scope.launch {
            runCatchingIo { fetch(page + 1) }.onSuccess(::append).onFailure(onError)
            loading = false
        }
    }

    private fun append(result: VideoPage) {
        // Zwischen zwei Seiten verschiebt sich die Liste (neuer Stream, Aufrufe ändern sich); ein Video
        // käme dann doppelt, und doppelte Schlüssel lassen die Reihe abstürzen.
        val known = knownIds()
        items += result.data.distinctById().filter { it.id !in known }
        page = result.meta?.currentPage ?: (page + 1)
        lastPage = result.meta?.lastPage ?: page
    }

    private fun knownIds(): Set<String> = items.mapTo(HashSet()) { it.id }
}
