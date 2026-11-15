package com.sole.cinevault

/**
 * Builds the list the player may auto-advance through.
 *
 * - Secret / restricted items are never pulled in as "next" from a normal
 *   video, and a Secret / restricted video only ever plays by itself.
 * - A TV episode gets the other episodes of the same show, in order.
 * - Anything else gets the visible library (so "next" still works for movies).
 */
internal fun buildPlaybackPlaylist(
    selected: VideoWithMetadata,
    library: List<VideoWithMetadata>,
    isHidden: (VideoWithMetadata) -> Boolean
): List<VideoWithMetadata> {
    if (isHidden(selected)) return listOf(selected)
    val visible = library.filter { !isHidden(it) }
    if (selected.type != "tv") return visible
    val sameShow = visible
        .filter { it.type == "tv" && it.title == selected.title }
        .sortedBy { it.subtitle }
    return if (sameShow.any { it.video.path == selected.video.path }) sameShow else visible
}
