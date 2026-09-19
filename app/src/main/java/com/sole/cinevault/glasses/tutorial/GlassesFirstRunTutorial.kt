package com.sole.cinevault.glasses.tutorial

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

private const val FIRST_RUN_PREFS = "cinevault_glasses_first_run"
private const val KEY_SEEN = "seen"

fun hasSeenGlassesFirstRunTutorial(context: Context): Boolean =
    context.getSharedPreferences(FIRST_RUN_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

fun markGlassesFirstRunTutorialSeen(context: Context) {
    context.getSharedPreferences(FIRST_RUN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SEEN, true)
        .apply()
}

private data class TutorialCard(val title: String, val body: String)

private val CARDS = listOf(
    TutorialCard(
        title = "Your tablet is now a remote",
        body = "With glasses connected, this screen dims and becomes a touch surface — drag to move the pointer, tap to click, just like a trackpad."
    ),
    TutorialCard(
        title = "Drag to point, tap to click",
        body = "The amber halo follows your finger. A short pulse confirms a tap landed — that's the only feedback you'll get on a dark screen, so it's worth knowing to look for."
    ),
    TutorialCard(
        title = "Five fingers, anytime, brings you back",
        body = "Spread five fingers on the tablet at any point to end the glasses session immediately and return to normal playback. Works even mid-gesture."
    ),
)

/**
 * Phase 9 — shown once ever, app-wide, the first time glasses connect
 * (see MainActivity.kt's CineVaultApp for where this is gated). Every
 * step is skippable; the whole thing can be dismissed at any card.
 */
@Composable
fun GlassesFirstRunTutorial(visible: Boolean, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = {}), // swallow taps behind the card
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .glassPanel(cornerRadius = 20.dp, fill = GlassSurfaceStrong.copy(alpha = 0.92f))
                    .padding(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARDS.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (i <= index) AmberCore else AmberCore.copy(alpha = 0.18f))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                    label = "tutorial-card",
                ) { i ->
                    val card = CARDS[i]
                    Column {
                        Text(text = card.title, color = AmberCore, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = card.body, color = TextBright, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Skip",
                        color = TextMuted,
                        fontSize = 12.5.sp,
                        modifier = Modifier.clickable {
                            onDismiss()
                        },
                    )
                    Text(
                        text = if (index == CARDS.lastIndex) "Done" else "Next",
                        color = AmberCore,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AmberCore.copy(alpha = 0.14f))
                            .clickable {
                                if (index == CARDS.lastIndex) {
                                    onDismiss()
                                } else {
                                    index++
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
