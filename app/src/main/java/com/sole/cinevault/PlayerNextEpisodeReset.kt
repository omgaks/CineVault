package com.sole.cinevault

internal fun shouldResetNextEpisodeOverlay(
    showNextEpisodeOverlay: Boolean,
    creditsStartMs: Long?,
    position: Long,
): Boolean {
    val creditsStart = creditsStartMs ?: return false
    return showNextEpisodeOverlay && position < creditsStart
}
