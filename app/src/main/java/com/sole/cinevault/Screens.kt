package com.sole.cinevault

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.asImageBitmap
import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private fun Context.findCineActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
    NavigationBar(containerColor = Color(0xFF0D0D0D)) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            label = { Text("Home", color = Color.White) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White) }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            label = { Text("Library", color = Color.White) },
            icon = { Icon(Icons.Filled.List, contentDescription = "Library", tint = Color.White) }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            label = { Text("Search", color = Color.White) },
            icon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White) }
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            label = { Text("Settings", color = Color.White) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) }
        )
    }
}

@Composable
fun HomeScreen(
    videos: List<VideoWithMetadata>,
    onScanRequest: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState()
) {
    ForceCineVaultBrightness()

    val context = LocalContext.current
    var heroIndex by remember { mutableStateOf(0) }

    val continueWatching =
        remember(videos) {
            videos.filter {
                loadPlaybackPosition(context, it.video.path) > 15_000L
            }.take(12)
        }

    val watchHistory =
        remember(videos, continueWatching) {
            loadWatchHistoryItems(context, videos)
                .filterNot { historyItem ->
                    continueWatching.any { it.video.path == historyItem.video.path }
                }
                .take(12)
        }

    val recentlyAdded =
        remember(videos) {
            videos.take(18)
        }

    val movieItems =
        remember(videos) {
            videos.filter { it.type.equals("movie", ignoreCase = true) }.take(18)
        }

    val tvItems =
        remember(videos) {
            videos
                .filter { it.type.equals("tv", ignoreCase = true) }
                .groupBy { it.title }
                .mapNotNull { (_, episodes) ->
                    episodes.firstOrNull()
                }
                .take(18)
        }

    val localItems =
        remember(videos) {
            videos.filter {
                !it.type.equals("movie", ignoreCase = true) &&
                        !it.type.equals("tv", ignoreCase = true)
            }.take(18)
        }

    val heroCandidates =
        remember(videos, continueWatching) {
            val preferred =
                (continueWatching + videos)
                    .distinctBy { it.video.path }
                    .filter {
                        !it.backdropUrl.isNullOrBlank() ||
                                !it.posterUrl.isNullOrBlank() ||
                                !it.episodeStill.isNullOrBlank()
                    }

            preferred.ifEmpty {
                videos.take(8)
            }
        }

    val heroItem =
        heroCandidates.getOrNull(
            if (heroCandidates.isNotEmpty()) {
                heroIndex % heroCandidates.size
            } else {
                0
            }
        )

    LaunchedEffect(heroCandidates.size) {
        while (heroCandidates.size > 1) {
            delay(8500)
            heroIndex = (heroIndex + 1) % heroCandidates.size
        }
    }

    val heroMotion = rememberInfiniteTransition(label = "homeHeroMotion")
    val heroShift by heroMotion.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroShift"
    )

    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 34.dp)
    ) {
        item {
            CinematicHeroSection(
                item = heroItem,
                heroShift = heroShift,
                videosCount = videos.size,
                continueCount = continueWatching.size,
                onScanRequest = onScanRequest,
                onItemClick = onItemClick
            )
        }

        if (continueWatching.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "Continue Watching",
                    subtitle = "Pick up where you left off",
                    items = continueWatching,
                    large = true,
                    showProgress = true,
                    onItemClick = onItemClick
                )
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "Recently Added",
                    subtitle = "Fresh from your local vault",
                    items = recentlyAdded,
                    large = false,
                    showProgress = false,
                    onItemClick = onItemClick
                )
            }
        }

        if (movieItems.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "Movies",
                    subtitle = "Your cinema shelf",
                    items = movieItems,
                    large = false,
                    showProgress = true,
                    onItemClick = onItemClick
                )
            }
        }

        if (tvItems.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "TV Shows",
                    subtitle = "Episodes and seasons",
                    items = tvItems,
                    large = false,
                    showProgress = true,
                    onItemClick = onItemClick
                )
            }
        }

        if (watchHistory.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "Watch History",
                    subtitle = "Recently played",
                    items = watchHistory,
                    large = false,
                    showProgress = false,
                    onItemClick = onItemClick
                )
            }
        }

        if (localItems.isNotEmpty()) {
            item {
                CinematicPosterRow(
                    title = "Downloads & Local",
                    subtitle = "Personal and unmatched videos",
                    items = localItems,
                    large = false,
                    showProgress = true,
                    onItemClick = onItemClick
                )
            }
        }

        if (videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.07f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Scan your library to unlock your cinematic home screen.",
                        color = Color(0xFFBDBDD0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CinematicHeroSection(
    item: VideoWithMetadata?,
    heroShift: Float,
    videosCount: Int,
    continueCount: Int,
    onScanRequest: () -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val heroImage =
        item?.backdropUrl
            ?: item?.episodeStill
            ?: item?.posterUrl
            ?: item?.video?.path

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(335.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Color(0xFF151515))
    ) {
        if (!heroImage.isNullOrBlank()) {
            AsyncImage(
                model = heroImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.98f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                        translationX = heroShift * 0.35f
                        translationY = heroShift * 0.08f
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.38f),
                            Color(0xFF070707).copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.44f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = "$videosCount videos",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFC857).copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = "CINEVAULT",
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp)
        ) {
            Text(
                text = item?.title ?: "Your Cinema Library",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text =
                    if (item != null) {
                        listOf(
                            item.subtitle.ifBlank { item.type.uppercase() },
                            item.type.uppercase(),
                            if ((item.rating ?: 0.0) > 0.0) "TMDB ${String.format("%.1f", item.rating)}" else null
                        ).filterNotNull().joinToString(" • ")
                    } else {
                        "Movies • TV Shows • Local Playback"
                    },
                color = Color(0xFFE8E8E8),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (item != null) {
                            onItemClick(item)
                        } else {
                            onScanRequest()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = if (item != null) "▶ Play" else "Scan Library",
                        fontWeight = FontWeight.Black
                    )
                }

                Button(
                    onClick = onScanRequest,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (continueCount > 0) "$continueCount Continue" else "Open Library",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CinematicPosterRow(
    title: String,
    subtitle: String,
    items: List<VideoWithMetadata>,
    large: Boolean,
    showProgress: Boolean,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    val posterWidth = if (large) 168.dp else 118.dp
    val posterHeight = if (large) 108.dp else 165.dp

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (large) 22.sp else 20.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Text(
                text = "${items.size}",
                color = Color(0xFFFFD36A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(
                items = items,
                key = { it.video.path }
            ) { item ->
                val watchedPercent =
                    if (showProgress) getWatchedPercent(context, item) else 0f

                Column(
                    modifier = Modifier
                        .width(posterWidth)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onItemClick(item) }
                ) {
                    Box {
                        PosterBox(
                            posterUrl = item.backdropUrl ?: item.episodeStill ?: item.posterUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(posterHeight),
                            progress = watchedPercent,
                            videoPath = item.video.path
                        )

                        MediaBadgeRow(
                            badges = mediaBadgesFromName(item.video.name).take(2),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                        )

                        if ((item.rating ?: 0.0) > 0.0) {
                            Text(
                                text = String.format("%.1f", item.rating),
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFFFD36A))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = if (large) 11.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = item.subtitle.ifBlank { item.type.uppercase() },
                        color = Color.Gray,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

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
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(
                    text = "List",
                    selected = mode == "List",
                    onClick = { onModeChange("List") }
                )

                SmallToggleChip(
                    text = "Grid",
                    selected = mode == "Grid",
                    onClick = { onModeChange("Grid") }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (mode == "List") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(items = items, key = { it.video.path }) { item ->
                    val watchedPercent = getWatchedPercent(context, item)

                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(145.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF141414))
                            .clickable { onItemClick(item) }
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

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.82f),
                                            Color.Black.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.34f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            ProgressWithPercentCircle(progress = watchedPercent)
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.42f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(23.dp)
                            )
                        }
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(155.dp),
                                progress = watchedPercent,
                                onClick = { onItemClick(item) }
                            )
                        }

                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun WatchHistorySection(
    items: List<VideoWithMetadata>,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    Column {
        Text(
            text = "Watch History",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = items, key = { it.video.path }) { item ->
                val badges = mediaBadgesFromName(item.video.name)

                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onItemClick(item) }
                ) {
                    Box {
                        PosterBox(
                            posterUrl = item.posterUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(178.dp),
                            videoPath = item.video.path
                        )

                        MediaBadgeRow(
                            badges = badges.take(2),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Recently played",
                        color = Color(0xFFFFD36A),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
            .clickable { onClick() }
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.30f))
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Column {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                ProgressWithPercentCircle(progress = progress, compact = true)
            }
        }
    }
}

@Composable
private fun ProgressWithPercentCircle(
    progress: Float,
    compact: Boolean = false
) {
    val percent = (progress * 100).toInt().coerceIn(1, 99)
    val circleSize = if (compact) 19.dp else 22.dp
    val percentFont = if (compact) 7.sp else 8.sp
    val barHeight = if (compact) 4.dp else 5.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF03A9F4)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = percentFont,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(if (compact) 5.dp else 7.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color(0xFF03A9F4))
            )
        }
    }
}

@Composable
private fun SmallToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Color.White.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FeaturedLibrarySection(
    items: List<VideoWithMetadata>,
    mode: String,
    onModeChange: (String) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Featured From Your Library",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SmallToggleChip(
                    text = "Grid",
                    selected = mode == "Grid",
                    onClick = { onModeChange("Grid") }
                )

                SmallToggleChip(
                    text = "List",
                    selected = mode == "List",
                    onClick = { onModeChange("List") }
                )
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
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onItemClick(item) }
                            ) {
                                Box {
                                    PosterBox(
                                        posterUrl = item.posterUrl,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(132.dp),
                                        videoPath = item.video.path
                                    )

                                    MediaBadgeRow(
                                        badges = mediaBadgesFromName(item.video.name).take(2),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.take(10).forEach { item ->
                    LibraryCard(
                        item = item,
                        onClick = { onItemClick(item) }
                    )
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
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items = items, key = { it.video.path }) { item ->
                val watchedPercent = getWatchedPercent(context, item)

                Column(
                    modifier = Modifier
                        .width(145.dp)
                        .clickable { onItemClick(item) }
                ) {
                    PosterBox(
                        posterUrl = item.posterUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        progress = watchedPercent,
                        videoPath = item.video.path
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (watchedPercent > 0f) {
                        Text(
                            text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched",
                            color = Color(0xFF03A9F4),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
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

    val filteredVideos =
        remember(videos, query) {
            videos.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.video.name.contains(query, ignoreCase = true)
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies or shows...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 105.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = filteredVideos,
                key = { it.video.path }
            ) { videoItem ->
                SearchPosterCard(
                    item = videoItem,
                    onClick = {
                        onVideoClick(videoItem)
                    }
                )
            }
        }
    }
}


@Composable
private fun SearchPosterCard(
    item: VideoWithMetadata,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                onClick()
            }
    ) {
        Box {
            PosterBox(
                posterUrl =
                    if (item.type.equals("tv", ignoreCase = true))
                        item.episodeStill ?: item.backdropUrl ?: item.posterUrl
                    else
                        item.posterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable {
                        onClick()
                    },
                videoPath = item.video.path
            )

            MediaBadgeRow(
                badges = mediaBadgesFromName(item.video.name).take(2),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text =
                if (item.type.equals("tv", ignoreCase = true))
                    item.subtitle.ifBlank { item.title }
                else
                    item.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )

        if (item.subtitle.isNotBlank()) {
            Text(
                text =
                    if (item.type.equals("tv", ignoreCase = true))
                        item.title
                    else
                        item.subtitle,
                color = Color.Gray,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PosterBox(
    posterUrl: String?,
    modifier: Modifier,
    progress: Float = 0f,
    videoPath: String? = null
) {
    val context = LocalContext.current

    var localBitmap by remember(videoPath) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    var thumbnailFailed by remember(videoPath) {
        mutableStateOf(false)
    }

    LaunchedEffect(posterUrl, videoPath) {
        if (posterUrl.isNullOrBlank() && !videoPath.isNullOrBlank() && !thumbnailFailed) {
            val bitmap =
                VideoThumbnailHelper.generateLocalThumbnail(
                    context = context,
                    videoPath = videoPath
                )

            if (bitmap != null) {
                localBitmap = bitmap
            } else {
                thumbnailFailed = true
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF141414))
    ) {
        when {
            !posterUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            localBitmap != null -> {
                Image(
                    bitmap = localBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF101010)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFF03A9F4))
                )
            }
        }
    }
}


@Composable
private fun MediaBadgeRow(
    badges: List<String>,
    modifier: Modifier = Modifier
) {
    if (badges.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(items = badges) { badge ->
            Text(
                text = badge,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (badge.contains("DOLBY", ignoreCase = true) || badge.contains("ATMOS", ignoreCase = true))
                            Color(0xFFFFB300).copy(alpha = 0.82f)
                        else
                            Color.Black.copy(alpha = 0.70f)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun LibraryGridCard(
    item: VideoWithMetadata,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val badges = mediaBadgesFromName(item.video.name)
    val watchedPercent = getWatchedPercent(context, item)

    Column(
        modifier = Modifier.clickable { onClick() }
    ) {
        Box {
            PosterBox(
                posterUrl = item.posterUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp),
                progress = watchedPercent,
                videoPath = item.video.path
            )

            MediaBadgeRow(
                badges = badges.take(3),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

            if (watchedPercent > 0f) {
                Text(
                    text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.26f))
                    .padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )

                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryCard(
    item: VideoWithMetadata,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val watchedPercent = getWatchedPercent(context, item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterBox(
            posterUrl = item.posterUrl,
            modifier = Modifier
                .width(72.dp)
                .height(106.dp),
            progress = watchedPercent,
            videoPath = item.video.path
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.subtitle.ifBlank { item.video.name },
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (watchedPercent > 0f) {
                Spacer(modifier = Modifier.height(7.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(watchedPercent.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFF03A9F4))
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched",
                    color = Color(0xFF03A9F4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if ((item.rating ?: 0.0) > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "TMDB ${String.format("%.1f", item.rating)}",
                    color = Color(0xFFFFD54F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun getWatchedPercent(
    context: Context,
    item: VideoWithMetadata
): Float {
    val savedPosition = loadPlaybackPosition(context, item.video.path)

    if (savedPosition <= 15_000L) return 0f

    val estimatedDuration = 90L * 60L * 1000L
    return (savedPosition.toFloat() / estimatedDuration.toFloat()).coerceIn(0.03f, 0.98f)
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

    if (
        lower.contains("dolby vision") ||
        lower.contains("dovi") ||
        lower.contains(".dv.") ||
        lower.contains(" dv ") ||
        lower.contains("dvhe")
    ) {
        badges.add("DOLBY VISION")
    }

    if (
        lower.contains("atmos") ||
        lower.contains("dolby atmos")
    ) {
        badges.add("ATMOS")
    }

    if (
        lower.contains("truehd") ||
        lower.contains("eac3") ||
        lower.contains("e-ac3") ||
        lower.contains("ddp") ||
        lower.contains("dolby digital") ||
        lower.contains("dolby")
    ) {
        badges.add("DOLBY")
    }

    if (
        lower.contains("dts") ||
        lower.contains("dts-hd") ||
        lower.contains("dtsx")
    ) {
        badges.add("DTS")
    }

    if (
        lower.contains("3d") ||
        lower.contains("sbs") ||
        lower.contains("hsbs") ||
        lower.contains("half sbs") ||
        lower.contains("ou")
    ) {
        badges.add("3D")
    }

    if (
        lower.contains("2160p") ||
        lower.contains("4k") ||
        lower.contains("uhd")
    ) {
        badges.add("4K")
    } else if (lower.contains("1080p")) {
        badges.add("1080p")
    } else if (lower.contains("720p")) {
        badges.add("720p")
    }

    if (
        lower.contains("hdr10+") ||
        lower.contains("hdr10plus") ||
        lower.contains("hdr10 plus")
    ) {
        badges.add("HDR10+")
    } else if (
        lower.contains("hdr10")
    ) {
        badges.add("HDR10")
    } else if (
        lower.contains("hdr")
    ) {
        badges.add("HDR")
    }

    if (
        lower.contains("10bit") ||
        lower.contains("10-bit") ||
        lower.contains("10 bit")
    ) {
        badges.add("10-BIT")
    }

    if (
        lower.contains("hevc") ||
        lower.contains("x265") ||
        lower.contains("h265")
    ) {
        badges.add("HEVC")
    }

    return badges.distinct()
}
