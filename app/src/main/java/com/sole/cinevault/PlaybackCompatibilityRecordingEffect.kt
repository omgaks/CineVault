package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sole.cinevault.library.VideoFile

/**
 * Live bridge between the player recovery state and CineVault's compatibility
 * session recorder.
 *
 * The recovery state already owns the authoritative decoder/health/fallback
 * signals. This effect simply snapshots those signals whenever they change
 * and records the latest meaningful result for the current video.
 */
@Composable
fun PlaybackCompatibilityRecordingEffect(
    currentVideo: VideoFile,
    recoveryState: PlayerPlaybackRecoveryState,
    recorder: PlaybackCompatibilitySessionRecorder,
) {
    val snapshot = recoveryState.playbackDiagnosticsSnapshot
    val testCase = PlaybackCompatibilityTestCase(
        testId = currentVideo.path,
        sourceLabel = currentVideo.name
            .takeIf { it.isNotBlank() }
            ?: currentVideo.path.substringAfterLast('/'),
    )

    LaunchedEffect(
        currentVideo.path,
        snapshot,
    ) {
        recorder.record(
            testCase = testCase,
            snapshot = snapshot,
        )
    }
}
