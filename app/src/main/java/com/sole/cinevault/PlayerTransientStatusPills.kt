package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.glassPanel

@Composable
internal fun BoxScope.PlayerTransientStatusPills(
    autoSubtitleStatus: String,
    showSeekPreview: Boolean,
    isLandscape: Boolean,
    isZoomMode: Boolean,
) {
    AnimatedVisibility(
        visible = autoSubtitleStatus.isNotBlank() && !showSeekPreview,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(120)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = if (isLandscape) 54.dp else 86.dp)
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = autoSubtitleStatus,
            color = AmberCore,
            fontSize = if (isLandscape) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceStrong)
                .widthIn(max = if (isLandscape) 320.dp else 300.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }

    AnimatedVisibility(
        visible = isZoomMode && !showSeekPreview,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(120)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = if (isLandscape) 54.dp else 90.dp)
    ) {
        Text(
            text = "⛶  Fill",
            color = AmberCore,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
