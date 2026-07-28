package com.sole.cinevault

import com.sole.cinevault.ui.theme.*
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Quick Subtitle Menu — redesigned for progressive disclosure ──────────
// This is deliberately MUCH smaller than the version it replaces. Everyone
// agreed (both external reviews + Ash's own on-device complaint) that the
// old quick menu was trying to be a mini-Studio: a 2x2 action grid, three
// full drag sliders, three text links, a pulsing Reset, AND a footer button
// into the real Studio — all stacked in one small popup. Tap now shows
// only what covers ~95% of real usage; everything else (Find/search
// results, Dialogue Sync, Drift, Auto-Sync, cleaning, dual subtitles,
// behavior prefs) lives one level down, reached via long-press on the CC
// icon into the new Tools Grid (see SubtitleStudioSheet.kt).
fun subtitleMenuWidth(screenWidthDp: Float, isLandscape: Boolean): Dp =
    if (isLandscape) (screenWidthDp * 0.19f).dp.coerceIn(150.dp, 190.dp)
    else (screenWidthDp * 0.40f).dp.coerceIn(165.dp, 210.dp)

@Composable
fun SubtitleSettingsMenu(
    isVisible: Boolean,
    subtitlesEnabled: Boolean,
    activeTrackStatusText: String = "",
    onToggleSubtitles: () -> Unit,
    onTracksClick: () -> Unit,
    onFindClick: () -> Unit,
    onSyncClick: () -> Unit,
    onStyleClick: () -> Unit,
    onDismiss: () -> Unit,
    currentFontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    currentVerticalPosition: Float,
    onVerticalPositionChange: (Float) -> Unit,
    onUserInteraction: () -> Unit
) {
    if (!isVisible) return
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val popupWidth: Dp = subtitleMenuWidth(configuration.screenWidthDp.toFloat(), isLandscape)

    val titleSize = if (isLandscape) 12.sp else 13.sp
    val statusSize = if (isLandscape) 9.sp else 9.5.sp

    Column(
        modifier = Modifier
            .width(popupWidth)
            .glassPanel(cornerRadius = if (isLandscape) 18.dp else 20.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .clickable { onUserInteraction() }
            .padding(horizontal = if (isLandscape) 11.dp else 13.dp, vertical = if (isLandscape) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 9.dp else 11.dp)
    ) {
        // Header — title + real ON/OFF switch, matching the mockup's
        // "Subtitles    ON ●" row exactly, instead of an icon pill buried
        // in an action grid.
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Subtitles", color = TextBright, fontSize = titleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = { onUserInteraction(); onToggleSubtitles() },
                    modifier = Modifier.height(if (isLandscape) 18.dp else 20.dp),
                    colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.45f))
                )
            }
            if (activeTrackStatusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeTrackStatusText, color = TextMuted, fontSize = statusSize, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4-icon quick row — Tracks / Find / Sync / Style. Each one is a
        // direct shortcut straight into that specific Studio tool (see
        // VideoPlayerScreen.kt's wiring), bypassing the Tools Grid
        // entirely, since these are the four things people reach for most.
        // "Browse a local file" deliberately isn't here anymore — it
        // already lives inside Tracks (per the review: a duplicate entry
        // point for the same action just adds clutter).
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            QuickIconAction(icon = Icons.Filled.ViewList, label = "Tracks", modifier = Modifier.weight(1f), isLandscape = isLandscape) { onUserInteraction(); onTracksClick() }
            QuickIconAction(icon = Icons.Filled.Search, label = "Find", modifier = Modifier.weight(1f), isLandscape = isLandscape) { onUserInteraction(); onFindClick() }
            QuickIconAction(icon = Icons.Filled.Sync, label = "Sync", modifier = Modifier.weight(1f), isLandscape = isLandscape) { onUserInteraction(); onSyncClick() }
            QuickIconAction(icon = Icons.Filled.Palette, label = "Style", modifier = Modifier.weight(1f), isLandscape = isLandscape) { onUserInteraction(); onStyleClick() }
        }

        HorizontalDivider(color = GlassBorderBottom)

        // Size — tap stepper instead of a drag slider. Matches the
        // mockup's "Size   −  22  +" row exactly, and is much easier to
        // hit precisely on a tablet than a thin drag bar.
        QuickStepperRow(
            label = "Size",
            valueText = "${currentFontSize.toInt()}sp",
            onDecrease = { onUserInteraction(); onFontSizeChange((currentFontSize - 1f).coerceIn(12f, 32f)) },
            onIncrease = { onUserInteraction(); onFontSizeChange((currentFontSize + 1f).coerceIn(12f, 32f)) },
            isLandscape = isLandscape
        )

        // Position — Low/Mid/High pills instead of a drag slider, same
        // three bands the old slider's label already snapped to, just
        // exposed directly as one-tap targets now.
        QuickPositionRow(
            currentValue = currentVerticalPosition,
            onSelect = { onUserInteraction(); onVerticalPositionChange(it) },
            isLandscape = isLandscape
        )
    }
}

@Composable
private fun QuickIconAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, isLandscape: Boolean, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, AmberCore.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = if (isLandscape) 6.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = AmberCore, modifier = Modifier.size(if (isLandscape) 14.dp else 16.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = TextBright, fontSize = if (isLandscape) 7.5.sp else 8.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickStepperRow(label: String, valueText: String, onDecrease: () -> Unit, onIncrease: () -> Unit, isLandscape: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = Color(0xFFC9A765), fontSize = if (isLandscape) 10.sp else 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        StepperButton(icon = Icons.Filled.Remove, isLandscape = isLandscape, onClick = onDecrease)
        Text(
            text = valueText, color = AmberCore, fontSize = if (isLandscape) 10.sp else 11.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.width(if (isLandscape) 34.dp else 38.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        StepperButton(icon = Icons.Filled.Add, isLandscape = isLandscape, onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(icon: ImageVector, isLandscape: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(if (isLandscape) 22.dp else 24.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .border(1.dp, AmberCore.copy(alpha = 0.4f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(if (isLandscape) 11.dp else 12.dp))
    }
}

private fun positionBandLabel(value: Float): String {
    val fraction = ((value - 0.02f) / (0.30f - 0.02f)).coerceIn(0f, 1f)
    return when {
        fraction < 0.34f -> "Low"
        fraction < 0.67f -> "Mid"
        else -> "High"
    }
}

@Composable
private fun QuickPositionRow(currentValue: Float, onSelect: (Float) -> Unit, isLandscape: Boolean) {
    val currentBand = positionBandLabel(currentValue)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Position", color = Color(0xFFC9A765), fontSize = if (isLandscape) 10.sp else 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Low" to 0.02f, "Mid" to 0.16f, "High" to 0.30f).forEach { (label, value) ->
                val selected = currentBand == label
                Text(
                    text = label, color = if (selected) Color.Black else TextBright,
                    fontSize = if (isLandscape) 8.sp else 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AmberCore else GlassSurface)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}
