package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ── Studio tabs ───────────────────────────────────────────────────────────
// FIX (UI redesign round): was TRACK/SYNC/STYLE/POSITION/ADVANCED (5 tabs,
// with Position and Style artificially split even though the approved
// mockup's own Style panel example shows Text Size and Vertical Offset
// together in one place) — now 5 tabs matching the 6-tile Tools Grid minus
// "Find" (which isn't a Studio tab at all; it closes Studio and opens the
// separate Search sheet instead, same as it always has). Position's
// controls now live inside Appearance; Dual Subtitles — previously buried
// at the bottom of the old catch-all Advanced tab — is its own tool now.
// MANAGE added this round — lists every language already downloaded for
// THIS video (via OpenSubtitlesClient.listCachedSubtitlesForVideo) with a
// delete action per language, addressing "no way to manage downloaded
// subtitles" directly instead of leaving cached files to just accumulate
// silently.

@Composable
internal fun StudioAppearanceTab(
    presetName: String,
    appearance: SubtitleAppearance,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onApplyPreset: (String, SubtitleAppearance) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onEdgeColorChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    isAssOrSsaFormat: Boolean,
    preserveOriginalStyling: Boolean,
    onPreserveOriginalStylingChange: (Boolean) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit
) {
    SubtitleAppearanceStudioSheet(
        presetName = presetName,
        appearance = appearance,
        fontSizeSp = fontSizeSp,
        popupWidth = popupWidth,
        popupMaxHeight = popupMaxHeight,
        onApplyPreset = onApplyPreset,
        onForegroundChange = onForegroundChange,
        onEdgeTypeChange = onEdgeTypeChange,
        onEdgeColorChange = onEdgeColorChange,
        onBackgroundChange = onBackgroundChange,
        isAssOrSsaFormat = isAssOrSsaFormat,
        preserveOriginalStyling = preserveOriginalStyling,
        onPreserveOriginalStylingChange = onPreserveOriginalStylingChange,
        onFontSizeChange = onFontSizeChange,
        bottomPadding = bottomPadding,
        onBottomPaddingChange = onBottomPaddingChange,
        onDismiss = {}
    )
}

@Composable
private fun DotThumbSliderDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

// Shared dot-thumb slider — same visual as WaveformSlider's fallback
// track (SubtitleStudioTimingTab.kt) but without the waveform overlay,
// since these two controls (text size, vertical position) have no audio
// signal to show. Kept here rather than exported from Timing so this file
// doesn't have to depend on Timing's audio-specific WaveformSlider.
@Composable
internal fun DotThumbSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    fun fractionFor(x: Float) = (x / widthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    fun valueFor(fraction: Float) = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)

    Box(
        modifier = modifier
            .height(20.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat() }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ -> onValueChange(valueFor(fractionFor(change.position.x))) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.12f))
        )
        val thumbFraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { (thumbFraction * widthPx).toDp() })
                    .background(AmberGlow)
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbFraction * widthPx).roundToInt() - with(density) { 7.dp.roundToPx() }, 0) }
                .size(14.dp)
                .clip(CircleShape)
                .background(AmberCore)
                .border(2.dp, Color(0xFF1A1206), CircleShape)
        )
    }
}
