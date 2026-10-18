package com.sole.cinevault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

private val PillMint = Color(0xFF7CE0C3)
private val PillGold = Color(0xFFFFC94D)
private val PillSky = Color(0xFF6FC3FF)
private val PillViolet = Color(0xFFC792FF)
private val PillRose = Color(0xFFFF6E8C)
private val PillGrey = Color(0xFFA8A6A0)

/**
 * Left-hand pill column, top to bottom: HW (opens playback info), autoplay, audio
 * track, sound FX, subtitles. These used to be icon buttons in the bottom bar; they do
 * exactly what they did there. Audio and subtitle menus still anchor from these pills.
 */
@Composable
internal fun PlayerLeftPills(
    isLandscape: Boolean,
    hwLabel: String,
    hwNeedsAttention: Boolean,
    hwSoftware: Boolean,
    onHwClick: () -> Unit,
    autoPlayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    audioActive: Boolean,
    onAudioClick: () -> Unit,
    onAudioCenterMeasured: (Float) -> Unit,
    onAudioFxClick: () -> Unit,
    subtitleActive: Boolean,
    isStreamMedia: Boolean,
    onSubtitleClick: () -> Unit,
    onSubtitleLongClick: () -> Unit,
    onSubtitleCenterMeasured: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PlayerPillMetrics.gap(isLandscape)),
        horizontalAlignment = Alignment.Start,
    ) {
        NeonPill(
            icon = Icons.Rounded.Memory,
            label = hwLabel,
            accent = when {
                hwNeedsAttention -> PillGold
                hwSoftware -> PillRose
                else -> PillMint
            },
            contentDescription = "Playback info",
            compact = isLandscape,
            highlighted = hwNeedsAttention,
            activeDot = hwNeedsAttention,
            onClick = onHwClick,
        )
        NeonPill(
            icon = Icons.Rounded.AllInclusive,
            label = if (autoPlayEnabled) "AUTOPLAY ON" else "AUTOPLAY OFF",
            accent = if (autoPlayEnabled) PillGold else PillGrey,
            contentDescription = "Autoplay next episode",
            compact = isLandscape,
            highlighted = autoPlayEnabled,
            onClick = onToggleAutoplay,
        )
        NeonPill(
            icon = Icons.Rounded.Audiotrack,
            label = "AUDIO",
            accent = PillSky,
            contentDescription = "Audio track",
            compact = isLandscape,
            highlighted = audioActive,
            modifier = Modifier.onGloballyPositioned {
                onAudioCenterMeasured(it.positionInRoot().x + it.size.width / 2f)
            },
            onClick = onAudioClick,
        )
        NeonPill(
            icon = Icons.Rounded.Equalizer,
            label = "SOUND FX",
            accent = PillViolet,
            contentDescription = "Sound effects",
            compact = isLandscape,
            onClick = onAudioFxClick,
        )
        NeonPill(
            icon = Icons.Rounded.Subtitles,
            label = "SUBS",
            accent = PillGold,
            contentDescription = if (isStreamMedia) "Network subtitle controls" else "Subtitle controls",
            compact = isLandscape,
            highlighted = subtitleActive,
            modifier = Modifier.onGloballyPositioned {
                onSubtitleCenterMeasured(it.positionInRoot().x + it.size.width / 2f)
            },
            onClick = onSubtitleClick,
            onLongClick = onSubtitleLongClick,
        )
    }
}
