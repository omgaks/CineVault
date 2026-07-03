package com.sole.cinevault

import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// FIX: This is now the ONE shared findCineActivity() for the whole app.
// Removed the duplicate private copies from LocalVideoLibraryScreen.kt and MainActivity.kt
// to resolve "Conflicting overloads" / "Overload resolution ambiguity" build errors.

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
        containerColor = Color(0xFF0D0D0D),
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
                        color = if (selected) Color(0xFFE8A020) else Color(0xFF666666),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) Color(0xFFE8A020) else Color(0xFF555555),
                            modifier = Modifier.size(22.dp)
                        )
                        if (selected) {
                            Spacer(Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFFE8A020))
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
    onItemClick: (VideoWithMetadata) -> Unit
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

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(285.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF161616))
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
                                    Color(0xCC080808)
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(144.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.cinevault_circle_logo),
                        contentDescription = "CineVault Logo",
                        modifier = Modifier.size(117.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Your Cinema Library",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Movies • TV Shows • Local Playback",
                        color = Color(0xFFE6E6E6),
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onScanRequest,
                        shape = RoundedCornerShape(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE8A020).copy(alpha = 0.85f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(if (videos.isEmpty()) "Scan Library" else "Open Library")
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
                    onItemClick = onItemClick
                )
            }
        } else {
            item {
                Text(
                    text = "Scan your library to see posters and Continue Watching here.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 15.sp
                )
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
                items(items) { item ->
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

                            ProgressBar(progress = watchedPercent)
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFE8A020).copy(alpha = 0.85f)),
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
                .background(Color.Black.copy(alpha = 0.45f))
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

                ProgressBar(progress = progress, compact = true)
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    compact: Boolean = false
) {
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
                .background(Color(0xFFE8A020))
        )
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
                if (selected) Color(0xFFE8A020).copy(alpha = 0.85f)
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
                                PosterBox(
                                    posterUrl = item.posterUrl,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(148.dp),
                                    videoPath = item.video.path
                                )

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
                                        color = Color(0xFF888888),
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
            items(items) { item ->
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
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (watchedPercent > 0f) {
                        Spacer(modifier = Modifier.height(5.dp))
                        ProgressBar(progress = watchedPercent)
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
        videos.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.video.name.contains(query, ignoreCase = true)
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies or shows...", color = Color(0xFF666666)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFE8A020),
                unfocusedBorderColor = Color(0xFF333333)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredVideos) { videoItem ->
                SearchPosterCard(
                    item = videoItem,
                    onClick = { onVideoClick(videoItem) }
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
            .clickable { onClick() }
    ) {
        PosterBox(
            posterUrl = item.posterUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            videoPath = item.video.path
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )

        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                color = Color(0xFF888888),
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
            val bitmap = VideoThumbnailHelper.generateLocalThumbnail(
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
            .background(Color(0xFF131313))
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
                        .background(Color(0xFF111111)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF444444),
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
                            Color.Black.copy(alpha = 0.65f)
                        )
                    )
                )
        )

        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFFE8A020))
                )
            }
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

            if (badges.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = badges) { badge ->
                        Text(
                            text = badge,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE8A020).copy(alpha = 0.85f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

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
                        color = Color.White.copy(alpha = 0.65f),
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
            .background(Color(0xFF131313))
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
                color = Color(0xFF888888),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (watchedPercent > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                ProgressBar(progress = watchedPercent)
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "${(watchedPercent * 100).toInt().coerceIn(1, 99)}% watched",
                    color = Color(0xFFE8A020),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if ((item.rating ?: 0.0) > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "★ ${String.format("%.1f", item.rating)}",
                    color = Color(0xFFE8A020),
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

    if (lower.contains("3d") || lower.contains("sbs") ||
        lower.contains("hsbs") || lower.contains("half sbs") ||
        lower.contains("ou")) badges.add("3D")

    if (lower.contains("hevc") || lower.contains("x265") ||
        lower.contains("h265")) badges.add("HEVC")

    if (lower.contains("2160p") || lower.contains("4k")) {
        badges.add("4K")
    } else if (lower.contains("1080p")) {
        badges.add("1080p")
    } else if (lower.contains("720p")) {
        badges.add("720p")
    }

    if (lower.contains("hdr")) badges.add("HDR")
    if (lower.contains("atmos")) badges.add("ATMOS")

    return badges.distinct()
}
