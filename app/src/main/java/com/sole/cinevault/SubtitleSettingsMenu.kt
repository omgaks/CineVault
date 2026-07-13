package com.sole.cinevault

import com.sole.cinevault.ui.theme.*
import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    hasInternalSubtitles: Boolean,
    onInternalClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPickFileClick: () -> Unit = {},
    onToggleSubtitles: () -> Unit,
    onDismiss: () -> Unit,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentVerticalPosition: Float,
    onVerticalPositionChange: (Float) -> Unit,
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onReset: () -> Unit,
    onUserInteraction: () -> Unit
) {
    if (!isVisible) return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // RESPONSIVE SIZING — width halved from the original pass to save space.
    // Height still caps at a fraction of the screen; verticalScroll below
    // catches anything left over.
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val popupWidth: Dp = if (isLandscape) {
        (screenWidthDp * 0.17f).dp.coerceIn(115.dp, 150.dp)
    } else {
        (screenWidthDp * 0.35f).dp.coerceIn(120.dp, 170.dp)
    }
    val panelMaxHeight: Dp = if (isLandscape) {
        (screenHeightDp * 0.78f).dp.coerceAtMost(230.dp)
    } else {
        (screenHeightDp * 0.55f).dp.coerceAtMost(400.dp)
    }

    val rowGap = if (isLandscape) 4.dp else 6.dp
    val titleSize = if (isLandscape) 11.sp else 12.sp
    val labelSize = if (isLandscape) 9.sp else 10.sp
    val valueSize = if (isLandscape) 8.sp else 9.sp

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = panelMaxHeight)
            .glassPanel(cornerRadius = if (isLandscape) 16.dp else 18.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .clickable { onUserInteraction() }
            .padding(horizontal = if (isLandscape) 8.dp else 9.dp, vertical = if (isLandscape) 7.dp else 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Subtitles", color = AmberCore, fontSize = titleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { onUserInteraction(); onDismiss() }, modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp).clip(CircleShape).background(GlassSurface)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextBright, modifier = Modifier.size(if (isLandscape) 11.dp else 12.dp))
            }
        }
        // "Internal" was removed — Browse does the same job (opens the file/
        // track picker), and dropping it frees up more breathing room at
        // this width.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SubtitleTopChip(text = "Download", selected = false, isLandscape = isLandscape) { onUserInteraction(); onDownloadClick() }
            SubtitleTopChip(text = if (subtitlesEnabled) "Turn OFF" else "Turn ON", selected = !subtitlesEnabled, isLandscape = isLandscape) { onUserInteraction(); onToggleSubtitles() }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(GlassSurface)
                .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(10.dp))
                .clickable { onUserInteraction(); onPickFileClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = AmberCore, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Browse files", color = TextBright, fontSize = labelSize, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(color = GlassBorderBottom)
        CompactSliderRow(label = "Text Size", valueLabel = "${currentFontSize.toInt()}sp", value = currentFontSize, valueRange = 12f..32f, labelSize = labelSize, valueSize = valueSize) { onUserInteraction(); onFontSizeChange(it) }
        CompactSliderRow(label = "Position", valueLabel = "${(currentVerticalPosition * 100).toInt()}%", value = currentVerticalPosition, valueRange = 0.02f..0.30f, labelSize = labelSize, valueSize = valueSize) { onUserInteraction(); onVerticalPositionChange(it) }
        CompactSyncRow(currentSyncOffset = currentSyncOffset, isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize, onUserInteraction = onUserInteraction, onSyncOffsetChange = onSyncOffsetChange)
        HorizontalDivider(color = GlassBorderBottom)
        TextButton(onClick = { onUserInteraction(); onReset() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
            Text(text = "Reset", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// A genuinely thin slider — Material3's Slider always reserves a large touch
// target no matter how short its Modifier.height is set, which is what was
// eating vertical space. This draws just a thin rounded track + small thumb
// directly, at whatever height is asked for (12-16dp instead of 26-34dp).
@Composable
private fun ThinSliderBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp
) {
    fun valueFromFraction(fraction: Float): Float =
        valueRange.start + fraction.coerceIn(0f, 1f) * (valueRange.endInclusive - valueRange.start)

    // Only the thumb (the small dot) responds to touch/drag now — anywhere
    // else on the bar lets the gesture pass straight through to the popup's
    // verticalScroll. Previously ANY touch on the bar (including the start of
    // a vertical scroll swipe) was captured as a horizontal drag, which is
    // what made scrolling this menu feel broken, especially in landscape
    // where the popup is short and packed tight with sliders.
    val currentValue = rememberUpdatedState(value)
    val currentOnChange = rememberUpdatedState(onValueChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(valueRange) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val fraction = ((currentValue.value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                    val thumbX = size.width * fraction
                    val hitRadiusPx = 22.dp.toPx()
                    if (kotlin.math.abs(down.position.x - thumbX) > hitRadiusPx) {
                        // Missed the thumb — don't consume, let it fall through to scroll.
                        return@awaitEachGesture
                    }
                    down.consume()
                    currentOnChange.value(valueFromFraction(down.position.x / size.width.toFloat()))
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentOnChange.value(valueFromFraction(change.position.x / size.width.toFloat()))
                    }
                }
            }
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = (size.height * 0.30f).coerceAtLeast(2f)
            val cy = size.height / 2f
            val r = CornerRadius(trackH / 2f, trackH / 2f)
            drawRoundRect(color = Color.White.copy(alpha = 0.18f), topLeft = Offset(0f, cy - trackH / 2f), size = Size(size.width, trackH), cornerRadius = r)
            drawRoundRect(color = AmberGlow, topLeft = Offset(0f, cy - trackH / 2f), size = Size(size.width * fraction, trackH), cornerRadius = r)
            drawCircle(color = AmberCore, radius = size.height * 0.46f, center = Offset(size.width * fraction, cy))
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = size.height * 0.16f, center = Offset(size.width * fraction, cy))
        }
    }
}

@Composable
private fun CompactSliderRow(label: String, valueLabel: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, labelSize: TextUnit, valueSize: TextUnit, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = TextBright, fontSize = labelSize, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = valueLabel, color = AmberCore, fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        ThinSliderBar(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun CompactSyncRow(currentSyncOffset: Float, isLandscape: Boolean, labelSize: TextUnit, valueSize: TextUnit, onUserInteraction: () -> Unit, onSyncOffsetChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Sync", color = TextBright, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = if (currentSyncOffset >= 0f) "+${String.format("%.1f", currentSyncOffset)}s" else "${String.format("%.1f", currentSyncOffset)}s", color = AmberCore, fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        ThinSliderBar(value = currentSyncOffset.coerceIn(-10f, 10f), onValueChange = { onUserInteraction(); onSyncOffsetChange(it) }, valueRange = -10f..10f)
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            TinySyncButton(text = "−0.5", modifier = Modifier.weight(1f)) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.5f).coerceAtLeast(-10f)) }
            TinySyncButton(text = "−0.1", modifier = Modifier.weight(1f)) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.1f).coerceAtLeast(-10f)) }
            TinySyncButton(text = "+0.1", modifier = Modifier.weight(1f)) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.1f).coerceAtMost(10f)) }
            TinySyncButton(text = "+0.5", modifier = Modifier.weight(1f)) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.5f).coerceAtMost(10f)) }
        }
    }
}

@Composable
private fun SubtitleTopChip(text: String, selected: Boolean, isLandscape: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(modifier = modifier.fillMaxWidth().height(if (isLandscape) 24.dp else 27.dp).clip(shape)
        .background(if (selected) AmberGlow.copy(alpha = 0.16f) else GlassSurface)
        .border(1.dp, brush = if (selected) Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))) else Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), shape = shape)
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text = text, color = if (selected) AmberCore else TextBright, fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TinySyncButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.height(22.dp).clip(RoundedCornerShape(8.dp)).background(GlassSurface)
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(8.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text = text, color = TextBright, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
