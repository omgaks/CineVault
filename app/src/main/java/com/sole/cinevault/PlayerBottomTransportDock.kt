package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberDeep
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/**
 * Bottom transport bar: Back, -10 s, play/pause, +10 s, and Next episode (series only).
 * The whole bar and every button carry the amber outline and glow; icons fill their discs.
 * Autoplay, audio, sound FX and subtitles live in the left pill column now.
 */
@Composable
internal fun BoxScope.PlayerBottomTransportDock(
    visible: Boolean,
    bottomDockPadding: Dp,
    sidePadding: Dp,
    scale: Float,
    smallButton: Dp,
    playButton: Dp,
    isPlaying: Boolean,
    isVideoEnded: Boolean,
    showPrevNextButtons: Boolean,
    hasNextVideo: Boolean,
    onBack: () -> Unit,
    onReplay10: () -> Unit,
    onPlayPause: () -> Unit,
    onForward10: () -> Unit,
    onNext: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it / 2 },
            animationSpec = tween(180)
        ) + fadeOut(animationSpec = tween(140)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        BoxWithConstraints {
            // Shrink the row a little on a narrow screen so every button is visible.
            val fit = transportFitFactor(
                availableDp = maxWidth.value - 2 * sidePadding.value,
                smallButtonDp = smallButton.value * 1.12f,
                playButtonDp = playButton.value * 1.08f,
                smallButtonCount = if (showPrevNextButtons) 4 else 3,
                spacingDp = 5 * scale,
                extraGapDp = 0f,
                sidePaddingDp = 14 * scale
            )
            val sb = smallButton * (1.12f * fit)
            val pb = playButton * (1.08f * fit)
            val sc = scale * fit
            val barShape = RoundedCornerShape(42.dp)
            Row(
                modifier = Modifier
                    .padding(bottom = bottomDockPadding, start = sidePadding, end = sidePadding)
                    .shadow(14.dp, barShape, ambientColor = AmberCore, spotColor = AmberCore)
                    .glassPanel(cornerRadius = 42.dp, fill = GlassSurfaceStrong)
                    .background(Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.14f), Color.Transparent)))
                    .border(1.4.dp, AmberGlow.copy(alpha = 0.78f), barShape)
                    .padding(horizontal = (14 * sc).dp, vertical = (7 * sc).dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy((5 * sc).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AmberTransportButton(Icons.Rounded.ArrowBack, "Back", sb, AmberCore, onBack)
                AmberTransportButton(Icons.Rounded.Replay10, "Back 10 seconds", sb, TextBright, onReplay10)
                FrostedPlayButton(isPlaying = isPlaying, isEnded = isVideoEnded, size = pb, onClick = onPlayPause)
                AmberTransportButton(Icons.Rounded.Forward10, "Forward 10 seconds", sb, TextBright, onForward10)
                if (showPrevNextButtons) {
                    AmberTransportButton(
                        Icons.Rounded.SkipNext,
                        "Next episode",
                        sb,
                        if (hasNextVideo) TextBright else TextMuted.copy(alpha = 0.35f),
                        onNext,
                    )
                }
            }
        }
    }
}

/** Round glass button with an amber outline and glow; the icon fills most of the disc. */
@Composable
private fun AmberTransportButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(6.dp, CircleShape, ambientColor = AmberCore, spotColor = AmberCore)
            .clip(CircleShape)
            .background(GlassSurface)
            .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = 0.16f), Color.Transparent)))
            .border(
                1.3.dp,
                Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.55f))),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}
