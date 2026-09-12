package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DecoderAmber = Color(0xFFFFB300)

@Composable
internal fun PlaybackDecoderStatusPill(
    status: ActiveVideoDecoderStatus,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when (status.kind) {
        ActiveVideoDecoderKind.HARDWARE -> "HW"
        ActiveVideoDecoderKind.SOFTWARE -> "SW"
        ActiveVideoDecoderKind.UNKNOWN -> null
    }

    val pillLabel = label ?: ""

    AnimatedVisibility(
        visible = visible && label != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.58f))
                .border(
                    width = 1.dp,
                    color = DecoderAmber.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(DecoderAmber, CircleShape)
            )

            Text(
                text = pillLabel,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
