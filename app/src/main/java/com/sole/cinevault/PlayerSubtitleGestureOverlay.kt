package com.sole.cinevault

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import kotlinx.coroutines.delay

/*
 * PlayerSubtitleGestureOverlay.kt
 *
 * Owns the optional gesture band above the transport controls. It remains
 * disabled by default and writes to the same subtitle state holders used by
 * Subtitle Studio, so gestures and menu controls stay synchronized.
 */

@Composable
@OptIn(UnstableApi::class)
internal fun PlayerSubtitleGestureOverlay(
    player: ExoPlayer,
    haptics: HapticFeedback,
    isStreamMedia: Boolean,
    bottomDockPadding: Dp,
    playButtonSize: Dp,
    coreUi: SubtitleCoreUiState,
    appearanceUi: SubtitleAppearanceUiState,
    studioUi: SubtitleStudioUiState,
    onShowControls: () -> Unit,
) {
    LaunchedEffect(studioUi.gestureFeedback) {
        if (studioUi.gestureFeedback.isBlank()) return@LaunchedEffect
        delay(900)
        studioUi.gestureFeedback = ""
    }

    if (
        !coreUi.behaviorPrefs.enableSubtitleGestures ||
        !coreUi.subtitlesEnabled ||
        isStreamMedia
    ) {
        return
    }

    val gestureZoneHeight = 110.dp
    val gestureZoneBottomOffset = bottomDockPadding + playButtonSize + 26.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = gestureZoneBottomOffset,
                    start = 48.dp,
                    end = 48.dp,
                )
                .fillMaxWidth()
                .height(gestureZoneHeight)
                .subtitleGestureZone(
                    enabledKey = coreUi.behaviorPrefs.enableSubtitleGestures,
                    onPinchTextSize = { zoom ->
                        appearanceUi.textSizeSp =
                            (appearanceUi.textSizeSp * zoom).coerceIn(12f, 32f)
                        studioUi.gestureFeedback =
                            "${appearanceUi.textSizeSp.toInt()}sp"
                    },
                    onHorizontalSyncDrag = { deltaX ->
                        val deltaSeconds = deltaX / 60f
                        coreUi.syncOffset =
                            (coreUi.syncOffset + deltaSeconds).coerceIn(-10f, 10f)

                        val formattedOffset =
                            String.format("%.1f", coreUi.syncOffset)

                        studioUi.gestureFeedback =
                            if (coreUi.syncOffset >= 0f) {
                                "+${formattedOffset}s"
                            } else {
                                "${formattedOffset}s"
                            }
                    },
                    onVerticalPositionDrag = { deltaFraction ->
                        appearanceUi.bottomPadding =
                            (appearanceUi.bottomPadding + deltaFraction)
                                .coerceIn(0.02f, 0.90f)
                        studioUi.gestureFeedback = "Position"
                    },
                    onDoubleTapResetSync = {
                        coreUi.syncOffset = 0f
                        studioUi.gestureFeedback = "Sync reset"
                    },
                    onLongPressTogglePlayback = {
                        haptics.performHapticFeedback(
                            HapticFeedbackType.LongPress
                        )
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        onShowControls()
                    },
                ),
        )

        AnimatedVisibility(
            visible = studioUi.gestureFeedback.isNotBlank(),
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = gestureZoneBottomOffset +
                        gestureZoneHeight / 2
                ),
        ) {
            Text(
                text = studioUi.gestureFeedback,
                color = AmberCore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(GlassSurfaceStrong)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
