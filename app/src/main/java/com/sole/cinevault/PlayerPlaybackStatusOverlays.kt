package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/*
 * PlayerPlaybackStatusOverlays.kt
 *
 * Owns the short-lived playback feedback drawn above the video surface:
 * gesture HUDs, connection and swipe hints, buffering/error feedback, and
 * the active sleep-timer countdown. State and retry behavior remain owned by
 * VideoPlayerScreen; this file only renders the supplied values and actions.
 */

@Composable
internal fun BoxScope.PlayerPlaybackStatusOverlays(
    isLandscape: Boolean,
    hudSize: Dp,
    showBrightnessCircle: Boolean,
    brightnessPercent: Int,
    showVolumeCircle: Boolean,
    volumePercent: Int,
    edgeSwipeHint: String,
    showGlassesConnectedHint: Boolean,
    showBufferingSpinner: Boolean,
    stuckBufferingHint: Boolean,
    playerErrorMessage: String?,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    translationSuccessLanguage: String?,
    translationSuccessBottomPadding: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = translationSuccessLanguage != null,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(220),
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it / 4 },
            animationSpec = tween(180),
        ) + fadeOut(animationSpec = tween(160)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = translationSuccessBottomPadding,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 340.dp)
                .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Text(
                    text = "${translationSuccessLanguage.orEmpty()} translation ready",
                    color = TextBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Subtitles applied automatically",
                    color = AmberCore,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showBrightnessCircle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(if (isLandscape) Alignment.TopEnd else Alignment.CenterEnd)
            .padding(top = if (isLandscape) 86.dp else 0.dp, end = 28.dp),
    ) {
        VerticalBrightnessHud(value = brightnessPercent, size = hudSize)
    }

    AnimatedVisibility(
        visible = showVolumeCircle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(if (isLandscape) Alignment.TopStart else Alignment.CenterStart)
            .padding(top = if (isLandscape) 86.dp else 0.dp, start = 28.dp),
    ) {
        val volumeColor = when {
            volumePercent > 120 -> Color.Red
            volumePercent > 90 -> Color(0xFFFF9800)
            else -> Color.White
        }
        FilledCircleHud(
            value = volumePercent,
            maxValue = 150,
            color = volumeColor,
            size = hudSize,
        )
    }

    AnimatedVisibility(
        visible = edgeSwipeHint.isNotBlank(),
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Text(
            text = edgeSwipeHint,
            color = TextBright,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }

    AnimatedVisibility(
        visible = showGlassesConnectedHint,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = if (isLandscape) 54.dp else 90.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "External display connected — glasses subtitle profile",
                color = TextBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    AnimatedVisibility(
        visible = showBufferingSpinner && playerErrorMessage == null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .glassPanel(cornerRadius = 28.dp, fill = GlassSurfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = AmberCore,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (stuckBufferingHint) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Taking longer than usual — slow drive or connection?",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .glassPanel(cornerRadius = 14.dp, fill = GlassSurfaceStrong)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    AnimatedVisibility(
        visible = playerErrorMessage != null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Playback Error",
                color = TextBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = playerErrorMessage.orEmpty(),
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Back",
                    color = TextBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
                Text(
                    text = "Retry",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AmberCore)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }

    if (sleepTimerActive && sleepTimerRemainingMs > 0L) {
        val sleepMins = (sleepTimerRemainingMs / 60_000L).toInt()
        val sleepSecs = ((sleepTimerRemainingMs % 60_000L) / 1_000L).toInt()
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = AmberCore,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "%d:%02d".format(sleepMins, sleepSecs),
                color = AmberCore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
