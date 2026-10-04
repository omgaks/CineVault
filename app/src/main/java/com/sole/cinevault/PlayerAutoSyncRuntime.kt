package com.sole.cinevault

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.subtitles.AutoSyncCoordinator
import com.sole.cinevault.subtitles.AutoSyncStatus
import kotlinx.coroutines.CoroutineScope

internal data class PlayerAutoSyncRuntime(
    val available: Boolean,
    val coordinator: AutoSyncCoordinator,
)

@Composable
internal fun rememberPlayerAutoSyncRuntime(
    context: Context,
    scope: CoroutineScope,
    exoPlayer: ExoPlayer,
    currentVideoPath: String,
    isStreamMedia: Boolean,
    trackUi: SubtitleTrackSelectionState,
    coreUi: SubtitleCoreUiState,
    studioUi: SubtitleStudioUiState,
    driftUi: DriftCorrectionState,
    gestureUi: PlayerGestureUiState,
    autoSyncStatus: AutoSyncStatus,
    onAutoSyncStatusChanged: (AutoSyncStatus) -> Unit,
    onAutoSyncSpeechTimelineChanged: (FloatArray?) -> Unit,
): PlayerAutoSyncRuntime {
    val latestVideoPath = rememberUpdatedState(currentVideoPath)
    val latestAutoSyncStatus = rememberUpdatedState(autoSyncStatus)
    val latestPrimarySubtitle = rememberUpdatedState(trackUi.primaryUri)

    val primarySubtitle = trackUi.primaryUri
    val available = isPlayerAutoSyncAvailable(
        primarySubtitleName = primarySubtitle?.lastPathSegment ?: primarySubtitle?.toString(),
        isStreamMedia = isStreamMedia,
        videoPath = currentVideoPath,
    )

    val coordinator = remember(exoPlayer) {
        AutoSyncCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            getPrimarySubtitleUri = { latestPrimarySubtitle.value },
            getCurrentVideoPath = { latestVideoPath.value },
            getAutoSyncStatus = { latestAutoSyncStatus.value },
            setAutoSyncStatus = onAutoSyncStatusChanged,
            resetPreviewFrames = {
                gestureUi.previewFrames = emptyList()
                gestureUi.previewBitmap = null
            },
            incrementPreviewReloadKey = { gestureUi.previewReloadKey++ },
            setSyncOffsetSeconds = { coreUi.syncOffset = it },
            setDriftScale = { driftUi.scale = it },
            incrementStudioMenuTouchKey = { studioUi.menuTouchKey++ },
            setSpeechTimeline = onAutoSyncSpeechTimelineChanged,
        )
    }

    DisposableEffect(coordinator, currentVideoPath, primarySubtitle) {
        onDispose {
            coordinator.cancelAutoSync()
        }
    }

    return PlayerAutoSyncRuntime(
        available = available,
        coordinator = coordinator,
    )
}
