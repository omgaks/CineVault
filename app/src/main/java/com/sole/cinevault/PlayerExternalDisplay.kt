package com.sole.cinevault

/**
 * Display-facing models that do not own Presentation or PlayerView lifecycle.
 * Device-sensitive surface handoff remains in VideoPlayerScreen until
 * regression testing is available across glasses brands (currently only
 * validated on RayNeo hardware — see notes on multi-brand support).
 */
internal fun buildExternalRatingText(
    currentVideoPath: String,
    episodeList: List<VideoWithMetadata>
): String? = episodeList.firstOrNull { it.video.path == currentVideoPath }?.let { meta ->
    buildList {
        meta.imdbRating
            ?.takeIf { it.isNotBlank() && it != "N/A" }
            ?.let { add("IMDb $it") }
        meta.rottenTomatoesRating
            ?.takeIf { it.isNotBlank() && it != "N/A" }
            ?.let { add("RT $it") }
        meta.rating
            ?.takeIf { it > 0.0 }
            ?.let { add("TMDB ${String.format("%.1f", it)}") }
    }.joinToString("  •  ").ifBlank { null }
}
