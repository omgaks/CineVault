package com.sole.cinevault.collections

/**
 * Franchise "universes": several TMDB collections shown as ONE page (e.g. Jurassic
 * Park + Jurassic World), optionally with an explicit in-universe "story order".
 *
 * Deliberately no hardcoded TMDB IDs (they can't be trusted to stay right):
 *  - members are TMDB collection NAMES; ids come from the user's own films, or a
 *    one-time cached TMDB name search;
 *  - story order matches films by RELEASE YEAR (read from TMDB's own part list),
 *    with an optional title hint only when two films share a year.
 *
 * To add a franchise, add one entry to [universes] below (GitHub web editor is fine).
 * A name TMDB doesn't know is skipped silently, and a story year that matches no
 * film is skipped, so a wrong entry can never crash or hide a film.
 */
data class StoryKey(val year: Int, val hint: String? = null) {
    fun matches(part: CollectionPart): Boolean {
        val partYear = part.releaseDate?.take(4)?.toIntOrNull() ?: return false
        if (partYear != year) return false
        return hint == null || part.title.contains(hint, ignoreCase = true)
    }
}

data class Universe(
    val id: String,
    val name: String,
    /** TMDB collection names that belong to this universe (all treated as members). */
    val collections: List<String>,
    /** In-universe viewing order; empty = release order is already the story order. */
    val story: List<StoryKey> = emptyList()
) {
    private val memberKeys: Set<String> = collections.map(UniverseCatalog::key).toSet()
    val hasStoryOrder: Boolean get() = story.isNotEmpty()
    fun ownsCollectionName(name: String?): Boolean = name != null && UniverseCatalog.key(name) in memberKeys
}

object UniverseCatalog {

    /** Mirrors the "New collection page" setting; universes only exist on the new page. */
    @Volatile var groupingEnabled: Boolean = true

    /** "The Lord of the Rings Collection" -> "lord of the rings" (tolerates TMDB naming drift). */
    fun key(name: String): String {
        var s = name.lowercase().replace(Regex("\\s+"), " ").trim()
        s = s.removeSuffix(" collection").trim()
        if (s.startsWith("the ")) s = s.removePrefix("the ")
        return s
    }

    fun byId(id: String): Universe? = universes.firstOrNull { it.id == id }

    fun forCollectionName(name: String?): Universe? =
        if (!groupingEnabled || name == null) null else universes.firstOrNull { it.ownsCollectionName(name) }

    val universes: List<Universe> = listOf(
        Universe(
            id = "jurassic",
            name = "Jurassic Park & World",
            collections = listOf("Jurassic Park Collection", "Jurassic World Collection")
        ),
        Universe(
            id = "middle-earth",
            name = "Middle-earth",
            collections = listOf("The Lord of the Rings Collection", "The Hobbit Collection"),
            // The Hobbit trilogy first, then The Lord of the Rings.
            story = years(2012, 2013, 2014, 2001, 2002, 2003)
        ),
        Universe(
            id = "wizarding-world",
            name = "Wizarding World",
            collections = listOf("Harry Potter Collection", "Fantastic Beasts Collection"),
            // Fantastic Beasts is set decades before Harry Potter.
            story = years(2016, 2018, 2022, 2001, 2002, 2004, 2005, 2007, 2009, 2010, 2011)
        ),
        Universe(
            id = "star-wars",
            name = "Star Wars",
            collections = listOf("Star Wars Collection"),
            // Prequels, Solo, Rogue One, original trilogy, sequels.
            story = years(1999, 2002, 2005, 2018, 2016, 1977, 1980, 1983, 2015, 2017, 2019)
        ),
        Universe(
            id = "fast-furious",
            name = "Fast & Furious",
            collections = listOf("The Fast and the Furious Collection", "Fast & Furious Collection"),
            // Tokyo Drift (2006) takes place after Fast & Furious 6 (2013).
            story = years(2001, 2003, 2009, 2011, 2013, 2006, 2015, 2017, 2019, 2021, 2023)
        ),
        Universe(
            id = "hunger-games",
            name = "The Hunger Games",
            collections = listOf("The Hunger Games Collection"),
            // The Ballad of Songbirds & Snakes (2023) is a prequel.
            story = years(2023, 2012, 2013, 2014, 2015)
        ),
        Universe(
            id = "rocky-creed",
            name = "Rocky & Creed",
            collections = listOf("Rocky Collection", "Creed Collection")
        )
    )

    private fun years(vararg y: Int): List<StoryKey> = y.map { StoryKey(it) }
}

/** Keeps "play the whole thing in order" working with a countdown between films. */
object MarathonSession {
    @Volatile private var paths: List<String> = emptyList()

    fun start(orderedPaths: List<String>) {
        paths = if (orderedPaths.size >= 2) orderedPaths.toList() else emptyList()
    }

    fun clear() { paths = emptyList() }

    /** True when the player's list is exactly the marathon the collection page started. */
    fun matches(playerListPaths: List<String>): Boolean {
        val p = paths
        return p.size >= 2 && p == playerListPaths
    }
}
