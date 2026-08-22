package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.sole.cinevault.segments.PostCreditNotice
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSkipPill

@Composable
internal fun BoxScope.PlayerSmartPlaybackOverlays(
    sidePadding: Dp,
    showSeekPreview: Boolean,
    isDraggingSeekbar: Boolean,
    showNextEpisodeOverlay: Boolean,
    pendingNextEpisode: VideoWithMetadata?,
    nextEpisodeCountdown: Int,
    activeSmartSegment: SmartSegment?,
    suppressCreditsPillForScene: Boolean,
    anyMenuOpenForSmartSkip: Boolean,
    creditNoticeVisible: Boolean,
    exactSceneSegment: SmartSegment?,
    hasMidCreditsScene: Boolean,
    hasPostCreditsScene: Boolean,
    position: Long,
    isLandscape: Boolean,
    onPlayNextEpisode: (VideoWithMetadata) -> Unit,
    onCancelNextEpisode: () -> Unit,
    onSkipSegment: (SmartSegment) -> Unit,
    onJumpToCreditScene: (SmartSegment) -> Unit,
) {
    AnimatedVisibility(
        visible = showNextEpisodeOverlay && pendingNextEpisode != null && !showSeekPreview,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(120)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = sidePadding)
    ) {
        pendingNextEpisode?.let { next ->
            NextEpisodeCountdownOverlay(
                nextEpisode = next,
                countdown = nextEpisodeCountdown,
                isLandscape = isLandscape,
                onPlayNow = { onPlayNextEpisode(next) },
                onCancel = onCancelNextEpisode
            )
        }
    }

    AnimatedVisibility(
        visible = activeSmartSegment != null &&
            !suppressCreditsPillForScene &&
            !showNextEpisodeOverlay &&
            !showSeekPreview &&
            !isDraggingSeekbar &&
            !anyMenuOpenForSmartSkip,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = sidePadding)
    ) {
        activeSmartSegment?.let { segment ->
            SmartSkipPill(
                segment = segment,
                remainingMs = segment.endMs - position,
                onClick = { onSkipSegment(segment) }
            )
        }
    }

    AnimatedVisibility(
        visible = creditNoticeVisible && !showSeekPreview && !anyMenuOpenForSmartSkip,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = sidePadding)
    ) {
        PostCreditNotice(
            hasExactTimestamp = exactSceneSegment != null,
            isMidCredits = hasMidCreditsScene && !hasPostCreditsScene,
            onJump = exactSceneSegment?.let { scene ->
                { onJumpToCreditScene(scene) }
            }
        )
    }
}
