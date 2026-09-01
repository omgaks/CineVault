package com.sole.cinevault

import com.sole.cinevault.metadata.*
import com.sole.cinevault.library.*

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sole.cinevault.ui.theme.*

@Composable
internal fun rememberPillGlowAlpha(): Float {
    val infinite = rememberInfiniteTransition(label = "detailPillGlow")
    val alpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "detailPillGlowAlpha"
    )
    return alpha
}

// A visible glowing amber ring around a pill — combines the soft amberGlow
// shadow with an explicit gradient border so the effect is unmistakable even
// if amberGlow's shadow alone renders subtly on some devices.
internal fun Modifier.strongPillGlow(glow: Float, cornerRadius: androidx.compose.ui.unit.Dp = 8.dp, glowRadius: androidx.compose.ui.unit.Dp = 34.dp): Modifier = this
    .amberGlow(radius = glowRadius, alpha = glow)
    .border(
        width = 1.3.dp,
        brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = glow), AmberCore.copy(alpha = glow * 0.7f))),
        shape = RoundedCornerShape(cornerRadius)
    )

// Custom-drawn clapperboard mark for the Trailer button — more distinctive
// and on-theme than a generic library icon, at almost no extra weight.
@Composable
internal fun ClapperboardIcon(size: androidx.compose.ui.unit.Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyTop = h * 0.42f
        val corner = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f)

        // Board body
        drawRoundRect(color = tint, topLeft = Offset(0f, bodyTop), size = Size(w, h - bodyTop), cornerRadius = corner)

        // Clapper top bar
        val bar = Path().apply {
            moveTo(0f, bodyTop * 0.62f)
            lineTo(w, 0f)
            lineTo(w, bodyTop * 0.62f)
            lineTo(0f, bodyTop)
            close()
        }
        drawPath(path = bar, color = tint)

        // Diagonal cut stripes across the bar for the classic clapperboard look
        val stripeCount = 4
        val stripeWidth = w * 0.11f
        for (i in 0 until stripeCount) {
            val cx = w * (i + 0.5f) / stripeCount
            rotate(degrees = -18f, pivot = Offset(cx, bodyTop * 0.5f)) {
                drawRect(color = Color.Black.copy(alpha = 0.55f), topLeft = Offset(cx - stripeWidth / 2f, -h * 0.1f), size = Size(stripeWidth, bodyTop * 1.3f))
            }
        }
    }
}

// ── Rating badges — real logo marks, uniform 20.dp, matching the player screen, glowing ──
@Composable
internal fun TmdbBadge(value: String) {
    val glow = rememberPillGlowAlpha()
    Row(modifier = Modifier.strongPillGlow(glow).clip(RoundedCornerShape(8.dp)).background(GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(R.drawable.ic_tmdb), contentDescription = "TMDB", modifier = Modifier.height(14.dp), contentScale = ContentScale.Fit)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = value, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ImdbBadge(value: String) {
    val glow = rememberPillGlowAlpha()
    Row(modifier = Modifier.strongPillGlow(glow).clip(RoundedCornerShape(8.dp)).background(GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(R.drawable.ic_imdb), contentDescription = "IMDb", modifier = Modifier.height(16.dp), contentScale = ContentScale.Fit)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = value, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RottenTomatoesBadge(value: String) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    val glow = rememberPillGlowAlpha()
    Row(modifier = Modifier.strongPillGlow(glow).clip(RoundedCornerShape(8.dp)).background(GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_rotten_tomatoes),
            contentDescription = "Rotten Tomatoes",
            modifier = Modifier.height(16.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isFresh) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF8BC34A)) else null
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = value, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// TECH PILL — uniform height, glowing amber-glass border, small leading icon so
// format tags (type/resolution/audio/container) read as designed chips instead
// of plain text-on-background.
@Composable
internal fun TechBadge(text: String, icon: ImageVector) {
    val glow = rememberPillGlowAlpha()
    Row(
        modifier = Modifier
            .strongPillGlow(glow = glow, cornerRadius = 9.dp, glowRadius = 28.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(GlassSurface)
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AmberCore, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// GENRE CHIP — tappable pill, same glow language as the other badges, but a
// distinct pill shape (RoundedCornerShape(50)) so genres read as browsable
// categories rather than static file-format tags.
@Composable
internal fun GenreChip(text: String, onClick: () -> Unit) {
    val glow = rememberPillGlowAlpha()
    Row(
        modifier = Modifier
            .strongPillGlow(glow = glow, cornerRadius = 50.dp, glowRadius = 24.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(GlassSurface)
            .clickable { onClick() }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Cast row shimmer — replaces the old plain "Loading cast..." text with
// a row of pulsing circle+line placeholders shaped like the real CastCard
// layout below, so the loading state reads as "cast is arriving" rather
// than a blank line of text for however long the network call takes.
@Composable
internal fun CastRowShimmer() {
    val infinite = rememberInfiniteTransition(label = "castShimmer")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(animation = tween(750, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "castShimmerAlpha"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) {
            Column(modifier = Modifier.width(82.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(GlassSurfaceStrong.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.width(56.dp).height(9.dp).clip(RoundedCornerShape(4.dp)).background(GlassSurface.copy(alpha = alpha)))
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.width(40.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(GlassSurface.copy(alpha = alpha * 0.8f)))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
internal fun CastCard(cast: TmdbCastMember, movieName: String, onActorClick: (Int, String, String?) -> Unit) {
    val context = LocalContext.current
    val imageUrl = cast.profile_path?.let { "https://image.tmdb.org/t/p/w300$it" }
    val safeName = cast.name ?: "Unknown"
    val actorId = cast.id
    val onCardClick = {
        if (actorId != null && !cast.name.isNullOrBlank()) {
            // Routes into the Actor page, filtered against your own library.
            onActorClick(actorId, cast.name, cast.profile_path)
        } else {
            // No TMDB person id (older cached credits, or a rare API gap) —
            // fall back to the original web-search behavior instead of a dead tap.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode("$safeName $movieName")}")))
        }
    }
    Column(modifier = Modifier.width(82.dp).clickable { onCardClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(76.dp).clip(CircleShape).background(SpaceMid), contentAlignment = Alignment.Center) {
            if (!imageUrl.isNullOrBlank()) { AsyncImage(model = imageUrl, contentDescription = safeName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            else { Text(text = safeName.take(1).uppercase(), color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = safeName, color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!cast.character.isNullOrBlank()) { Text(text = cast.character, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
internal fun SectionTitle(text: String) { Text(text = text, color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
