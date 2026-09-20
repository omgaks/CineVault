package com.sole.cinevault.glasses.halo

/**
 * D2-14 — concrete adapter from HaloPlayerActionBridge to the canonical
 * CineVault player callbacks.
 *
 * No player/UI state is duplicated here. The owner supplies the SAME callbacks
 * already used by PlayerPlaybackGestureLayer / VideoPlayerScreen.
 */
class HaloPlayerCanonicalActions(
    private val brightnessDrag: (Float) -> Unit,
    private val volumeDrag: (Float) -> Unit,
    private val seekTo: (Long) -> Unit,
    private val userActivity: () -> Unit,
) : HaloCanonicalPlayerActions {

    override fun onBrightnessDrag(deltaYPx: Float) {
        brightnessDrag(deltaYPx)
    }

    override fun onVolumeDrag(deltaYPx: Float) {
        volumeDrag(deltaYPx)
    }

    override fun onSeekTo(positionMs: Long) {
        seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun onUserActivity() {
        userActivity()
    }
}
