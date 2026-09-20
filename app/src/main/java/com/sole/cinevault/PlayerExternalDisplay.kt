package com.sole.cinevault

/**
 * Display-facing models that do not own Presentation or PlayerView lifecycle.
 *
 * Surface handoff is vendor-neutral. Any compatible secondary display uses
 * the same external-display contract.
 */
internal fun buildExternalRatingText(
    currentVideoPath: String,
    episodeList: List<VideoWithMetadata>
): String? = episodeList.firstOrNull { it.video.path == currentVideoPath }?.let { meta ->
    buildList {
        meta.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }?.let { add("IMDb $it") }
        meta.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }?.let { add("RT $it") }
        meta.rating?.takeIf { it > 0.0 }?.let { add("TMDB ${String.format("%.1f", it)}") }
    }.joinToString("  •  ").ifBlank { null }
}
