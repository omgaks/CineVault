package com.sole.cinevault

import com.sole.cinevault.library.*

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

/**
 * Continue watching as a film strip: a dark reel with sprocket holes, one
 * frame per film, a progress ring and the time left. Each frame is at least
 * 156dp wide and the reel scrolls sideways.
 */
@Composable
fun ContinueWatchingSection(
    items: List<VideoWithMetadata>,
    onItemClick: (VideoWithMetadata) -> Unit,
    onSeeAll: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Continue",
                color = TextBright,
                fontFamily = NewsreaderFamily,
                fontSize = CineType.Section,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${items.size} IN PROGRESS",
                color = TextFaint,
                fontFamily = PlexMonoFamily,
                fontSize = CineType.Caption,
                letterSpacing = 1.4.sp
            )
            // Only worth showing when there's actually more to see than the
            // 12-item cap this row already renders.
            if (items.size >= 12) {
                Text(
                    text = "See all",
                    color = AmberCore,
                    fontSize = CineType.Caption,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSeeAll() }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0C12))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(bottom = 12.dp)
        ) {
            FilmStripHoles()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                items(items) { item ->
                    val positionMs = loadPlaybackPosition(context, item.video.path)
                    val durationMs = loadDuration(context, item.video.path)
                    val watchedPercent = getWatchedPercent(context, item)
                    val minutesLeft = if (durationMs > positionMs) ((durationMs - positionMs) / 60_000L).toInt() else 0

                    Column(
                        modifier = Modifier.width(160.dp).clickable { onItemClick(item) },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.6f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceMid)
                        ) {
                            ContinueArt(item)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605060A))))
                            )
                            ProgressRing(
                                progress = watchedPercent,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.title,
                                color = TextBright,
                                fontSize = CineType.Label,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (minutesLeft > 0) "${formatBudgetMinutes(minutesLeft)} LEFT" else formatClock(positionMs),
                                color = TextMuted,
                                fontFamily = PlexMonoFamily,
                                fontSize = CineType.Caption
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The row of sprocket holes along the top of the reel. */
@Composable
private fun FilmStripHoles() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
        val holeW = 8.dp.toPx()
        val holeH = 5.dp.toPx()
        val step = 20.dp.toPx()
        var x = 4.dp.toPx()
        while (x < size.width) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.16f),
                topLeft = androidx.compose.ui.geometry.Offset(x, 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(holeW, holeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
            )
            x += step
        }
    }
}

/** A small ring that fills as the film is watched. */
@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    androidx.compose.foundation.Canvas(modifier = modifier.size(30.dp)) {
        val strokeWidth = 2.4.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        val inset = strokeWidth
        val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawCircle(color = Color(0x99050608))
        drawArc(
            color = Color.White.copy(alpha = 0.20f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = AmberCore,
            startAngle = -90f,
            sweepAngle = 360f * clamped,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke
        )
    }
}

/**
 * The picture for one reel frame. Real landscape art (backdrop or still) fills
 * the frame. If only a portrait poster exists, it is shown whole on the right
 * with the same poster blurred as ambient fill on the left, so faces and title
 * art are never sliced off.
 */
@Composable
private fun ContinueArt(item: VideoWithMetadata) {
    val landscapeImage = item.backdropUrl ?: item.episodeStill
    val fallbackPoster = item.posterUrl
    if (!landscapeImage.isNullOrBlank()) {
        AsyncImage(
            model = landscapeImage,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else if (!fallbackPoster.isNullOrBlank()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1.35f).fillMaxHeight()) {
                AsyncImage(
                    model = fallbackPoster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.4f; scaleY = 1.4f
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = android.graphics.RenderEffect
                                    .createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                        }
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                AsyncImage(
                    model = fallbackPoster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = TextFaint, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
internal fun ResumePosterBox(
    item: VideoWithMetadata,
    modifier: Modifier,
    progress: Float,
    onClick: () -> Unit
) {
    Column(modifier = modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SpaceMid)
        ) {
            val imageModel = item.posterUrl ?: item.video.path
            if (imageModel.isNotBlank()) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = TextFaint, modifier = Modifier.size(30.dp))
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
            }

            RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = item.title, color = TextBright, fontSize = CineType.Caption, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProgressBar(progress: Float, compact: Boolean = false) {
    val barHeight = if (compact) 3.dp else 4.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AmberGlow)
        )
    }
}

@Composable
internal fun SmallToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AmberGlow.copy(alpha = 0.85f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (selected) Color.Black else TextMuted, fontSize = CineType.Caption, fontWeight = FontWeight.Bold)
    }
}

// Quick play button overlay — gold circle, kept from CV1
@Composable
internal fun QuickPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AmberGlow.copy(alpha = 0.92f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ── Poster corner chips — small glass badges, screenshot style ───────────────

@Composable
internal fun ImdbCornerChip(value: String, modifier: Modifier = Modifier) {
    RatingLogoChip(logo = R.drawable.ic_imdb, description = "IMDb", value = value, modifier = modifier)
}

@Composable
internal fun TmdbCornerChip(value: String, modifier: Modifier = Modifier) {
    RatingLogoChip(logo = R.drawable.ic_tmdb, description = "TMDB", value = value, modifier = modifier)
}

@Composable
internal fun RottenTomatoesCornerChip(value: String, modifier: Modifier = Modifier) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    RatingLogoChip(
        logo = R.drawable.ic_rotten_tomatoes,
        description = "Rotten Tomatoes",
        value = value,
        modifier = modifier,
        tint = if (isFresh) null else Color(0xFF8BC34A)
    )
}

// Shared vertical stack of whichever rating badges the item actually has —
// IMDb, Rotten Tomatoes, TMDB. Used by every poster-forward card (Library
// grid, Home Featured, Continue Watching grid mode, Search results) so
// ratings show up consistently everywhere a poster is the primary visual,
// not just on the Detail screen. Renders nothing if the item has no ratings
// at all (e.g. Select-Folder items, which never go through TMDB enrichment).
@Composable
internal fun RatingBadgeStack(item: VideoWithMetadata, modifier: Modifier = Modifier) {
    val imdb = item.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = item.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = item.rating?.takeIf { it > 0.0 }
    if (imdb == null && rt == null && tmdb == null) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (imdb != null) ImdbCornerChip(value = imdb)
        if (rt != null) RottenTomatoesCornerChip(value = rt)
        if (tmdb != null) TmdbCornerChip(value = String.format("%.1f", tmdb))
    }
}

@Composable
internal fun CornerChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextBright,
        fontSize = CineType.Caption,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
