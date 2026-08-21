package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
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

@Composable
internal fun PlayerStatusOverlays(
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
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = showBrightnessCircle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.TopEnd else Alignment.CenterEnd)
                    .padding(top = if (isLandscape) 86.dp else 0.dp, end = 28.dp)
            ) {
                VerticalBrightnessHud(value = brightnessPercent, size = hudSize)
            }
        }
    }

    AnimatedVisibility(
        visible = showVolumeCircle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            val volumeColor = when {
                volumePercent > 120 -> Color.Red
                volumePercent > 90 -> Color(0xFFFF9800)
                else -> Color.White
            }
            Box(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.TopStart else Alignment.CenterStart)
                    .padding(top = if (isLandscape) 86.dp else 0.dp, start = 28.dp)
            ) {
                FilledCircleHud(value = volumePercent, maxValue = 150, color = volumeColor, size = hudSize)
            }
        }
    }

    AnimatedVisibility(
        visible = edgeSwipeHint.isNotBlank(),
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = edgeSwipeHint,
                color = TextBright,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }

    AnimatedVisibility(
        visible = showGlassesConnectedHint,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isLandscape) 54.dp else 90.dp)
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Rounded.Tv, null, tint = AmberCore, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "External display connected — RayNeo subtitle profile",
                    color = TextBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showBufferingSpinner && playerErrorMessage == null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(56.dp).glassPanel(cornerRadius = 28.dp, fill = GlassSurfaceStrong),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberCore, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                if (stuckBufferingHint) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Taking longer than usual — slow drive or connection?",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .glassPanel(cornerRadius = 14.dp, fill = GlassSurfaceStrong)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = playerErrorMessage != null,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("Playback Error", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    playerErrorMessage ?: "",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Back",
                        color = TextBright,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                    Text(
                        "Retry",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AmberCore)
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }

    if (sleepTimerActive && sleepTimerRemainingMs > 0) {
        val sleepMins = (sleepTimerRemainingMs / 60000).toInt()
        val sleepSecs = ((sleepTimerRemainingMs % 60000) / 1000).toInt()
        Box(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Timer, null, tint = AmberCore, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("%d:%02d".format(sleepMins, sleepSecs), color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
