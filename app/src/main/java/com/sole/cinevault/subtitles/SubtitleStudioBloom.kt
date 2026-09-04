package com.sole.cinevault.subtitles

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun StudioBloom(
    onSelectRoom: (SubtitleStudioTab) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss)
        )
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.Center)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) * 0.42f
                val dash = Path()
                val steps = 72
                val ring = r * 1.18f
                for (i in 0 until steps step 2) {
                    val a0 = i / steps.toFloat() * 2f * PI.toFloat()
                    val a1 = (i + 1) / steps.toFloat() * 2f * PI.toFloat()
                    dash.moveTo(c.x + cos(a0) * ring, c.y + sin(a0) * ring)
                    dash.lineTo(c.x + cos(a1) * ring, c.y + sin(a1) * ring)
                }
                drawPath(dash, AmberCore.copy(alpha = 0.45f), style = Stroke(2f, cap = StrokeCap.Round))

                listOf(225f to true, 315f to false, 135f to false, 45f to false).forEach { (deg, lit) ->
                    rotate(degrees = deg, pivot = c) {
                        val path = petalPath(c, r)
                        drawPath(
                            path,
                            brush = Brush.radialGradient(
                                colors = if (lit) {
                                    listOf(Color(0xFFFFD56A), Color(0xCCFFC857), Color(0x22000000))
                                } else {
                                    listOf(Color(0x55FFC857), Color(0x22080808), Color(0x11000000))
                                },
                                center = Offset(c.x, c.y - r * 0.55f),
                                radius = r
                            )
                        )
                        drawPath(path, AmberCore.copy(alpha = if (lit) 0.95f else 0.55f), style = Stroke(3.2f))
                    }
                }
                drawCircle(Color(0xFF1A1408), radius = r * 0.22f, center = c)
                drawCircle(AmberCore, radius = r * 0.22f, center = c, style = Stroke(3.5f))
            }

            BloomHit(Alignment.TopStart, 18.dp, 18.dp, "TRACKS", Icons.Filled.ViewList, SubtitleStudioTab.SOURCE) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectRoom(it)
            }
            BloomHit(Alignment.TopEnd, (-18).dp, 18.dp, "SYNC", Icons.Filled.Sync, SubtitleStudioTab.TIME) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectRoom(it)
            }
            BloomHit(Alignment.BottomStart, 18.dp, (-18).dp, "STYLE", Icons.Filled.Palette, SubtitleStudioTab.LOOK) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectRoom(it)
            }
            BloomHit(Alignment.BottomEnd, (-18).dp, (-18).dp, "AUTO", Icons.Filled.Settings, SubtitleStudioTab.BRAIN) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelectRoom(it)
            }

            Text(
                "STUDIO",
                color = AmberCore,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 188.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1408))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Text("CC", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BoxScope.BloomHit(
    alignment: Alignment,
    x: androidx.compose.ui.unit.Dp,
    y: androidx.compose.ui.unit.Dp,
    label: String,
    icon: ImageVector,
    tab: SubtitleStudioTab,
    onSelect: (SubtitleStudioTab) -> Unit
) {
    Column(
        modifier = Modifier
            .align(alignment)
            .offset(x = x, y = y)
            .size(132.dp)
            .clickable { onSelect(tab) }
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(22.dp))
        Text(
            label,
            color = Color(0xFFFFE7B0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun petalPath(center: Offset, radius: Float): Path {
    val tip = Offset(center.x, center.y - radius)
    val left = Offset(center.x - radius * 0.46f, center.y - radius * 0.38f)
    val right = Offset(center.x + radius * 0.46f, center.y - radius * 0.38f)
    val neck = Offset(center.x, center.y - radius * 0.12f)
    return Path().apply {
        moveTo(neck.x, neck.y)
        cubicTo(left.x, left.y, left.x, tip.y + radius * 0.12f, tip.x, tip.y)
        cubicTo(right.x, tip.y + radius * 0.12f, right.x, right.y, neck.x, neck.y)
        close()
    }
}
