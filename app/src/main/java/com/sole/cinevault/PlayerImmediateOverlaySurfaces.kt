package com.sole.cinevault

import java.io.File
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import com.sole.cinevault.subtitles.AutoSyncCoordinator
import com.sole.cinevault.subtitles.AutoSyncStatus

/**
 * Slice 60: immediate overlays shown above the video surface.
 *
 * This host owns no mutable state. It only groups the lock layer, Auto-Sync
 * status overlay, and subtitle-delete feedback while routing all mutations
 * back to VideoPlayerScreen.
 */
@Composable
internal fun BoxScope.PlayerImmediateOverlaySurfaces(
    controlsLocked: Boolean,
    lockButtonVisibleWhileLocked: Boolean,
    showControls: Boolean,
    externalDisplayActive: Boolean,
    isLandscape: Boolean,
    haptics: HapticFeedback,
    containerWidth: Dp,
    containerHeight: Dp,
    autoSyncStatus: AutoSyncStatus,
    autoSyncCoordinator: AutoSyncCoordinator,
    pendingDeleteFile: File?,
    snackbarHostState: SnackbarHostState,
    snackbarBottomPadding: Dp,
    onControlsLockedChanged: (Boolean) -> Unit,
    onLockButtonVisibleWhileLockedChanged: (Boolean) -> Unit,
    onAutoSyncStatusChanged: (AutoSyncStatus) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (File) -> Unit,
) {
    PlayerControlsLockLayer(
        controlsLocked = controlsLocked,
        lockButtonVisible =
            !externalDisplayActive &&
                (if (controlsLocked) {
                    lockButtonVisibleWhileLocked
                } else {
                    showControls
                }) &&
                !CineVaultPlayerHolder.isInPipMode,
        isLandscape = isLandscape,
        onLockedSurfaceTap = {
            onLockButtonVisibleWhileLockedChanged(true)
        },
        onToggleLock = {
            haptics.performHapticFeedback(
                HapticFeedbackType.TextHandleMove
            )
            onControlsLockedChanged(!controlsLocked)
            onLockButtonVisibleWhileLockedChanged(true)
        },
    )

    PlayerAutoSyncFloatingOverlay(
        visible = !CineVaultPlayerHolder.isInPipMode,
        containerWidth = containerWidth,
        containerHeight = containerHeight,
        status = autoSyncStatus,
        onApply = { result ->
            autoSyncCoordinator.applyAutoSyncResult(result)
        },
        onCancel = {
            onAutoSyncStatusChanged(AutoSyncStatus.Idle)
        },
        onRetry = {
            autoSyncCoordinator.runAutoSync()
        },
    )

    PlayerSubtitleDeleteFeedback(
        pendingFile = pendingDeleteFile,
        snackbarHostState = snackbarHostState,
        snackbarBottomPadding = snackbarBottomPadding,
        onDismissDelete = onDismissDelete,
        onConfirmDelete = onConfirmDelete,
    )
}
