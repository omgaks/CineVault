package com.sole.cinevault.glasses.stereo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/**
 * Bridges D12 stereo policy to the canonical CineVault Player.
 * No second player, surface or playback timeline is created.
 */
internal data class StereoPlaybackRuntimeSnapshot(
    val decision: StereoPlaybackDecision,
    val sourcePath: String,
)

@Composable
internal fun rememberStereoPlaybackRuntime(
    player: Player,
    externalDisplayDestination: Boolean,
): StereoPlaybackRuntimeSnapshot {
    val initialPath = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
    val session = remember(player) {
        StereoPlaybackSession(
            fileName = initialPath.substringAfterLast('/'),
            path = initialPath,
        )
    }

    var sourcePath by remember(player) { mutableStateOf(initialPath) }
    var revision by remember(player) { mutableStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val path = mediaItem?.localConfiguration?.uri?.toString().orEmpty()
                sourcePath = path
                session.onVideoChanged(path.substringAfterLast('/'), path)
                val size = player.videoSize
                if (size.width > 0 && size.height > 0) {
                    session.updateVideoDimensions(size.width, size.height)
                }
                revision++
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    session.updateVideoDimensions(videoSize.width, videoSize.height)
                    revision++
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(initialPath) {
        if (initialPath != sourcePath) {
            sourcePath = initialPath
            session.onVideoChanged(initialPath.substringAfterLast('/'), initialPath)
            revision++
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val observedRevision = revision

    return StereoPlaybackRuntimeSnapshot(
        decision = session.resolve(externalDisplayActive = externalDisplayDestination),
        sourcePath = sourcePath,
    )
}
