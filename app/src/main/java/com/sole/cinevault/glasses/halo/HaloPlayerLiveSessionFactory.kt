package com.sole.cinevault.glasses.halo

/**
 * D2-17 — stable live-session factory for the PlayerPlaybackGestureLayer cutover.
 *
 * The caller supplies the same canonical callbacks already used by CineVault.
 * Keeping construction here makes the next PlayerPlaybackGestureLayer edit small
 * and prevents that composable from knowing the internal D2-11..D2-15 chain.
 */
object HaloPlayerLiveSessionFactory {
    fun create(
        brightnessDrag: (Float) -> Unit,
        volumeDrag: (Float) -> Unit,
        seekTo: (Long) -> Unit,
        userActivity: () -> Unit,
    ): HaloPlayerLiveSession =
        HaloPlayerLiveSession(
            brightnessDrag = brightnessDrag,
            volumeDrag = volumeDrag,
            seekTo = seekTo,
            userActivity = userActivity,
        )
}
