package com.sole.cinevault

/**
 * Artwork provenance can be inferred from the stored image URL, so this
 * remains accurate for existing cached items and needs no database migration.
 */
private fun artworkSourceName(url: String?): String {
    val normalized = url.orEmpty().lowercase()
    return when {
        normalized.contains("image.tmdb.org") -> "TMDB"
        normalized.contains("fanart.tv") -> "Fanart.tv"
        normalized.startsWith("http://") || normalized.startsWith("https://") -> "Online"
        else -> "Local fallback"
    }
}

fun artworkSourceSummary(posterUrl: String?, backdropUrl: String?): String {
    return "Poster: ${artworkSourceName(posterUrl)}  •  Backdrop: ${artworkSourceName(backdropUrl)}"
}
