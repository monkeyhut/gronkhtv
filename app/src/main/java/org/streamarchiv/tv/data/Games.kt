package org.streamarchiv.tv.data

/** Ein Spiel oder Thema über mehrere Streams: in wie vielen es als Kapitel vorkommt. */
data class GameStats(val category: Category, val streams: Int)

/**
 * Kategorien, die keine Spiele sind (gemessen an den 60 neuesten Streams, 21.10.2026). Die igdb_id trennt nicht sauber,
 * auch echte Spiele haben dort manchmal nichts.
 */
private val THEMES = setOf("Just Chatting", "Special Events", "Games + Demos", "Summer Game Fest")

/** Steht in 56 von 60 Streams und taugt deshalb nicht als eigene Kachel. */
private const val EVERYWHERE = "Just Chatting"

val Category.isTheme: Boolean get() = title in THEMES

/** Alle Kategorien aus [videos] (neueste zuerst) nach Zahl der Streams; bei Gleichstand steht die zuletzt gespielte vorn. */
fun rankCategories(videos: List<Video>): List<GameStats> {
    // Einfügereihenfolge = erstes Vorkommen = jüngster Stream; die stabile Sortierung behält sie bei Gleichstand.
    val stats = LinkedHashMap<String, GameStats>()
    for (video in videos) {
        val counted = HashSet<String>()
        for (chapter in video.chapters) {
            val category = chapter.category ?: continue
            if (counted.add(category.slug)) stats[category.slug] = GameStats(category, (stats[category.slug]?.streams ?: 0) + 1)
        }
    }
    return stats.values.sortedByDescending { it.streams }
}

/** Kacheln der Startseite: erst die Spiele, dahinter die Themen ohne „Just Chatting“. */
fun gameShelf(videos: List<Video>): List<GameStats> {
    val (themes, games) = rankCategories(videos).partition { it.category.isTheme }
    return games + themes.filter { it.category.title != EVERYWHERE }
}

/** Die aktuell meistgespielten Spiele für eigene Reihen: gezählt nur in den [window] neuesten Streams, ohne Themen. */
fun currentGames(videos: List<Video>, window: Int = 20, count: Int = 3): List<Category> =
    rankCategories(videos.take(window)).filterNot { it.category.isTheme }.take(count).map { it.category }

/** Das erste Kapitel von [slug] in diesem Stream; ein Spiel kann, von Pausen unterbrochen, mehrfach vorkommen. */
fun Video.chapterOf(slug: String): Chapter? = chapters.firstOrNull { it.category?.slug == slug }
