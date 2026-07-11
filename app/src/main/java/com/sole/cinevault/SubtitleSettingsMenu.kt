package com.sole.cinevault

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

    // RESPONSIVE SIZING: derived from actual screen width/height, not fixed constants.
    // Portrait: popup width scales with screen width (70%), landscape uses a narrower
    // fraction since horizontal space is shared with the video. Both clamped to
    // sane min/max so this works on a phone or a tablet.
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val popupWidth: Dp = if (isLandscape) {
        (screenWidthDp * 0.34f).dp.coerceIn(230.dp, 300.dp)
    } else {
        (screenWidthDp * 0.70f).dp.coerceIn(240.dp, 340.dp)
    }
    // Cap popup height to a fraction of screen height so it can never exceed the
    // viewport; verticalScroll below handles anything that still overflows.
    val panelMaxHeight: Dp = if (isLandscape) {
        (screenHeightDp * 0.78f).dp.coerceAtMost(244.dp)
    } else {
        (screenHeightDp * 0.55f).dp.coerceAtMost(420.dp)
    }

    val rowGap = if (isLandscape) 5.dp else 7.dp
    val titleSize = if (isLandscape) 13.sp else 15.sp
    val labelSize = if (isLandscape) 10.sp else 12.sp
    val valueSize = if (isLandscape) 9.sp else 10.sp

    Column(
        modifier = Modifier
            .width(popupWidth)
            .heightIn(max = panelMaxHeight)
            .glassPanel(cornerRadius = if (isLandscape) 18.dp else 22.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .clickable { onUserInteraction() }
            .padding(horizontal = if (isLandscape) 10.dp else 12.dp, vertical = if (isLandscape) 8.dp else 10.dp)
            .verticalScroll(rememberScrollState()), // FIX: content can no longer be silently clipped
        verticalArrangement = Arrangement.spacedBy(rowGap)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Subtitles", color = AmberCore, fontSize = titleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { onUserInteraction(); onDismiss() }, modifier = Modifier.size(if (isLandscape) 26.dp else 30.dp).clip(CircleShape).background(GlassSurface)) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextBright, modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SubtitleTopChip(text = "Internal", selected = subtitlesEnabled && hasInternalSubtitles, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onInternalClick() }
            SubtitleTopChip(text = "Download", selected = false, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onDownloadClick() }
            SubtitleTopChip(text = if (subtitlesEnabled) "OFF" else "ON", selected = !subtitlesEnabled, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onToggleSubtitles() }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GlassSurface)
                .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(12.dp))
                .clickable { onUserInteraction(); onPickFileClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = AmberCore, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Browse subtitle files", color = TextBright, fontSize = labelSize, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = GlassBorderBottom)
        CompactSliderRow(label = "Text Size", valueLabel = "${currentFontSize.toInt()}sp", value = currentFontSize, valueRange = 12f..32f, steps = 9, isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize) { onUserInteraction(); onFontSizeChange(it) }
        CompactSliderRow(label = "Position", valueLabel = "${(currentVerticalPosition * 100).toInt()}%", value = currentVerticalPosition, valueRange = 0.02f..0.30f, steps = 0, isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize) { onUserInteraction(); onVerticalPositionChange(it) }
        CompactSyncRow(currentSyncOffset = currentSyncOffset, isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize, onUserInteraction = onUserInteraction, onSyncOffsetChange = onSyncOffsetChange)
        HorizontalDivider(color = GlassBorderBottom)
        TextButton(onClick = { onUserInteraction(); onReset() }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Reset to Defaults", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactSliderRow(label: String, valueLabel: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, isLandscape: Boolean, labelSize: androidx.compose.ui.unit.TextUnit, valueSize: androidx.compose.ui.unit.TextUnit, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = TextBright, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = valueLabel, color = AmberCore, fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.fillMaxWidth().height(if (isLandscape) 26.dp else 34.dp),
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.18f)))
    }
}

@Composable
private fun CompactSyncRow(currentSyncOffset: Float, isLandscape: Boolean, labelSize: androidx.compose.ui.unit.TextUnit, valueSize: androidx.compose.ui.unit.TextUnit, onUserInteraction: () -> Unit, onSyncOffsetChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Sync", color = TextBright, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (currentSyncOffset >= 0f) "+${String.format("%.1f", currentSyncOffset)}s" else "${String.format("%.1f", currentSyncOffset)}s", color = AmberCore, fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)) {
            TinySyncButton(text = "−0.5", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.5f).coerceAtLeast(-10f)) }
            TinySyncButton(text = "−0.1", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.1f).coerceAtLeast(-10f)) }
            Slider(value = currentSyncOffset.coerceIn(-10f, 10f), onValueChange = { onUserInteraction(); onSyncOffsetChange(it) }, valueRange = -10f..10f,
                modifier = Modifier.weight(1f).height(if (isLandscape) 26.dp else 34.dp),
                colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.18f)))
            TinySyncButton(text = "+0.1", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.1f).coerceAtMost(10f)) }
            TinySyncButton(text = "+0.5", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.5f).coerceAtMost(10f)) }
        }
    }
}

@Composable
private fun SubtitleTopChip(text: String, selected: Boolean, isLandscape: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(modifier = modifier.height(if (isLandscape) 32.dp else 38.dp).clip(shape)
        .background(if (selected) AmberGlow.copy(alpha = 0.16f) else GlassSurface)
        .border(1.dp, brush = if (selected) Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))) else Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), shape = shape)
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(text = text, color = if (selected) AmberCore else TextBright, fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TinySyncButton(text: String, isLandscape: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.height(if (isLandscape) 26.dp else 30.dp).clip(RoundedCornerShape(10.dp)).background(GlassSurface)
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(10.dp))
        .clickable { onClick() }.padding(horizontal = if (isLandscape) 6.dp else 7.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = TextBright, fontSize = if (isLandscape) 8.sp else 9.sp, fontWeight = FontWeight.Bold)
    }
}
