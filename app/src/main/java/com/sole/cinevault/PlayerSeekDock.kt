package com.sole.cinevault

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.glassPanel

@Composable
internal fun BoxScope.PlayerSeekDock(
    showSeekPreview: Boolean,
    previewBitmap: Bitmap?,
    previewPosition: Long,
    duration: Long,
    isLandscape: Boolean,
    isSeekPreviewLarge: Boolean,
    seekBottomPadding: Dp,
    sidePadding: Dp,
    scale: Float,
    position: Long,
    isDraggingSeekbar: Boolean,
    seed: Int,
    onPreviewPositionChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
) {
    SeekPreviewBubble(
        isVisible = showSeekPreview,
        bitmap = previewBitmap,
        timeText = formatTime(previewPosition),
        isLandscape = isLandscape,
        isLarge = isSeekPreviewLarge,
        progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
        bottomPadding = seekBottomPadding + (38f + 14f * scale).dp + 18.dp,
    )

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = sidePadding,
                end = sidePadding,
                bottom = seekBottomPadding
            )
            .glassPanel(
                cornerRadius = 30.dp,
                fill = GlassSurface
            )
            .padding(
                horizontal = (14 * scale).dp,
                vertical = (7 * scale).dp
            )
    ) {
        CinematicSeekBar(
            position = position,
            duration = duration,
            isDragging = isDraggingSeekbar,
            seed = seed,
            onPreviewPositionChanged = onPreviewPositionChanged,
            onSeekFinished = onSeekFinished
        )
    }
}
