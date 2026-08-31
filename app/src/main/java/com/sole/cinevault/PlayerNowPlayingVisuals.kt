package com.sole.cinevault

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.sole.cinevault.ui.theme.*

@Composable
internal fun NowPlayingTitlePill(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val infinite = rememberInfiniteTransition(label = "titlePulse")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "titleDotAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
            .border(1.dp, Brush.horizontalGradient(listOf(AmberGlow.copy(alpha = 0.15f), AmberGlow.copy(alpha = 0.55f), AmberGlow.copy(alpha = 0.15f))), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberCore.copy(alpha = dotAlpha)))
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = text, color = TextBright, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FloatingScoreCapsule(meta: VideoWithMetadata?, vertical: Boolean = false) {
    if (meta == null) return
    val imdb = meta.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = meta.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = meta.rating?.takeIf { it > 0.0 }
    if (imdb == null && rt == null && tmdb == null) return

    val entries: List<@Composable () -> Unit> = buildList {
        if (imdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = imdb, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (rt != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TomatoLogoMark(value = rt)
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = rt, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tmdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TmdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = String.format("%.1f", tmdb), color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (vertical) {
        Column(
            modifier = Modifier.glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { entries.forEach { it() } }
    } else {
        Row(
            modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) { entries.forEach { it() } }
    }
}

@Composable
private fun RatingLogoGlow(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val breathe = rememberInfiniteTransition(label = "ratingGlow")
    val glowAlpha by breathe.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ratingGlowAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .amberGlow(radius = size * 1.4f, alpha = glowAlpha),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ImdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_imdb),
            contentDescription = "IMDb",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TmdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_tmdb),
            contentDescription = "TMDB",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TomatoLogoMark(value: String) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_rotten_tomatoes),
            contentDescription = "Rotten Tomatoes",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isFresh) ColorFilter.tint(Color(0xFF8BC34A)) else null
        )
    }
}
