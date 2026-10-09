package com.sole.cinevault.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.glassPanel

@Composable
fun SmartSkipPill(segment: SmartSegment, remainingMs: Long, onClick: () -> Unit) {
    val title = when (segment.type) {
        SegmentType.RECAP -> "SKIP RECAP"
        SegmentType.INTRO -> "SKIP INTRO"
        SegmentType.PREVIEW -> "SKIP PREVIEW"
        SegmentType.COMMERCIAL -> "SKIP BREAK"
        SegmentType.CREDITS -> "SKIP CREDITS"
        SegmentType.MID_CREDITS_SCENE, SegmentType.POST_CREDITS_SCENE -> "SKIP TO SCENE"
    }
    Column(horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(AmberCore, AmberGlow)))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = Color.Black)
            Text(title, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${(remainingMs.coerceAtLeast(0L) / 1000L)}s remaining · ${segment.source}",
            color = TextBright.copy(alpha = 0.72f), fontSize = 10.sp,
            modifier = Modifier.glassPanel(12.dp, GlassSurfaceStrong).padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PostCreditNotice(
    hasExactTimestamp: Boolean,
    isMidCredits: Boolean,
    onJump: (() -> Unit)?,
    // Set when the time is CineVault's own guess rather than a timestamp from a data source.
    confidencePercent: Int? = null,
    evidence: String? = null,
    // Why no time could be found, when that is the case.
    detail: String? = null
) {
    val pulse by rememberInfiniteTransition(label = "noticeDot").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "noticeDotPulse"
    )
    Column(
        modifier = Modifier.widthIn(min = 240.dp, max = 320.dp)
            .glassPanel(24.dp, GlassSurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // A steady, glowing dot: the same one that marks the scene on the seek bar.
            Box(
                modifier = Modifier.size(18.dp).drawBehind {
                    drawCircle(AmberGlow.copy(alpha = 0.20f * pulse), radius = size.minDimension / 2f)
                    drawCircle(AmberGlow.copy(alpha = 0.45f * pulse), radius = size.minDimension / 3.2f)
                    drawCircle(AmberCore, radius = size.minDimension / 6f)
                }
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (isMidCredits) "MID-CREDITS SCENE" else "POST-CREDIT SCENE",
                    color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp
                )
                Text(
                    when {
                        hasExactTimestamp && confidencePercent != null -> "A scene likely starts ahead"
                        hasExactTimestamp -> "One scene remains \u2014 jump to it now"
                        else -> "Stay \u2014 one scene remains after the credits"
                    },
                    color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (hasExactTimestamp && confidencePercent != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(confidencePercent.coerceIn(0, 100) / 100f).height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(AmberCore, AmberGlow)))
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("$confidencePercent% sure", color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            evidence?.let {
                Spacer(Modifier.height(3.dp))
                Text("from $it", color = TextBright.copy(alpha = 0.60f), fontSize = 10.sp)
            }
        }

        if (hasExactTimestamp && onJump != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(AmberCore, AmberGlow)))
                    .clickable(onClick = onJump)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("SKIP TO SCENE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        } else if (!hasExactTimestamp && detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Couldn't pin the time. $detail",
                color = TextBright.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 13.sp
            )
        }
    }
}
