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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.GlassBorderTop
import com.sole.cinevault.ui.theme.GlassHighlight
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

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
    autoPlayEnabled: Boolean,
    showAudioSelector: Boolean,
    showSubtitleActive: Boolean,
    isStreamMedia: Boolean,
    onBack: () -> Unit,
    onReplay10: () -> Unit,
    onPlayPause: () -> Unit,
    onForward10: () -> Unit,
    onNext: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onAudioClick: () -> Unit,
    onAudioCenterMeasured: (Float) -> Unit,
    onSubtitleClick: () -> Unit,
    onSubtitleLongClick: () -> Unit,
    onSubtitleCenterMeasured: (Float) -> Unit,
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
        Row(
            modifier = Modifier
                .padding(bottom = bottomDockPadding, start = sidePadding, end = sidePadding)
                .glassPanel(cornerRadius = 42.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy((7 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackIconButton(size = smallButton, onClick = onBack)

            GlassTransportButton(
                icon = Icons.Rounded.Replay10,
                size = smallButton,
                onClick = onReplay10
            )

            FrostedPlayButton(
                isPlaying = isPlaying,
                isEnded = isVideoEnded,
                size = playButton,
                onClick = onPlayPause
            )

            GlassTransportButton(
                icon = Icons.Rounded.Forward10,
                size = smallButton,
                onClick = onForward10
            )

            if (showPrevNextButtons) {
                IconCircle(
                    icon = Icons.Rounded.SkipNext,
                    size = smallButton,
                    tint = if (hasNextVideo) TextBright else TextMuted.copy(alpha = 0.35f),
                    onClick = onNext
                )
            }

            Spacer(modifier = Modifier.width((4 * scale).dp))

            IconCircle(
                icon = Icons.Rounded.AllInclusive,
                size = smallButton,
                tint = if (autoPlayEnabled) AmberCore else TextMuted.copy(alpha = 0.6f),
                onClick = onToggleAutoplay
            )

            IconCircle(
                icon = Icons.Rounded.Audiotrack,
                size = smallButton,
                tint = if (showAudioSelector) AmberCore else TextBright,
                modifier = Modifier.onGloballyPositioned {
                    onAudioCenterMeasured(it.positionInRoot().x + it.size.width / 2f)
                },
                onClick = onAudioClick
            )

            if (!isStreamMedia) {
                Box(
                    modifier = Modifier
                        .size(smallButton)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassSurface)
                        .background(
                            Brush.verticalGradient(
                                0f to GlassHighlight,
                                0.4f to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)),
                            RoundedCornerShape(20.dp)
                        )
                        .onGloballyPositioned {
                            onSubtitleCenterMeasured(it.positionInRoot().x + it.size.width / 2f)
                        }
                        .combinedClickable(
                            onClick = onSubtitleClick,
                            onLongClick = onSubtitleLongClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ClosedCaption,
                        contentDescription = null,
                        tint = if (showSubtitleActive) AmberCore else TextBright,
                        modifier = Modifier.size(smallButton * 0.44f)
                    )
                }
            }
        }
    }
}
