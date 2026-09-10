package com.sole.cinevault

fun shouldResetNextEpisodeOverlay(
    showNextEpisodeOverlay: Boolean,
    creditsStartMs: Long?,
    position: Long,
): Boolean {
    val creditsStart = creditsStartMs ?: return false
    return showNextEpisodeOverlay && position < creditsStart
}
