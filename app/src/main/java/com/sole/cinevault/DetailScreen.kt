package com.sole.cinevault

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sole.cinevault.ui.theme.*

@Composable
fun DetailScreen(
    item: VideoWithMetadata,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val fullPath = item.video.path.replace("%20", " ")
    val resolutionBadge = detectResolution(item.video.name)
    val audioBadge = detectAudioFormat(item.video.name)
    val savedPosition = remember { loadPlaybackPosition(context, item.video.path) }
    val hasResumePosition = savedPosition > 15_000L
    val trailerSearchUrl = remember(item.title) { "https://www.youtube.com/results?search_query=${Uri.encode("${item.title} official trailer")}" }

    var castList by remember(item.video.path) { mutableStateOf(loadCastCache(context, item.tmdbId, item.type)) }
    var castLoading by remember(item.video.path) { mutableStateOf(castList.isEmpty()) }

    LaunchedEffect(item.tmdbId, item.type) {
        val id = item.tmdbId ?: return@LaunchedEffect
        val cached = loadCastCache(context, id, item.type)
        if (cached.isNotEmpty()) { castList = cached; castLoading = false; return@LaunchedEffect }
        castLoading = true
        val freshCast = try {
            val credits = if (item.type == "tv") TmdbClient.api.getTvCredits(BuildConfig.TMDB_TOKEN, id) else TmdbClient.api.getMovieCredits(BuildConfig.TMDB_TOKEN, id)
            credits.cast.take(16)
        } catch (e: Exception) { emptyList() }
        castList = freshCast
        saveCastCache(context, id, item.type, freshCast)
        castLoading = false
    }

    Box(modifier = Modifier.fillMaxSize().background(SpaceBlack)) {
        val heroImage = item.backdropUrl ?: item.posterUrl
        if (!heroImage.isNullOrBlank()) {
            AsyncImage(model = heroImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(520.dp).blur(32.dp))
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.72f), SpaceBlack), startY = 0f, endY = 1400f)))

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().height(380.dp)) {
                if (!heroImage.isNullOrBlank()) { AsyncImage(model = heroImage, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), SpaceBlack))))
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.Bottom) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(model = item.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(100.dp).height(148.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title, color = TextBright, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(text = item.subtitle, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                Spacer(modifier = Modifier.height(14.dp))

                // Rating badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    if ((item.rating ?: 0.0) > 0.0) TmdbBadge(value = formatRating(item.rating))
                    if (!item.imdbRating.isNullOrBlank() && item.imdbRating != "N/A") ImdbBadge(value = item.imdbRating!!)
                    if (!item.rottenTomatoesRating.isNullOrBlank() && item.rottenTomatoesRating != "N/A") RottenTomatoesBadge(value = item.rottenTomatoesRating!!)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tech badges
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TechBadge(item.type.uppercase())
                    if (resolutionBadge.isNotBlank()) TechBadge(resolutionBadge)
                    if (audioBadge.isNotBlank()) TechBadge(audioBadge)
                    TechBadge(item.video.name.substringAfterLast(".").uppercase())
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = onPlay, shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGlow, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasResumePosition) "Resume ${formatResumeTime(savedPosition)}" else "Play", fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerSearchUrl))) },
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurface, contentColor = TextBright),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                        Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trailer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!item.overview.isNullOrBlank()) {
                    SectionTitle("Overview")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = item.overview!!, color = TextMuted, fontSize = 15.sp, lineHeight = 22.sp)
                    Spacer(modifier = Modifier.height(26.dp))
                }

                SectionTitle("Cast & Crew")
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    castLoading && castList.isEmpty() -> Text(text = "Loading cast...", color = TextFaint, fontSize = 14.sp)
                    castList.isEmpty() -> Text(text = "Cast info not available.", color = TextFaint, fontSize = 14.sp)
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(castList) { cast -> CastCard(cast = cast, movieName = item.title) } }
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(text = "File", color = TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = fullPath, color = TextFaint.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(modifier = Modifier.height(110.dp))
            }
        }

        // Back button — glass circle
        Box(modifier = Modifier.align(Alignment.TopStart).padding(14.dp).size(42.dp).clip(CircleShape).background(GlassSurfaceStrong).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = TextBright, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Rating badges — glass styled ──────────────────────────────────────────────
@Composable
private fun TmdbBadge(value: String) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF032541)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF90CEA1), Color(0xFF01B4E4)))).padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text(text = "TMDB", color = Color(0xFF032541), fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(imageVector = Icons.Rounded.Star, contentDescription = null, tint = Color(0xFF01D277), modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ImdbBadge(value: String) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5C518)).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "IMDb", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(imageVector = Icons.Rounded.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RottenTomatoesBadge(value: String) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    val bgColor = if (isFresh) Color(0xFFFA320A) else Color(0xFF4A7C59)
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bgColor).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val cx = size.width / 2f; val cy = size.height / 2f; val r = size.width * 0.38f
            if (isFresh) {
                drawCircle(color = Color(0xFFFF6B47), radius = r, center = Offset(cx, cy + 2f))
                drawCircle(color = Color(0xFFCC2200), radius = r * 0.7f, center = Offset(cx - r * 0.2f, cy + 2f))
                val path = Path().apply { moveTo(cx, cy - r * 0.3f); lineTo(cx - 2f, cy - r); lineTo(cx + 2f, cy - r) }
                drawPath(path, color = Color(0xFF2E7D32), style = Fill)
            } else {
                drawCircle(color = Color(0xFF8BC34A).copy(alpha = 0.9f), radius = r, center = Offset(cx, cy))
                drawCircle(color = Color(0xFF558B2F), radius = r * 0.5f, center = Offset(cx + r * 0.2f, cy - r * 0.1f))
            }
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = value, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TechBadge(text: String) {
    Text(text = text, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GlassSurface).padding(horizontal = 10.dp, vertical = 5.dp))
}

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun detectResolution(fileName: String): String {
    val lower = fileName.lowercase()
    return when { lower.contains("2160p") || lower.contains("4k") || lower.contains("uhd") -> "4K"; lower.contains("1080p") -> "1080p"; lower.contains("720p") -> "720p"; lower.contains("480p") -> "480p"; else -> "" }
}

private fun detectAudioFormat(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.contains("truehd") && lower.contains("atmos") -> "TrueHD Atmos"; lower.contains("atmos") -> "Atmos"; lower.contains("truehd") -> "TrueHD"
        lower.contains("dts-hd") || lower.contains("dtshd") -> "DTS-HD"; lower.contains("dts") -> "DTS"; lower.contains("ddp") || lower.contains("dd+") || lower.contains("eac3") -> "DD+"
        lower.contains("aac") -> "AAC"; lower.contains("ac3") -> "AC3"; else -> ""
    }
}

private fun formatResumeTime(positionMs: Long): String { val s = positionMs / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec) }

@Composable
private fun CastCard(cast: TmdbCastMember, movieName: String) {
    val context = LocalContext.current
    val imageUrl = cast.profile_path?.let { "https://image.tmdb.org/t/p/w300$it" }
    val safeName = cast.name ?: "Unknown"
    Column(modifier = Modifier.width(82.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode("$safeName $movieName")}"))) }, horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun SectionTitle(text: String) { Text(text = text, color = TextBright, fontSize = 20.sp, fontWeight = FontWeight.Bold) }

private fun formatRating(rating: Double?): String = if (rating == null || rating <= 0.0) "N/A" else String.format("%.1f", rating)

private fun castCacheKey(tmdbId: Int?, type: String) = "cast_${type}_${tmdbId ?: 0}"

private fun saveCastCache(context: Context, tmdbId: Int, type: String, cast: List<TmdbCastMember>) {
    try { context.getSharedPreferences("cinevault_cast_cache", Context.MODE_PRIVATE).edit().putString(castCacheKey(tmdbId, type), Gson().toJson(cast)).apply() } catch (_: Exception) {}
}

private fun loadCastCache(context: Context, tmdbId: Int?, type: String): List<TmdbCastMember> {
    if (tmdbId == null) return emptyList()
    return try { val json = context.getSharedPreferences("cinevault_cast_cache", Context.MODE_PRIVATE).getString(castCacheKey(tmdbId, type), null) ?: return emptyList(); Gson().fromJson(json, object : TypeToken<List<TmdbCastMember>>() {}.type) } catch (_: Exception) { emptyList() }
}
