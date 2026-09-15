package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps FFmpeg-first audio rescue scoped to one active player-screen session.
 *
 * Path changes clear a previous title's rescue. Leaving the player screen also
 * clears a rescue owned by this title, so reopening the same file later starts
 * again from CineVault's normal PLATFORM_FIRST path.
 */
@Composable
internal fun AudioRuntimeRescueVideoScopeEffect(
    videoPath: String,
) {
    DisposableEffect(videoPath) {
        AudioRuntimeRescueController.resetForVideo(videoPath)

        onDispose {
            AudioRuntimeRescueController.endVideoScope(videoPath)
        }
    }
}
