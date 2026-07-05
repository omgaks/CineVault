package com.sole.cinevault

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    hasInternalSubtitles: Boolean,
    onInternalClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onPickFileClick: () -> Unit = {},   // NEW: local SRT file picker
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

    val panelWidth = if (isLandscape) 260.dp else 270.dp
    val panelBottomPadding = if (isLandscape) 92.dp else 235.dp
    val panelMaxHeight = if (isLandscape) 240.dp else 480.dp
    val panelPH = if (isLandscape) 10.dp else 12.dp
    val panelPV = if (isLandscape) 8.dp else 10.dp
    val rowGap = if (isLandscape) 5.dp else 8.dp
    val titleSize = if (isLandscape) 13.sp else 15.sp
    val labelSize = if (isLandscape) 10.sp else 12.sp
    val valueSize = if (isLandscape) 9.sp else 10.sp

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.06f))
            .clickable { onUserInteraction(); onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = panelBottomPadding, end = 14.dp)
                .width(panelWidth)
                .heightIn(max = panelMaxHeight)
                .clip(RoundedCornerShape(if (isLandscape) 18.dp else 22.dp))
                .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.58f))))
                .clickable { onUserInteraction() }
                .padding(horizontal = panelPH, vertical = panelPV),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Subtitles", color = Color.White, fontSize = titleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onUserInteraction(); onDismiss() },
                    modifier = Modifier.size(if (isLandscape) 26.dp else 30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp))
                }
            }

            // Source buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                SubtitleTopChip(text = "Internal", selected = subtitlesEnabled && hasInternalSubtitles, enabled = hasInternalSubtitles, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onInternalClick() }
                SubtitleTopChip(text = "Download", selected = false, enabled = true, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onDownloadClick() }
                SubtitleTopChip(text = if (subtitlesEnabled) "OFF" else "ON", selected = !subtitlesEnabled, enabled = true, isLandscape = isLandscape, modifier = Modifier.weight(1f)) { onUserInteraction(); onToggleSubtitles() }
            }

            // File picker button
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onUserInteraction(); onPickFileClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFFFFD36A), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Browse local SRT file", color = Color.White, fontSize = labelSize, fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            // FIX: Default font size 18sp (was 22sp), position default 13% from bottom
            CompactSliderRow(
                label = "Text Size", valueLabel = "${currentFontSize.toInt()}sp",
                value = currentFontSize, valueRange = 12f..32f, steps = 9,
                isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize
            ) { onUserInteraction(); onFontSizeChange(it) }

            CompactSliderRow(
                label = "Position", valueLabel = "${(currentVerticalPosition * 100).toInt()}%",
                value = currentVerticalPosition, valueRange = 0.02f..0.30f, steps = 0,
                isLandscape = isLandscape, labelSize = labelSize, valueSize = valueSize
            ) { onUserInteraction(); onVerticalPositionChange(it) }

            CompactSyncRow(
                currentSyncOffset = currentSyncOffset, isLandscape = isLandscape,
                labelSize = labelSize, valueSize = valueSize,
                onUserInteraction = onUserInteraction, onSyncOffsetChange = onSyncOffsetChange
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            TextButton(onClick = { onUserInteraction(); onReset() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Reset to Defaults", color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CompactSliderRow(label: String, valueLabel: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, isLandscape: Boolean, labelSize: androidx.compose.ui.unit.TextUnit, valueSize: androidx.compose.ui.unit.TextUnit, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = Color.White, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = valueLabel, color = Color(0xFFFFD36A), fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps,
            modifier = Modifier.fillMaxWidth().height(if (isLandscape) 26.dp else 34.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFFD36A), inactiveTrackColor = Color.White.copy(alpha = 0.22f)))
    }
}

@Composable
private fun CompactSyncRow(currentSyncOffset: Float, isLandscape: Boolean, labelSize: androidx.compose.ui.unit.TextUnit, valueSize: androidx.compose.ui.unit.TextUnit, onUserInteraction: () -> Unit, onSyncOffsetChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(if (isLandscape) 0.dp else 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Sync", color = Color.White, fontSize = labelSize, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (currentSyncOffset >= 0f) "+${String.format("%.1f", currentSyncOffset)}s" else "${String.format("%.1f", currentSyncOffset)}s", color = Color(0xFFFFD36A), fontSize = valueSize, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)) {
            TinySyncButton(text = "-0.5", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.5f).coerceAtLeast(-10f)) }
            TinySyncButton(text = "-0.1", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset - 0.1f).coerceAtLeast(-10f)) }
            Slider(value = currentSyncOffset.coerceIn(-10f, 10f), onValueChange = { onUserInteraction(); onSyncOffsetChange(it) }, valueRange = -10f..10f,
                modifier = Modifier.weight(1f).height(if (isLandscape) 26.dp else 34.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFFD36A), inactiveTrackColor = Color.White.copy(alpha = 0.22f)))
            TinySyncButton(text = "+0.1", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.1f).coerceAtMost(10f)) }
            TinySyncButton(text = "+0.5", isLandscape = isLandscape) { onUserInteraction(); onSyncOffsetChange((currentSyncOffset + 0.5f).coerceAtMost(10f)) }
        }
    }
}

@Composable
private fun SubtitleTopChip(text: String, selected: Boolean, enabled: Boolean, isLandscape: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(enabled = enabled, onClick = onClick, shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFFFFD36A).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.10f),
            disabledContainerColor = Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Color.Black else Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        contentPadding = PaddingValues(horizontal = if (isLandscape) 4.dp else 6.dp, vertical = if (isLandscape) 3.dp else 5.dp),
        modifier = modifier.height(if (isLandscape) 32.dp else 38.dp)
    ) {
        Text(text = text, fontSize = if (isLandscape) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TinySyncButton(text: String, isLandscape: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.10f), contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = if (isLandscape) 4.dp else 5.dp, vertical = if (isLandscape) 3.dp else 5.dp),
        modifier = Modifier.height(if (isLandscape) 26.dp else 30.dp)
    ) {
        Text(text = text, fontSize = if (isLandscape) 7.sp else 8.sp, fontWeight = FontWeight.Bold)
    }
}
