package com.sole.cinevault.glasses.tutorial

import com.sole.cinevault.glasses.gestures.HeadGesture
import com.sole.cinevault.glasses.gestures.rememberHeadGestureDetector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.SpaceGlassBackground
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted

/**
 * Phase 9 — gesture practice, reachable anytime from Settings > Glasses
 * Mode. Uses the exact same detector real playback uses
 * (rememberHeadGestureDetector), so practicing here and using it for
 * real feel identical — and if this model has no usable external
 * orientation sensor, this screen says so plainly instead of sitting
 * there looking broken with controls that will never respond.
 */
@Composable
fun GlassesGestureTutorialScreen(onBack: () -> Unit) {
    var lastGesture by remember { mutableStateOf<HeadGesture?>(null) }

    val sensorAvailable = rememberHeadGestureDetector(enabled = true) { gesture ->
        lastGesture = gesture
    }

    SpaceGlassBackground {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = TextBright,
                modifier = Modifier.size(28.dp).clickable { onBack() },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (!sensorAvailable) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Gestures aren't available on this pair",
                            color = TextBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This model doesn't expose a separate head-orientation sensor CineVault can read — use the touchpad instead.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Try a nod or a shake",
                            color = TextBright,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(AmberCore.copy(alpha = if (lastGesture != null) 0.35f else 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedVisibility(
                                visible = lastGesture != null,
                                enter = fadeIn(tween(120)),
                                exit = fadeOut(tween(400)),
                            ) {
                                Text(
                                    text = when (lastGesture) {
                                        HeadGesture.NOD -> "NOD"
                                        HeadGesture.SHAKE -> "SHAKE"
                                        null -> ""
                                    },
                                    color = AmberCore,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Nod = confirm / play-pause. Shake = dismiss / show controls.",
                            color = TextMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
