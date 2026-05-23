package com.sole.cinevault

import android.Manifest
import android.app.Activity
import android.content.Context
import android.view.WindowManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    ForceCineVaultBrightness()

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
fun LocalVideoLibraryScreen(
    videos: List<VideoWithMetadata>,
    onVideosLoaded: (List<VideoWithMetadata>) -> Unit,
    onItemClick: (VideoWithMetadata) -> Unit,
    onTvGroupClick: (TvGroup) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var scanStatus by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isGridMode by remember { mutableStateOf(true) }

    val permission =
        if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (!isGranted) {
                scanStatus = "Storage permission denied"
                return@rememberLauncherForActivityResult
            }

            scope.launch {
                isLoading = true
                scanStatus = "Scanning device videos..."

                val scannedVideos = scanDeviceVideos(context)

                scanStatus = "Found ${scannedVideos.size} videos. Fetching posters..."

                val finalList =
                    scannedVideos.mapIndexed { index, item ->

                        scanStatus =
                            "Loading ${index + 1}/${scannedVideos.size}: ${item.video.name.take(28)}"

                        val episodeInfo = extractEpisodeInfo(item.video.name)

                        if (episodeInfo != null) {
                            val tv =
                                try {
                                    TmdbClient.api.searchTv(
                                        bearerToken = BuildConfig.TMDB_TOKEN,
                                        query = episodeInfo.showName
                                    ).results.firstOrNull()
                                } catch (e: Exception) {
                                    null
                                }
                            val episodeDetails =
                                try {

                                    if (tv?.id != null) {

                                        TmdbClient.api.getEpisodeDetails(
                                            bearerToken = BuildConfig.TMDB_TOKEN,
                                            seriesId = tv.id,
                                            seasonNumber = episodeInfo.season,
                                            episodeNumber = episodeInfo.episode
                                        )

                                    } else null

                                } catch (e: Exception) {
                                    null
                                }

                            VideoWithMetadata(
                                video = item.video,
                                title = tv?.name ?: episodeInfo.showName,
                                subtitle =
                                    "S${episodeInfo.season.toString().padStart(2, '0')}E${episodeInfo.episode.toString().padStart(2, '0')} • ${episodeDetails?.name ?: ""}",
                                posterUrl = tv?.poster_path?.let {
                                    "https://image.tmdb.org/t/p/w500$it"
                                },
                                backdropUrl = tv?.backdrop_path?.let {
                                    "https://image.tmdb.org/t/p/original$it"
                                },
                                episodeStill =
                                    episodeDetails?.still_path?.let {
                                        "https://image.tmdb.org/t/p/w780$it"
                                    },
                                overview = tv?.overview ?: item.overview,
                                rating = tv?.vote_average ?: item.rating,
                                imdbRating = item.imdbRating,
                                rottenTomatoesRating = item.rottenTomatoesRating,
                                tmdbId = tv?.id ?: item.tmdbId,
                                type = "tv"
                            )
                        } else {
                            val movieSearchName = cleanMovieFilename(item.video.name)

                            val movieResults =
                                try {
                                    TmdbClient.api.searchMovie(
                                        bearerToken = BuildConfig.TMDB_TOKEN,
                                        query = movieSearchName
                                    ).results
                                } catch (e: Exception) {
                                    emptyList()
                                }

                            val movie =
                                if (movieSearchName.contains("sassy girl", ignoreCase = true)) {
                                    movieResults.firstOrNull {
                                        it.release_date?.startsWith("2001") == true
                                    } ?: movieResults.firstOrNull()
                                } else {
                                    movieResults.firstOrNull()
                                }

                            VideoWithMetadata(
                                video = item.video,
                                title = movie?.title ?: item.title,
                                subtitle = movie?.release_date?.take(4) ?: item.subtitle,
                                posterUrl = movie?.poster_path?.let {
                                    "https://image.tmdb.org/t/p/w500$it"
                                },
                                backdropUrl = movie?.backdrop_path?.let {
                                    "https://image.tmdb.org/t/p/original$it"
                                },
                                overview = movie?.overview ?: item.overview,
                                rating = movie?.vote_average ?: item.rating,
                                imdbRating = item.imdbRating,
                                rottenTomatoesRating = item.rottenTomatoesRating,
                                tmdbId = movie?.id ?: item.tmdbId,
                                type = "movie"
                            )
                        }
                    }

                onVideosLoaded(finalList)

                saveLibraryCache(
                    context = context,
                    videos = finalList
                )

                scanStatus = "Loaded ${finalList.size} videos"
                delay(1200)
                scanStatus = ""
                isLoading = false
            }
        }

    val categories = listOf("All", "Movies", "TV Shows", "Downloads")
    val tvGroups = groupTvShows(videos)

    val filteredVideos =
        when (selectedCategory) {
            "TV Shows" -> emptyList()
            "Downloads" -> videos.filter {
                it.video.path.contains("download", ignoreCase = true)
            }
            "Movies" -> videos.filter { it.type != "tv" }
            else -> videos.filter { it.type != "tv" }
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070707))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { isGridMode = !isGridMode },
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray.copy(alpha = 0.28f),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isGridMode) "List" else "Grid")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !isLoading,
                    onClick = {
                        permissionLauncher.launch(permission)
                    },
                    shape = RoundedCornerShape(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray.copy(alpha = 0.30f),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isLoading)
                            scanStatus.ifBlank { "Scanning..." }
                        else
                            "Scan Device Videos"
                    )
                }

                OutlinedButton(
                    enabled = !isLoading,
                    onClick = {
                        clearLibraryCache(context)
                        onVideosLoaded(emptyList())
                        scanStatus = "Cache cleared. Scan again."
                    },
                    shape = RoundedCornerShape(40.dp)
                ) {
                    Text("Refresh")
                }
            }

            if (scanStatus.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = scanStatus,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            val cachedLibrary = loadLibraryCache(context)

            cachedLibrary?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last Scan: " + java.text.SimpleDateFormat(
                        "hh:mm a",
                        java.util.Locale.getDefault()
                    ).format(java.util.Date(it.timestamp)),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White.copy(alpha = 0.18f),
                            selectedLabelColor = Color.White,
                            containerColor = Color.Transparent,
                            labelColor = Color(0xFFB8B8B8)
                        )
                    )
                }
            }
        }

        if (!isLoading && filteredVideos.isEmpty() && tvGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No videos found. Tap Scan Device Videos.",
                        color = Color(0xFFBDBDD0),
                        fontSize = 15.sp
                    )
                }
            }
        }

        if (tvGroups.isNotEmpty() && selectedCategory in listOf("All", "TV Shows")) {
            item {
                Text(
                    text = "TV Shows",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(items = tvGroups) { show ->
                        Column(
                            modifier = Modifier
                                .width(145.dp)
                                .clickable { onTvGroupClick(show) }
                        ) {
                            PosterBox(
                                posterUrl = show.posterUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = show.showName,
                                color = Color.White,
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "${show.episodes.size} Episodes",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (filteredVideos.isNotEmpty()) {
            item {
                Text(
                    text = "Movies",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                if (isGridMode) {
                    val rowCount =
                        ((filteredVideos.size + 2) / 3).coerceAtLeast(1)

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((rowCount * 255).dp)
                    ) {
                        items(items = filteredVideos) { item ->
                            Box(
                                modifier = Modifier
                                    .width(145.dp)
                            ) {
                                LibraryGridCard(
                                    item = item,
                                    onClick = { onItemClick(item) }
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        filteredVideos.forEach { item ->
                            LibraryCard(
                                item = item,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}