package org.streamarchiv.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.streamarchiv.tv.data.Category
import org.streamarchiv.tv.data.GameStats
import org.streamarchiv.tv.data.GronkhApi
import org.streamarchiv.tv.data.GronkhApi.Discovery
import org.streamarchiv.tv.data.GronkhApi.Order
import org.streamarchiv.tv.data.LiveStream
import org.streamarchiv.tv.data.ResumeStore
import org.streamarchiv.tv.data.SHOW_FORMATS
import org.streamarchiv.tv.data.Video
import org.streamarchiv.tv.data.WatchStats
import org.streamarchiv.tv.data.WatchSummary
import org.streamarchiv.tv.data.WatchlistStore
import org.streamarchiv.tv.data.currentGames
import org.streamarchiv.tv.data.distinctById
import org.streamarchiv.tv.data.gameShelf
import org.streamarchiv.tv.data.monthOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Netz- und Formatfehler werden angezeigt, alles andere (Programmfehler, Abbruch) läuft weiter durch. */
suspend fun <T> runCatchingIo(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: IOException) {
    Result.failure(e)
} catch (e: SerializationException) {
    Result.failure(e)
}

/**
 * Welche IDs aus Weiterschauen und Merkliste fliegen: nur die, zu denen die Schnittstelle „gelöscht“ meldet, und nur,
 * wenn im selben Durchgang mindestens ein anderes Video geladen wurde. Meldet sie alles als gelöscht (leere oder falsche
 * Datenbank), wäre das ein Ausfall und kein Aufräumen. Eine feste Obergrenze je Durchgang schützte nicht: die Schleife
 * in loadLocalRows holte Runde um Runde die nächsten und leerte die Listen so trotzdem.
 * Preis dafür: Fehlt nur ein einziges, schon gelöschtes Video, fliegt es erst beim nächsten Durchgang mit Treffer.
 */
internal fun idsToForget(results: Map<String, Result<Video>>): Set<String> =
    if (results.values.none { it.isSuccess }) emptySet()
    else results.filterValues { it.exceptionOrNull() is GronkhApi.NotFound }.keys

/** Ein angefangenes Video samt Stelle, für die Weiterschauen-Reihe. */
data class Started(val video: Video, val positionMs: Long)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val resume = ResumeStore(app)
    private val watchlistStore = WatchlistStore(app)
    private val stats = WatchStats(app)
    /** Der laufende Monat für den Rückblick; null, solange noch nichts geschaut ist. */
    var watchedThisMonth by mutableStateOf<WatchSummary?>(null); private set
    var continueWatching by mutableStateOf<List<Started>>(emptyList()); private set
    var watchlist by mutableStateOf<List<Video>>(emptyList()); private set
    /** Erst danach steht fest, welche Reihe oben steht und den Startfokus bekommt. */
    var localRowsLoaded by mutableStateOf(false); private set
    var live by mutableStateOf<LiveStream?>(null); private set
    var newest by mutableStateOf<List<Video>>(emptyList()); private set
    var hot by mutableStateOf<List<Video>>(emptyList()); private set
    var random by mutableStateOf<List<Video>>(emptyList()); private set
    /** Spiele und Themen der neuesten Streams für die Kachelreihe. */
    var games by mutableStateOf<List<GameStats>>(emptyList()); private set
    /** Die aktuell meistgespielten Spiele, je mit eigener Reihe, die wie „Alle Streams“ nachlädt. */
    var currentGames by mutableStateOf<List<Pair<Category, PagedVideos>>>(emptyList()); private set
    /** Bleibt ein Spiel vorn, behält seine Reihe Inhalt und Scrollstelle über das nächste Aktualisieren. */
    private val gameRows = HashMap<String, PagedVideos>()
    var error by mutableStateOf<String?>(null); private set

    private val showError: (Throwable) -> Unit = { error = it.message ?: it.toString() }
    /** Details der Videos aus Weiterschauen und Merkliste. Nach der Rückkehr aus dem Player fehlt meist höchstens eins. */
    private val known = HashMap<String, Video>()
    /** elapsedRealtime der letzten Abfrage der Archivlisten; 0 = noch nie. */
    private var listsAt = 0L
    val all = PagedVideos(viewModelScope, showError) { GronkhApi.videos(Order.NEWEST, it) }
    val mostViewed = PagedVideos(viewModelScope, showError) { GronkhApi.videos(Order.VIEWS, it) }
    /** Je Sendereihe eine Liste, die wie „Alle Streams“ nachlädt; Fremdtreffer der Suche fallen schon beim Laden raus. */
    val formats = SHOW_FORMATS.map { format ->
        format.title to PagedVideos(viewModelScope, showError) { page ->
            GronkhApi.search(format.query, page).let { it.copy(data = it.data.filter(format::belongsTo)) }
        }
    }

    /**
     * Läuft beim Öffnen, bei jeder Rückkehr in die App und alle [REFRESH_INTERVAL] (siehe HomeScreen). Die Archivlisten
     * ändern sich selten und kommen höchstens alle [LISTS_MIN_GAP], nach einem Fehler sofort; die eigenen Reihen immer.
     */
    fun refresh() {
        val now = SystemClock.elapsedRealtime()
        val listsDue = error != null || listsAt == 0L || now - listsAt >= LISTS_MIN_GAP.inWholeMilliseconds
        error = null
        if (listsDue) {
            listsAt = now
            refreshLists()
        }
        loadLocalRows()
        refreshLive()
        watchedThisMonth = stats.summary(monthOf(System.currentTimeMillis())).takeIf { it.seconds > 0 }
    }

    private fun refreshLists() {
        viewModelScope.launch {
            runCatchingIo {
                coroutineScope {
                    val n = async { GronkhApi.discovery(Discovery.NEWEST) }
                    val h = async { GronkhApi.discovery(Discovery.HOT) }
                    newest = n.await().distinctById(); hot = h.await().distinctById()
                }
            }.onFailure(showError)
        }
        // Der Zufallsfund wechselt nur beim ersten Laden, sonst springt die Reihe unter dem Fokus weg.
        if (random.isEmpty()) viewModelScope.launch {
            runCatchingIo { GronkhApi.discovery(Discovery.RANDOM) }.onSuccess { random = it.distinctById() }.onFailure(showError)
        }
        viewModelScope.launch {
            runCatchingIo { recentStreams() }
                .onSuccess { recent ->
                    games = gameShelf(recent)
                    currentGames = currentGames(recent).map { category ->
                        category to gameRows.getOrPut(category.slug) {
                            PagedVideos(viewModelScope, showError) { GronkhApi.categoryVideos(category.slug, it) }
                        }
                    }
                    currentGames.forEach { (_, list) -> list.refreshHead() }
                }
                .onFailure(showError)
        }
        mostViewed.refreshHead()
        formats.forEach { (_, list) -> list.refreshHead() }
    }

    /**
     * Die neuesten [RECENT_PAGES] Archivseiten; deren Kapitel ergeben die Spiele. Eine Liste aller Kategorien bietet die
     * Schnittstelle nicht an (categories antwortet 404), nur die Streams je Kategorie. Seite 1 ist zugleich der Kopf
     * von „Alle Streams“ und wird deshalb nur einmal und zuerst geholt: scheitert eine spätere Seite, fehlen nur ein
     * paar Spiele, „Alle Streams“ bleibt gefüllt.
     */
    private suspend fun recentStreams(): List<Video> {
        val first = GronkhApi.videos(Order.NEWEST, 1)
        all.showHead(first)
        val rest = coroutineScope {
            (2..RECENT_PAGES).map { page ->
                async {
                    runCatchingIo { GronkhApi.videos(Order.NEWEST, page).data }
                        .onFailure { Log.w(TAG, "Archivseite $page für die Spiele nicht geladen", it) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        return (first.data + rest.flatten()).distinctById()
    }

    /**
     * Merkt [video] oder nimmt es von der Merkliste. true = jetzt gemerkt. Ein neues Video steht sofort vorn in der Reihe;
     * ein entferntes bleibt bis zum nächsten [refresh] stehen, sonst fiele der Fokus von der Karte ins Leere.
     */
    fun toggleWatchlist(video: Video): Boolean {
        val marked = watchlistStore.toggle(video.id)
        known[video.id] = video
        if (marked && watchlist.none { it.id == video.id }) watchlist = listOf(video) + watchlist
        return marked
    }

    /** Weiterschauen und Merkliste: die IDs liegen lokal, Titel und Bild kommen frisch aus der Schnittstelle. */
    private fun loadLocalRows() = viewModelScope.launch {
        // Fiel ein gelöschtes Video raus, rückt ein weiteres in die ersten n nach und muss auch geladen werden. Endet sicher:
        // jede weitere Runde braucht einen Treffer und eine gelöschte ID, und beides gibt es nur endlich oft.
        do {
            val removed = loadUnknown((startedIds() + watchlistIds()).distinct())
        } while (removed)
        continueWatching = startedIds().mapNotNull { known[it] }.map { Started(it, resume.position(it.id)) }
        // Erst nach dem Laden lesen: wer währenddessen merkt oder entfernt, würde sonst vom alten Stand überschrieben.
        watchlist = watchlistIds().mapNotNull { known[it] }
        localRowsLoaded = true
    }

    private fun startedIds() = resume.startedIds().take(CONTINUE_MAX)
    private fun watchlistIds() = watchlistStore.ids().take(WATCHLIST_MAX)

    /**
     * Holt nur Videos, die noch nicht in [known] stehen; was [idsToForget] nennt, fliegt aus Weiterschauen und Merkliste.
     * true heißt, es ist etwas rausgeflogen. Bei Netzfehlern fehlt nur die Karte, die ID bleibt für das nächste Mal.
     */
    private suspend fun loadUnknown(ids: List<String>): Boolean {
        val gate = Semaphore(DETAIL_PARALLEL)
        val results = coroutineScope {
            ids.filter { it !in known }.map { id -> async { id to gate.withPermit { runCatchingIo { GronkhApi.video(id) } } } }
                .awaitAll()
        }.toMap()
        results.forEach { (id, result) ->
            result.onSuccess { known[id] = it }.onFailure { Log.w(TAG, "Video $id nicht geladen", it) }
        }
        val gone = idsToForget(results)
        gone.forEach { resume.remove(it); watchlistStore.remove(it) }
        return gone.isNotEmpty()
    }

    fun refreshLive() = viewModelScope.launch {
        runCatchingIo { GronkhApi.gronkhLive() }
            .onSuccess { live = it }
            .onFailure { Log.w(TAG, "Live-Status nicht geladen", it) }
    }

    companion object {
        val REFRESH_INTERVAL = 12.hours
        val LIVE_INTERVAL = 2.minutes
        val LISTS_MIN_GAP = 5.minutes
        private const val CONTINUE_MAX = 10
        /** 3 Seiten à 20 = die 60 neuesten Streams, gut ein halbes Jahr. */
        private const val RECENT_PAGES = 3
        // Jedes unbekannte Video ist eine Anfrage; die Schnittstelle erlaubt 180 pro Minute.
        private const val WATCHLIST_MAX = 30
        private const val DETAIL_PARALLEL = 4
        private const val TAG = "GronkhHome"
    }
}

/** Alle Streams eines Spiels; die Schnittstelle filtert selbst nach Kategorie und lädt seitenweise nach. */
class GameViewModel(val category: Category) : ViewModel() {
    var error by mutableStateOf<String?>(null); private set
    val videos = PagedVideos(viewModelScope, { error = it.message ?: it.toString() }) { GronkhApi.categoryVideos(category.slug, it) }

    init { videos.loadMore() }

    /** Nach einem Fehler steht die Seite noch nicht als geladen, loadMore holt also genau sie nochmal. */
    fun retry() {
        error = null
        videos.loadMore()
    }
}

class SearchViewModel : ViewModel() {
    var query by mutableStateOf(""); private set
    var results by mutableStateOf<PagedVideos?>(null); private set
    var error by mutableStateOf<String?>(null); private set

    fun search(text: String) {
        query = text.trim()
        error = null
        if (query.isEmpty()) { results = null; return }
        lateinit var list: PagedVideos
        val term = query
        // Scheitert eine ältere, inzwischen ersetzte Suche, gehört ihr Fehler nicht unter die neue.
        list = PagedVideos(viewModelScope, { if (results === list) error = it.message ?: it.toString() }) {
            GronkhApi.search(term, it)
        }
        results = list
        list.loadMore()
    }
}
