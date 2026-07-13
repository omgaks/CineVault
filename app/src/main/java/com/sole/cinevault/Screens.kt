package com.sole.cinevault

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import android.app.Activity
import android.content.Context
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sole.cinevault.ui.theme.*

// Single shared findCineActivity() for the whole app
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
private object HomeScrollState {
    var index: Int = 0
    var offset: Int = 0
}

@Composable
private fun ForceCineVaultBrightness() {
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

@Composable
fun CineBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        Triple(Icons.Filled.Home, "Home", 0),
        Triple(Icons.Filled.List, "Library", 1),
        Triple(Icons.Filled.Search, "Search", 2),
        Triple(Icons.Filled.Settings, "Settings", 3)
    )

    NavigationBar(
        containerColor = SpaceDeep,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { (icon, label, index) ->
            val selected = selectedTab == index
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(index) },
                label = {
                    Text(
                        text = label,
                        color = if (selected) AmberGlow else TextFaint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) AmberGlow else TextFaint,
                            modifier = Modifier.size(22.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberGlow)
                            )
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
fun HomeScreen(
    videos: List<VideoWithMetadata>,
    onScanRequest: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    ForceCineVaultBrightness()

    val context = LocalContext.current
    var continueMode by remember { mutableStateOf("List") }
    var featuredMode by remember { mutableStateOf("Grid") }

    val continueWatching =
        videos.filter {
            loadPlaybackPosition(context, it.video.path) > 15_000L
        }.take(12)

    val heroImage =
        continueWatching.firstOrNull { it.backdropUrl != null }?.backdropUrl
            ?: videos.firstOrNull { it.backdropUrl != null }?.backdropUrl
            ?: videos.firstOrNull { it.posterUrl != null }?.posterUrl

    // Restores scroll position from HomeScrollState so returning from Detail
    // (or switching tabs and back) lands where you left off, not the top.
    val homeListState = rememberLazyListState(
        initialFirstVisibleItemIndex = HomeScrollState.index,
        initialFirstVisibleItemScrollOffset = HomeScrollState.offset
    )
    LaunchedEffect(homeListState) {
        snapshotFlow { homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> HomeScrollState.index = i; HomeScrollState.offset = o }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        state = homeListState,
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(SpaceMid)
            ) {
                if (heroImage != null) {
                    AsyncImage(
                        model = heroImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.98f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x66000000),
                                    SpaceBlack.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // LOGO FIX: removed the 80.dp background square (Color.Black alpha 0.38f
                // + RoundedCornerShape container) that was sitting behind the logo. The
                // logo now renders directly with a soft drop-shadow for contrast against
                // varying hero backdrops, instead of a visible box.
                Image(
                    painter = painterResource(id = R.drawable.cinevault_circle_logo),
                    contentDescription = "CineVault Logo",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(56.dp)
                        .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Your Cinema Library",
                        color = TextBright,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "Movies • TV Shows • Local Playback",
                        color = TextMuted,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onScanRequest,
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberGlow.copy(alpha = 0.90f),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(if (videos.isEmpty()) "Scan Library" else "Open Library", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingSection(
                    items = continueWatching,
                    mode = continueMode,
                    onModeChange = { continueMode = it },
                    onItemClick = onItemClick
                )
            }
        }

        if (videos.isNotEmpty()) {
            item {
                FeaturedLibrarySection(
                    items = videos.take(18),
                    mode = featuredMode,
                    onModeChange = { featuredMode = it },
                    onItemClick = onItemClick,
                    onPlayClick = onPlayClick
                )
            }
        } else {
            item {
                Text(
                    text = "Scan your library to see posters and Continue Watching here.",
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        }
    }
}

// ── Continue Watching — screenshot style: clean card, timestamps at the
//    corners, thin progress line at the bottom edge, title BELOW the card ──
@Composable
fun ContinueWatchingSection(
    items: List<VideoWithMetadata>,
    mode: String,
    onModeChange: (String) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Continue Watching",
                color = TextBright,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassSurface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(text = "List", selected = mode == "List", onClick = { onModeChange("List") })
                SmallToggleChip(text = "Grid", selected = mode == "Grid", onClick = { onModeChange("Grid") })
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (mode == "List") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(items) { item ->
                    val positionMs = loadPlaybackPosition(context, item.video.path)
                    val durationMs = loadDuration(context, item.video.path)
                    val watchedPercent = getWatchedPercent(context, item)

                    Column(modifier = Modifier.width(250.dp).clickable { onItemClick(item) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(SpaceMid)
                        ) {
                            val image = item.backdropUrl ?: item.episodeStill ?: item.posterUrl
                            if (!image.isNullOrBlank()) {
                                AsyncImage(
                                    model = image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Scrim just behind the timestamps
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))))
                            )

                            Text(
                                text = formatClock(positionMs),
                                color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp)
                            )
                            if (durationMs > 0L) {
                                Text(
                                    text = formatClock(durationMs),
                                    color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 8.dp)
                                )
                            }

                            // Thin progress line hugging the bottom edge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.18f))
                            ) {
                                Box(modifier = Modifier.fillMaxWidth(watchedPercent.coerceIn(0f, 1f)).fillMaxHeight().background(AmberGlow))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            val gridItems = items.take(6)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            val watchedPercent = getWatchedPercent(context, item)
                            ResumePosterBox(
                                item = item,
                                modifier = Modifier.weight(1f),
                                progress = watchedPercent,
                                onClick = { onItemClick(item) }
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumePosterBox(
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
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = item.title, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProgressBar(progress: Float, compact: Boolean = false) {
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
private fun SmallToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AmberGlow.copy(alpha = 0.85f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (selected) Color.Black else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// Quick play button overlay — gold circle, kept from CV1
@Composable
private fun QuickPlayButton(onClick: () -> Unit) {
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
private fun ImdbCornerChip(value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFF5C518)).padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            Text(text = "IMDb", color = Color.Black, fontSize = 6.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = value, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CornerChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextBright,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
fun FeaturedLibrarySection(
    items: List<VideoWithMetadata>,
    mode: String,
    onModeChange: (String) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onPlayClick: (VideoWithMetadata) -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured From Your Library",
                color = TextBright,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassSurface)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(text = "Grid", selected = mode == "Grid", onClick = { onModeChange("Grid") })
                SmallToggleChip(text = "List", selected = mode == "List", onClick = { onModeChange("List") })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mode == "Grid") {
            val gridItems = items.take(9)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridItems.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                LibraryGridCard(item = item, onClick = { onItemClick(item) }, onPlayClick = onPlayClick)
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(10).forEach { item ->
                    LibraryCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun HomeRow(
    title: String,
    items: List<VideoWithMetadata>,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(text = title, color = TextBright, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items) { item ->
                val watchedPercent = getWatchedPercent(context, item)
                Column(
                    modifier = Modifier.width(145.dp).clickable { onItemClick(item) }
                ) {
                    PosterBox(
                        posterUrl = item.posterUrl,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        progress = watchedPercent,
                        videoPath = item.video.path,
                        episodeStill = item.episodeStill,
                        backdropUrl = item.backdropUrl,
                        type = item.type
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = item.title, color = TextBright, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    if (item.subtitle.isNotBlank()) {
                        Text(text = item.subtitle, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    videos: List<VideoWithMetadata>,
    query: String,
    onQueryChange: (String) -> Unit,
    onVideoClick: (VideoWithMetadata) -> Unit
) {
    ForceCineVaultBrightness()

    val filteredVideos = videos.filter {
        it.title.contains(query, ignoreCase = true) ||
                it.video.name.contains(query, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies or shows...", color = TextFaint) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextBright,
                unfocusedTextColor = TextBright,
                focusedBorderColor = AmberGlow,
                unfocusedBorderColor = GlassBorderTop
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredVideos) { videoItem ->
                SearchPosterCard(item = videoItem, onClick = { onVideoClick(videoItem) })
            }
        }
    }
}

@Composable
private fun SearchPosterCard(item: VideoWithMetadata, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        PosterBox(
            posterUrl = item.posterUrl,
            modifier = Modifier.fillMaxWidth().height(210.dp),
            videoPath = item.video.path,
            episodeStill = item.episodeStill,
            backdropUrl = item.backdropUrl,
            type = item.type
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = item.title, color = TextBright, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (item.subtitle.isNotBlank()) {
            Text(text = item.subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── PosterBox — CLEAN poster art. No gradients, no text overlays. Just the
//    poster and (optionally) a thin progress line hugging the bottom edge. ──
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceMid)
    ) {
        when {
            !displayImage.isNullOrBlank() -> {
                AsyncImage(model = displayImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            localBitmap != null -> {
                Image(bitmap = localBitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
    val imdbChip = item.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
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

            if (imdbChip != null) {
                ImdbCornerChip(value = imdbChip, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
            }
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
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )

        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                color = TextMuted,
                fontSize = 9.sp,
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
                Text(text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched", color = AmberGlow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            if ((item.rating ?: 0.0) > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "★ ${String.format("%.1f", item.rating)}", color = AmberGlow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun getWatchedPercent(context: Context, item: VideoWithMetadata): Float {
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
