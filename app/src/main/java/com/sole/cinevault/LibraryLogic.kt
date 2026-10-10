package com.sole.cinevault

/**
 * Plain, testable rules for the Library screen. No Android types here.
 */

/** Three ways to show the films. */
enum class LibraryViewMode(val label: String) {
    Grid("Grid"),
    Details("Details"),
    Compact("Compact")
}

/** Each button in the floating pill opens one of these cards. */
enum class LibraryPanel(val title: String) {
    Search("Search"),
    Category("Category"),
    Genre("Genre"),
    Sort("Sort"),
    View("View"),
    Refresh("Refresh"),
    Scan("Scan")
}

internal val LIBRARY_CATEGORIES: List<String> = listOf(
    "All", "Continue Watching", "Movies", "TV Shows", "Folders", "Favorites", "Secret"
)

/** A remembered category that no longer exists (for example Downloads) falls back to All. */
internal fun normalizeLibraryCategory(saved: String): String =
    if (saved in LIBRARY_CATEGORIES) saved else "All"

/**
 * Risky panels (Refresh) only respond to their buttons. Everything else
 * closes when you tap outside.
 */
internal fun panelClosesOnOutsideTap(panel: LibraryPanel): Boolean = panel != LibraryPanel.Refresh

/** Panels where picking one option is the whole job, so the card closes itself. */
internal fun panelClosesAfterChoice(panel: LibraryPanel): Boolean = when (panel) {
    LibraryPanel.Category, LibraryPanel.Genre, LibraryPanel.Sort, LibraryPanel.View -> true
    LibraryPanel.Search, LibraryPanel.Refresh, LibraryPanel.Scan -> false
}

/** Best-effort year taken from a file name, or null when there isn't one. */
internal fun extractYearFromName(fileName: String): String? =
    Regex("\\b(19|20)\\d{2}\\b").find(fileName)?.value

/** Joins the parts that exist with a middle dot: "2018 · Sci-Fi, Action". */
internal fun joinMeta(vararg parts: String?): String =
    parts.mapNotNull { it?.trim()?.takeIf { text -> text.isNotEmpty() } }.joinToString(" · ")

/** Columns that fit a window: posters stay at least 100dp wide. */
internal fun libraryColumnsFor(windowWidthDp: Float, horizontalPaddingDp: Float = 32f): Int =
    com.sole.cinevault.ui.theme.adaptiveColumnCount(windowWidthDp - horizontalPaddingDp, minCardDp = 100f, gapDp = 12f)

/**
 * Other screens (a collection page's "search my library") can ask Library to
 * open with its Search card already filled in.
 */
object LibrarySearchRequest {
    var pending: String? = null
}

// ── Duplicates: show the best copy, keep the rest one tap away ─────────────

internal data class CopyInfo(val path: String, val name: String, val sizeBytes: Long)

/** Higher is better. Resolution matters most, then HDR and HEVC. */
internal fun qualityRank(fileName: String): Int {
    val lower = fileName.lowercase()
    var rank = when {
        lower.contains("2160p") || lower.contains("4k") -> 40
        lower.contains("1080p") -> 30
        lower.contains("720p") -> 20
        else -> 10
    }
    if (lower.contains("hdr")) rank += 5
    if (lower.contains("hevc") || lower.contains("x265") || lower.contains("h265")) rank += 2
    return rank
}

/**
 * The copy to show. A copy the person chose ("Make this the main one") wins;
 * otherwise the highest quality, then the bigger file, then the path.
 */
internal fun chooseMainCopy(copies: List<CopyInfo>, preferredPaths: Set<String>): CopyInfo =
    copies.firstOrNull { it.path in preferredPaths }
        ?: copies.sortedWith(
            compareByDescending<CopyInfo> { qualityRank(it.name) }
                .thenByDescending { it.sizeBytes }
                .thenBy { it.path }
        ).first()

internal data class FoldPlan(
    /** Copies that are tucked away behind their main copy. */
    val hiddenPaths: Set<String>,
    /** Main copy path -> every path in its group (main included). */
    val groupsByMain: Map<String, List<String>>
)

internal fun planDuplicateFold(groups: List<List<CopyInfo>>, preferredPaths: Set<String>): FoldPlan {
    val hidden = mutableSetOf<String>()
    val byMain = linkedMapOf<String, List<String>>()
    groups.filter { it.size > 1 }.forEach { group ->
        val main = chooseMainCopy(group, preferredPaths)
        byMain[main.path] = group.map { it.path }
        group.forEach { if (it.path != main.path) hidden += it.path }
    }
    return FoldPlan(hidden, byMain)
}

/** Remembers a choice: this path is the main one, and the others in its group are not. */
internal fun withMainCopy(preferred: Set<String>, mainPath: String, groupPaths: List<String>): Set<String> =
    (preferred - groupPaths.toSet()) + mainPath

// ── Search ────────────────────────────────────────────────────────────────

internal data class SearchDoc(
    val title: String,
    val fileName: String,
    val cast: List<String>,
    val director: String?,
    val genres: List<String>,
    val year: String?
)

/** Which field a search word matched, best first. */
internal enum class SearchMatch(val label: String) {
    Title("Title"), Cast("Cast"), Director("Director"), Genre("Genre"), Year("Year"), File("File")
}

internal data class SearchHit(val matchedBy: SearchMatch, val rank: Int)

private fun String.hasWord(token: String) = this.lowercase().contains(token)

private fun matchToken(doc: SearchDoc, token: String): SearchHit? {
    val title = doc.title.lowercase()
    return when {
        title.startsWith(token) -> SearchHit(SearchMatch.Title, 0)
        title.contains(token) -> SearchHit(SearchMatch.Title, 1)
        doc.cast.any { it.hasWord(token) } -> SearchHit(SearchMatch.Cast, 2)
        doc.director?.hasWord(token) == true -> SearchHit(SearchMatch.Director, 3)
        doc.genres.any { it.hasWord(token) } -> SearchHit(SearchMatch.Genre, 4)
        doc.year?.contains(token) == true -> SearchHit(SearchMatch.Year, 5)
        doc.fileName.hasWord(token) -> SearchHit(SearchMatch.File, 6)
        else -> null
    }
}

/**
 * Every word you type must match something. The result is tagged with where
 * the first word matched, and ranked by the weakest match among the words.
 */
internal fun searchDocument(doc: SearchDoc, query: String): SearchHit? {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    val hits = tokens.map { matchToken(doc, it) ?: return null }
    return SearchHit(hits.first().matchedBy, hits.maxOf { it.rank })
}

/** Quick filters shown as chips under the search box. */
internal data class SearchFilters(
    val films: Boolean = false,
    val rating7: Boolean = false,
    val fourKOrHdr: Boolean = false,
    val unwatched: Boolean = false,
    val favourites: Boolean = false
) {
    val anyOn: Boolean get() = films || rating7 || fourKOrHdr || unwatched || favourites
}

internal fun isFourKOrHdr(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return lower.contains("2160p") || lower.contains("4k") || lower.contains("hdr")
}

internal fun passesSearchFilters(
    filters: SearchFilters,
    isFilm: Boolean,
    rating: Double?,
    fileName: String,
    watched: Boolean,
    favourite: Boolean
): Boolean =
    (!filters.films || isFilm) &&
        (!filters.rating7 || (rating ?: 0.0) >= 7.0) &&
        (!filters.fourKOrHdr || isFourKOrHdr(fileName)) &&
        (!filters.unwatched || !watched) &&
        (!filters.favourites || favourite)

/** Newest first, no repeats (ignoring case), at most [max]. Blank queries are ignored. */
internal fun pushRecentSearch(recents: List<String>, query: String, max: Int = 6): List<String> {
    val clean = query.trim()
    if (clean.isEmpty()) return recents
    return (listOf(clean) + recents.filterNot { it.equals(clean, ignoreCase = true) }).take(max)
}
