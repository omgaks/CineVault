package com.sole.cinevault

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.ui.theme.CineVaultTheme

object CineVaultPlayerHolder {
    var currentPlayer: ExoPlayer? = null
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }

        setContent {
            CineVaultTheme {
                CineVaultApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!isInPictureInPictureMode) {
                CineVaultPlayerHolder.currentPlayer?.pause()
            }
        } else {
            CineVaultPlayerHolder.currentPlayer?.pause()
        }
    }

    override fun onDestroy() {
        CineVaultPlayerHolder.currentPlayer?.pause()
        super.onDestroy()
    }
}

@Composable
fun CineVaultApp() {
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }
    var selectedDetail by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var selectedTvGroup by remember { mutableStateOf<TvGroup?>(null) }
    var currentEpisodeList by remember { mutableStateOf<List<VideoWithMetadata>>(emptyList()) }
    var libraryVideos by remember { mutableStateOf<List<VideoWithMetadata>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val cached = loadLibraryCache(context)
        if (cached != null && cached.videos.isNotEmpty()) {
            libraryVideos = cached.videos
        }
    }

    BackHandler {
        when {
            selectedVideo != null -> selectedVideo = null
            selectedDetail != null -> selectedDetail = null
            selectedTvGroup != null -> selectedTvGroup = null
            selectedTab != 0 -> selectedTab = 0
        }
    }

    Scaffold(
        containerColor = Color(0xFF070707),
        bottomBar = {
            if (selectedVideo == null) {
                CineBottomBar(selectedTab) { tab ->
                    selectedTab = tab
                    selectedVideo = null
                    selectedDetail = null
                    selectedTvGroup = null
                }
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                selectedVideo != null -> {
                    VideoPlayerScreen(
                        video = selectedVideo!!,
                        episodeList = currentEpisodeList,
                        onBack = { selectedVideo = null },
                        onPlayNext = { nextVideo ->
                            selectedVideo = nextVideo.video
                        }
                    )
                }

                selectedTvGroup != null -> {
                    TvShowDetailScreen(
                        group = selectedTvGroup!!,
                        onBack = {
                            selectedTvGroup = null
                        },
                        onEpisodeClick = { episode ->
                            currentEpisodeList =
                                selectedTvGroup?.episodes ?: emptyList()

                            selectedVideo =
                                episode.video
                        }
                    )
                }

                selectedTab == 2 -> {
                    SearchScreen(
                        videos = libraryVideos,
                        query = searchQuery,
                        onQueryChange = { newQuery -> searchQuery = newQuery },
                        onVideoClick = { item ->
                            currentEpisodeList = emptyList()
                            selectedDetail = item
                        }
                    )
                }

                selectedTab == 3 -> {
                    SettingsScreen()
                }

                selectedDetail != null -> {
                    DetailScreen(
                        item = selectedDetail!!,
                        onBack = {
                            selectedDetail = null
                        },
                        onPlay = {
                            currentEpisodeList = listOf(selectedDetail!!)
                            selectedVideo = selectedDetail!!.video
                        }
                    )
                }

                selectedTab == 1 -> {
                    LocalVideoLibraryScreen(
                        videos = libraryVideos,
                        onVideosLoaded = { loadedVideos ->
                            libraryVideos = loadedVideos
                            saveLibraryCache(
                                context = context,
                                videos = loadedVideos
                            )
                        },
                        onItemClick = { item ->
                            currentEpisodeList = emptyList()
                            selectedDetail = item
                        },
                        onTvGroupClick = { group ->
                            selectedTvGroup = group
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        videos = libraryVideos,
                        onScanRequest = { selectedTab = 1 },
                        onItemClick = { item ->
                            currentEpisodeList = emptyList()
                            selectedDetail = item
                        }
                    )
                }
            }
        }
    }
}