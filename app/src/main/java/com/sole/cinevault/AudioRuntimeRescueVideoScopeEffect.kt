package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps FFmpeg-first audio rescue scoped to one active player-screen session.
 *
 * Entering a video validates the complete rescue state, not only the stored
 * path. This also clears orphaned pending plans or orphaned FFmpeg-first mode.
 *
 * Leaving the player screen clears a rescue owned by this title, so reopening
 * the same file later starts again from CineVault's normal PLATFORM_FIRST path.
 */
@Composable
internal fun AudioRuntimeRescueVideoScopeEffect(
    videoPath: String,
) {
    DisposableEffect(videoPath) {
        AudioRuntimeRescueController.enterVideoScope(videoPath)

        onDispose {
            AudioRuntimeRescueController.endVideoScope(videoPath)
        }
    }
}
