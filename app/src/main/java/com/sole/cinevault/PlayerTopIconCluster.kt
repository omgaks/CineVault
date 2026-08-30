package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*

@Composable
internal fun TopIconCluster(
    isLandscape: Boolean,
    iconSize: Dp,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        AmberPillIcon(icon = Icons.Rounded.Tv, contentDescription = "Picture in picture", onClick = onPipClick)
        AmberPillIcon(icon = Icons.Rounded.Timer, contentDescription = "Sleep timer", activeDot = sleepTimerActive || showSleepMenu, onClick = onSleepClick)
        LabeledGlowIcon(icon = Icons.Rounded.Speed, label = "Speed", size = iconSize, tint = if (playbackSpeed != 1f || showSpeedMenu) AmberCore else TextBright, active = playbackSpeed != 1f || showSpeedMenu, onClick = onSpeedClick)
    }
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

// FEATURE: matches the lock/unlock button's exact styling (solid amber
// pill, black icon, no text label) rather than the glass-surface +
// label look LabeledGlowIcon uses — requested specifically for PiP and
// Sleep Timer, to bring them visually in line with Lock. activeDot is a
// small optional indicator (used for Sleep Timer) since a solid-fill
// pill has no obvious way to show an "active" state via icon swap the
// way Lock does (Lock/LockOpen) — Timer doesn't have a natural active
// variant, so a subtle dot preserves that information without breaking
// the requested visual consistency.
@Composable
private fun AmberPillIcon(icon: ImageVector, contentDescription: String, activeDot: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.Black, modifier = Modifier.size(22.dp))
        if (activeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFE53935))
            )
        }
    }
}

// Icon + text label, with a persistent amber-glass glow border (same
// visual language as the always-visible lock button, just a border/glow
// treatment rather than a solid fill so active/inactive state — e.g.
// playback speed != 1x, sleep timer running — still reads clearly through
// tint and glow intensity). Labels sit below the icon so PiP/Sleep/Speed
// don't need to be guessed from silhouette alone.
@Composable
private fun LabeledGlowIcon(icon: ImageVector, label: String, size: Dp, tint: Color = TextBright, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = if (active) 0.38f else 0.20f), Color.Transparent)))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = if (active) 0.90f else 0.55f), AmberDeep.copy(alpha = if (active) 0.55f else 0.25f))),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = if (active) AmberCore else TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
