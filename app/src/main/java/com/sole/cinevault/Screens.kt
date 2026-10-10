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

fun Context.findCineActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Persists the Home screen's scroll position across navigation — same pattern
// as LibraryScrollState in LocalVideoLibraryScreen.kt. A plain object survives
// composable disposal since it isn't tied to the composition.
internal object HomeScrollState {
    var index: Int = 0
    var offset: Int = 0
}

// Forces max screen brightness while any non-player screen is on-screen —
// a deliberate design choice (poster art and glass UI read better bright,
// especially on a small phone screen), NOT the same thing as the bug fixed
// in VideoPlayerScreen.kt/MainActivity.kt. The actual bug there was forcing
// brightness INCONSISTENTLY (only Home/Search had it, so leaving them for
// Library or Settings looked like "dimming"); the fix is applying it
// uniformly across every browsing screen, not removing it. The player is
// the one deliberate exception — video content should respect the real
// screen brightness (plus the manual swipe gesture), since forcing 100%
// there was actively harmful for night viewing and battery during long
// playback sessions. Short screens (browsing) vs. long screens (watching)
// genuinely warrant different defaults.
@Composable
// Not private — called from LocalVideoLibraryScreen.kt too, and Kotlin's
// `private` on a top-level function means file-private, not just
// package-private, so it had to be opened up to be callable across files.
fun ForceCineVaultBrightness() {
    val context = LocalContext.current
    val activity = context.findCineActivity()

    DisposableEffect(Unit) {
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = 1.0f
        }
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}

/**
 * The floating glass dock from the design kit. A rounded pill sits above
 * the content; the active tab opens into an amber pill with its label.
 * It handles the system gesture/navigation bar itself, and never grows
 * wider than 520dp so it stays a pill on tablets (a side rail for wide
 * windows comes with the navigation shell).
 */
@Composable
fun CineBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple(Icons.Filled.Home, "Home", 0),
        Triple(Icons.Filled.List, "Library", 1),
        Triple(Icons.Filled.Settings, "Settings", 3)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(68.dp)
                .clip(CircleShape)
                .background(SpaceMid.copy(alpha = 0.86f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEach { (icon, label, index) ->
                val selected = selectedTab == index
                val tabShape = CircleShape
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                        .clip(tabShape)
                        .then(
                            if (selected) Modifier
                                .background(AmberCore.copy(alpha = 0.14f))
                                .border(1.dp, AmberCore.copy(alpha = 0.5f), tabShape)
                            else Modifier
                        )
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onTabSelected(index) }
                        .padding(horizontal = if (selected) 18.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) AmberCore else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = AmberCore,
                            fontSize = CineType.Label,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * The dock on wide or landscape windows: the same three places, stacked on the
 * left edge. Each item is 56dp tall with its name underneath, so it reads
 * without relying on colour.
 */
@Composable
fun CineSideRail(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple(Icons.Filled.Home, "Home", 0),
        Triple(Icons.Filled.List, "Library", 1),
        Triple(Icons.Filled.Settings, "Settings", 3)
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(SpaceMid.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        tabs.forEach { (icon, label, index) ->
            val selected = selectedTab == index
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .then(
                        if (selected) Modifier
                            .background(AmberCore.copy(alpha = 0.14f))
                            .border(1.dp, AmberCore.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        else Modifier
                    )
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onTabSelected(index) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) AmberCore else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    color = if (selected) AmberCore else TextMuted,
                    fontSize = CineType.Caption,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PosterBox(
    posterUrl: String?,
    modifier: Modifier,
    progress: Float = 0f,
    videoPath: String? = null,
    episodeStill: String? = null,
    backdropUrl: String? = null,
    type: String = ""
) {
    val context = LocalContext.current

    // Only TV episodes use stills — movies always use poster
    val displayImage = when {
        type.equals("tv", ignoreCase = true) && !episodeStill.isNullOrBlank() -> episodeStill
        !posterUrl.isNullOrBlank() -> posterUrl
        else -> null
    }

    var localBitmap by remember(videoPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var thumbnailFailed by remember(videoPath) { mutableStateOf(false) }

    LaunchedEffect(displayImage, videoPath) {
        if (displayImage.isNullOrBlank() && !videoPath.isNullOrBlank() && !thumbnailFailed) {
            val bitmap = VideoThumbnailHelper.generateLocalThumbnail(context = context, videoPath = videoPath)
            if (bitmap != null) localBitmap = bitmap else thumbnailFailed = true
        }
    }

    val bitmapSnapshot = localBitmap
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceMid)
    ) {
        when {
            !displayImage.isNullOrBlank() -> {
                AsyncImage(model = displayImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            bitmapSnapshot != null -> {
                Image(bitmap = bitmapSnapshot.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().background(SpaceDeep), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = TextFaint, modifier = Modifier.size(42.dp))
                }
            }
        }

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
            }
        }
    }
}

// ── LibraryGridCard — the new poster card. Clean art, corner chips,
//    gold QuickPlay (CV1), title + year BELOW. Long-press for actions. ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {},
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val badges = mediaBadgesFromName(item.video.name)
    val watchedPercent = getWatchedPercent(context, item)
    val qualityChip = listOfNotNull(
        badges.firstOrNull { it == "4K" || it == "1080p" || it == "720p" },
        badges.firstOrNull { it == "HDR" }
    ).joinToString(" ")

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { onLongPress(item) }
        )
    ) {
        Box {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                progress = watchedPercent,
                videoPath = item.video.path,
                episodeStill = item.episodeStill,
                backdropUrl = item.backdropUrl,
                type = item.type
            )

            RatingBadgeStack(item = item, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            if (qualityChip.isNotBlank()) {
                CornerChip(text = qualityChip, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }

            // Gold QuickPlay — kept from CV1
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                QuickPlayButton { onPlayClick(item) }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            color = TextBright,
            fontSize = CineType.Caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )

        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                color = TextMuted,
                fontSize = CineType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryCard(
    item: VideoWithMetadata,
    onClick: () -> Unit,
    onLongPress: (VideoWithMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    val watchedPercent = getWatchedPercent(context, item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SpaceMid)
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress(item) })
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterBox(
            posterUrl = item.posterUrl,
            modifier = Modifier.width(72.dp).height(106.dp),
            progress = watchedPercent,
            videoPath = item.video.path,
            episodeStill = item.episodeStill,
            backdropUrl = item.backdropUrl,
            type = item.type
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.subtitle.ifBlank { item.video.name }, color = TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (watchedPercent > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                ProgressBar(progress = watchedPercent)
                Spacer(modifier = Modifier.height(5.dp))
                Text(text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched", color = AmberGlow, fontSize = CineType.Caption, fontWeight = FontWeight.SemiBold)
            }

            if ((item.rating ?: 0.0) > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "★ ${String.format("%.1f", item.rating)}", color = AmberGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun formatClock(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

internal fun getWatchedPercent(context: Context, item: VideoWithMetadata): Float {
    val savedPosition = loadPlaybackPosition(context, item.video.path)
    if (savedPosition <= 15_000L) return 0f
    // Use real saved duration if available, fall back to 90min estimate
    val realDuration = loadDuration(context, item.video.path)
    val duration = if (realDuration > 60_000L) realDuration else 90L * 60L * 1000L
    return (savedPosition.toFloat() / duration.toFloat()).coerceIn(0.03f, 0.98f)
}

fun groupTvShows(videos: List<VideoWithMetadata>): List<TvGroup> {
    return videos
        .filter { it.type == "tv" }
        .groupBy { it.title }
        .map { (title, episodes) ->
            TvGroup(
                showName = title,
                posterUrl = episodes.firstOrNull()?.posterUrl,
                backdropUrl = episodes.firstOrNull()?.backdropUrl,
                episodes = episodes.sortedBy { it.subtitle }
            )
        }
        .sortedBy { it.showName }
}

fun mediaBadgesFromName(fileName: String): List<String> {
    val lower = fileName.lowercase()
    val badges = mutableListOf<String>()

    if (lower.contains("3d") || lower.contains("sbs") || lower.contains("hsbs") ||
        lower.contains("half sbs") || lower.contains("ou")) badges.add("3D")
    if (lower.contains("hevc") || lower.contains("x265") || lower.contains("h265")) badges.add("HEVC")
    if (lower.contains("2160p") || lower.contains("4k")) badges.add("4K")
    else if (lower.contains("1080p")) badges.add("1080p")
    else if (lower.contains("720p")) badges.add("720p")
    if (lower.contains("hdr")) badges.add("HDR")
    if (lower.contains("atmos")) badges.add("ATMOS")

    return badges.distinct()
}
