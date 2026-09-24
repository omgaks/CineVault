package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.library.VideoFile

/**
 * Owns the player-session compatibility recorder and keeps it fed with the
 * current video's live recovery/diagnostics snapshot.
 *
 * The recorder deliberately survives video changes within the same player
 * session so compatibility observations can accumulate without moving this
 * persistence wiring back into VideoPlayerScreen.
 */
@Composable
fun rememberPlayerPlaybackCompatibilityRuntime(
    currentVideo: VideoFile,
    recoveryState: PlayerPlaybackRecoveryState,
): PlaybackCompatibilitySessionRecorder {
    val context = LocalContext.current
    val recorder = remember {
        val device = currentPlaybackCompatibilityDevice()
        val store = PlaybackCompatibilityStore(context)

        PlaybackCompatibilitySessionRecorder(
            device = device,
            initialEntries = store.load(device),
            onEntriesChanged = store::save,
        )
    }

    PlaybackCompatibilityRecordingEffect(
        currentVideo = currentVideo,
        recoveryState = recoveryState,
        recorder = recorder,
    )

    return recorder
}

