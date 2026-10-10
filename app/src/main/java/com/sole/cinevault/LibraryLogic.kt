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
    Category("Category"),
    Genre("Genre"),
    Sort("Sort"),
    View("View"),
    Refresh("Refresh"),
    Scan("Scan")
}

internal val LIBRARY_CATEGORIES: List<String> = listOf(
    "All", "Continue Watching", "Movies", "TV Shows", "Folders", "Favorites", "Duplicates", "Secret"
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
    LibraryPanel.Refresh, LibraryPanel.Scan -> false
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
