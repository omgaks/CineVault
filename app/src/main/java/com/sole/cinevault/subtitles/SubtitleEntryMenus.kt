package com.sole.cinevault.subtitles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.GlassBorderTop
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright

// ============================================================
//  Tap CC -> HUD Dock (4 items) / Long-press CC -> Bloom (2 petals)
//  Spring + stagger values match the approved motion spec:
//  Dock:  scale 0.85->1.0 overshoot 1.04, 280ms, items stagger 40ms
//  Bloom: 450ms hold trigger (handled by the caller's onLongClick),
//         petals scale 0->1 + rotate -8deg->0deg, staggered 120ms
// ============================================================

enum class SubtitleDockItem { TRACK, SYNC, STYLE, SUPER_SUBS }
enum class SubtitleBloomItem { SETTINGS, AI }

@Composable
fun SubtitleHudDock(
    activeItem: SubtitleDockItem?,
    onItemSelected: (SubtitleDockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(SubtitleDockItem.TRACK, Icons.Rounded.List, "Track"),
        Triple(SubtitleDockItem.SYNC, Icons.Rounded.Schedule, "Sync"),
        Triple(SubtitleDockItem.STYLE, Icons.Rounded.Palette, "Style"),
        Triple(SubtitleDockItem.SUPER_SUBS, Icons.Rounded.Search, "Super subs")
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(GlassSurfaceStrong)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.05f),
                    0.4f to Color.Transparent
                )
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { index, (item, icon, label) ->
            val entry = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 40L)
                entry.animateTo(
                    1f,
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
                )
            }
            val active = activeItem == item
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = 0.85f + 0.15f * entry.value
                        scaleY = 0.85f + 0.15f * entry.value
                        alpha = entry.value
                        translationY = (1f - entry.value) * 16f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) AmberCore.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onItemSelected(item) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) AmberCore else TextBright.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    label,
                    color = if (active) AmberCore else TextBright.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SubtitleBloomMenu(
    onItemSelected: (SubtitleBloomItem) -> Unit,
    onDismiss: () -> Unit,
    anchorSize: androidx.compose.ui.unit.Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val backdropAlpha = remember { Animatable(0f) }
    val settingsEntry = remember { Animatable(0f) }
    val aiEntry = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        backdropAlpha.animateTo(1f, tween(220, easing = LinearOutSlowInEasing))
        settingsEntry.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        kotlinx.coroutines.delay(120)
        aiEntry.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }

    Box(
        modifier = modifier
            .alpha(backdropAlpha.value)
            .background(
                Brush.radialGradient(
                    listOf(AmberCore.copy(alpha = 0.10f), Color.Transparent),
                    radius = 260f
                )
            )
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        BloomPetal(
            icon = Icons.Rounded.Settings,
            label = "Settings",
            entry = settingsEntry.value,
            offset = Offset(-46f, 60f),
            onClick = { onItemSelected(SubtitleBloomItem.SETTINGS) }
        )
        BloomPetal(
            icon = Icons.Rounded.AutoAwesome,
            label = "AI",
            entry = aiEntry.value,
            offset = Offset(46f, 60f),
            onClick = { onItemSelected(SubtitleBloomItem.AI) }
        )
    }
}

// The AI sheet, deduplicated to exactly the two entries that are
// genuinely "start a job and walk away" — Auto Sync lives in the Dock's
// Sync tile, and Dialogue Sync lives in Sync -> Fine Tune, since both
// need the person to interact mid-flow rather than just wait on a pill.
@Composable
fun SubtitleAiSheet(
    onSpeechToSubs: () -> Unit,
    onAiTranslate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceStrong)
    ) {
        AiSheetRow(
            icon = Icons.Rounded.Mic,
            label = "Speech to subs",
            onClick = onSpeechToSubs
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .size(width = 0.dp, height = 1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
        AiSheetRow(
            icon = Icons.Rounded.AutoAwesome,
            label = "AI translate",
            onClick = onAiTranslate
        )
    }
}

@Composable
private fun AiSheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(16.dp))
        Text(label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BloomPetal(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    entry: Float,
    offset: Offset,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .offset(x = offset.x.dp, y = offset.y.dp)
            .graphicsLayer {
                scaleX = entry
                scaleY = entry
                alpha = entry
                rotationZ = -8f * (1f - entry)
            }
            .size(56.dp)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = AmberCore, modifier = Modifier.size(18.dp))
        Text(label, color = TextBright, fontSize = 8.sp, fontWeight = FontWeight.Medium)
    }
}
