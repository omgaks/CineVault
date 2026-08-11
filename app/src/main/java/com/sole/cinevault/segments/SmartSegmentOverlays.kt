package com.sole.cinevault.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
fun PostCreditNotice(hasExactTimestamp: Boolean, isMidCredits: Boolean, onJump: (() -> Unit)?) {
    Column(
        modifier = Modifier.widthIn(min = 230.dp, max = 310.dp)
            .glassPanel(22.dp, GlassSurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            if (isMidCredits) "MID-CREDITS SCENE" else "POST-CREDIT SCENE",
            color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasExactTimestamp) "One scene remains — jump to it now."
            else "Stay—one scene remains after the credits.",
            color = TextBright, fontSize = 12.sp
        )
        if (hasExactTimestamp && onJump != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "SKIP TO SCENE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(AmberCore)
                    .clickable(onClick = onJump).padding(horizontal = 13.dp, vertical = 8.dp)
            )
        }
    }
}
