package com.sole.cinevault

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sole.cinevault.segments.SeekMarkerKind
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.segments.SeekMarker
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
    markers: List<SeekMarker> = emptyList(),
    bufferedProvider: () -> Long = { 0L },
) {
    var originMs by remember { mutableLongStateOf(0L) }
    var fine by remember { mutableFloatStateOf(1f) }
    var buffered by remember { mutableLongStateOf(0L) }
    var showRemaining by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { buffered = bufferedProvider(); delay(1000) }
    }
    val frac = if (duration > 0L) previewPosition.toFloat() / duration else 0f
    val bandLabel = markers.firstOrNull { it.endFraction > it.startFraction && frac in it.startFraction..it.endFraction }?.let {
        if (it.kind == SeekMarkerKind.CREDITS) "CREDITS" else "INTRO / RECAP"
    }
    SeekPreviewBubble(
        isVisible = showSeekPreview,
        bitmap = previewBitmap,
        timeText = formatTime(previewPosition),
        isLandscape = isLandscape,
        isLarge = isSeekPreviewLarge,
        progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
        bottomPadding = seekBottomPadding + (38f + 14f * scale).dp + 18.dp,
        deltaMs = previewPosition - originMs,
        fine = fine,
        label = bandLabel,
    )

    val dockShape = HudShape
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = sidePadding, end = sidePadding, bottom = seekBottomPadding)
            .shadow(10.dp, dockShape, ambientColor = AmberCore, spotColor = AmberCore)
            .clip(dockShape)
            .background(GlassSurfaceStrong)
            .scanlines()
            .border(1.5.dp, HudEdge, dockShape)
            .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(if (isDraggingSeekbar) previewPosition else position),
            color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(AmberGlow, blurRadius = 12f)),
        )
        Spacer(Modifier.width((10 * scale).dp))
        Box(Modifier.weight(1f)) {
            CinematicSeekBar(
                position = position,
                duration = duration,
                isDragging = isDraggingSeekbar,
                seed = seed,
                onPreviewPositionChanged = onPreviewPositionChanged,
                onSeekFinished = onSeekFinished,
                markers = markers,
                bufferedMs = buffered,
                onScrub = { o, f -> originMs = o; fine = f },
            )
        }
        Spacer(Modifier.width((10 * scale).dp))
        Text(
            text = if (showRemaining) "−" + formatTime((duration - position).coerceAtLeast(0L)) else formatTime(duration),
            color = Color(0xFF6FC3FF), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0xFF6FC3FF), blurRadius = 12f)),
            modifier = Modifier.clickable { showRemaining = !showRemaining },
        )
    }
}
