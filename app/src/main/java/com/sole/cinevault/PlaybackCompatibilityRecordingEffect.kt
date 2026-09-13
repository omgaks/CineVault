package com.sole.cinevault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sole.cinevault.library.VideoFile

/**
 * Live bridge between the player recovery state and CineVault's compatibility
 * session recorder.
 *
 * Slice 101 adds stable named torture-file cases. A filename using the
 * CVTEST__<id>__<label> convention is recorded by that stable id regardless
 * of which folder/device the suite is copied to. Normal media keeps the
 * existing path-based identity.
 */
@Composable
fun PlaybackCompatibilityRecordingEffect(
    currentVideo: VideoFile,
    recoveryState: PlayerPlaybackRecoveryState,
    recorder: PlaybackCompatibilitySessionRecorder,
) {
    val snapshot = recoveryState.playbackDiagnosticsSnapshot

    val resolvedCase = resolvePlaybackCompatibilityTestCase(
        path = currentVideo.path,
        displayName = currentVideo.name,
    )

    LaunchedEffect(
        currentVideo.path,
        resolvedCase.testCase.testId,
        snapshot,
    ) {
        recorder.record(
            testCase = resolvedCase.testCase,
            snapshot = snapshot,
        )
    }
}
