package com.sole.cinevault

data class PlaybackInfoVisibility(
    val showPanel: Boolean,
    val showRecoverySection: Boolean,
)

fun playbackInfoVisibility(
    panelRequested: Boolean,
    fallbackOccurred: Boolean,
): PlaybackInfoVisibility {
    return PlaybackInfoVisibility(
        showPanel = panelRequested,
        showRecoverySection = panelRequested && fallbackOccurred,
    )
}
