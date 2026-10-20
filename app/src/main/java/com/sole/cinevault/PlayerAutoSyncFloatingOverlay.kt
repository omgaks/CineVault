package com.sole.cinevault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.subtitles.AutoSyncStatus
import com.sole.cinevault.subtitles.SubtitleSyncResult

@Composable
internal fun BoxScope.PlayerAutoSyncFloatingOverlay(
    visible: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    status: AutoSyncStatus,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    if (!visible) return

    // Floating status pills stack under the last right-hand pill, always.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = PlayerPillMetrics.floatingTop(containerWidth > containerHeight), end = PlayerPillMetrics.end)
    ) {
        AutoSyncFloatingIndicator(
            status = status,
            onApply = onApply,
            onCancel = onCancel,
            onRetry = onRetry
        )
    }
}
