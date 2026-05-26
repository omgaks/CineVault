package com.sole.cinevault

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.ui.theme.CineVaultTheme
import kotlinx.coroutines.delay

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
                CineVaultRoot()
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
fun CineVaultRoot() {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(durationMillis = 450),
        label = "cinevaultRootFade"
    ) { splashVisible ->
        if (splashVisible) {
            CineVaultSplashScreen()
        } else {
            CineVaultApp()
        }
    }
}

@Composable
fun CineVaultSplashScreen() {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
    }

    val riseOffset by animateFloatAsState(
        targetValue = if (started) 0f else 90f,
        animationSpec = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
        label = "logoRise"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cineGalaxySplash")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloAlpha"
    )

    val slowRotate by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slowRotate"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF3A2200),
                        Color(0xFF090909),
                        Color(0xFF000000)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // soft galaxy horizon under the emblem
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
                .width(310.dp)
                .height(58.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFC107).copy(alpha = 0.38f),
                            Color(0xFFFFE08A).copy(alpha = 0.62f),
                            Color(0xFFFFC107).copy(alpha = 0.38f),
                            Color.Transparent
                        )
                    )
                )
        )

        // distant star dots / sparks
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-110).dp, y = (-145).dp)
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFD36B).copy(alpha = 0.70f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 120.dp, y = (-95).dp)
                .size(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.65f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-145).dp, y = 52.dp)
                .size(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFC107).copy(alpha = 0.68f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 150.dp, y = 92.dp)
                .size(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.55f))
        )

        Box(
            modifier = Modifier
                .size(345.dp)
                .clip(RoundedCornerShape(300.dp))
                .background(Color(0xFFFFC107).copy(alpha = haloAlpha * 0.28f))
        )

        Box(
            modifier = Modifier
                .size(275.dp)
                .clip(RoundedCornerShape(240.dp))
                .background(Color(0xFFFFB300).copy(alpha = haloAlpha * 0.34f))
        )

        Box(
            modifier = Modifier
                .size(238.dp)
                .clip(RoundedCornerShape(210.dp))
                .background(Color.Black.copy(alpha = 0.58f))
                .graphicsLayer {
                    translationY = riseOffset
                    alpha = logoAlpha
                    scaleX = pulse
                    scaleY = pulse
                    rotationZ = slowRotate
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.cinevault_circle_logo),
                contentDescription = "CineVault Logo",
                modifier = Modifier.size(210.dp)
            )
        }
    }
}

@Composable
fun CineVaultApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }
    var selectedDetail by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var selectedTvGroup by remember { mutableStateOf<TvGroup?>(null) }
    var currentEpisodeList by remember { mutableStateOf<List<VideoWithMetadata>>(emptyList()) }
    var libraryVideos by remember { mutableStateOf<List<VideoWithMetadata>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val homeListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val cached = loadLibraryCache(context)
        if (cached != null && cached.videos.isNotEmpty()) {
            libraryVideos = cached.videos
        }
    }

    var secretRefreshKey by remember { mutableStateOf(0) }

    val visibleLibraryVideos =
        remember(libraryVideos, secretRefreshKey) {
            val hiddenPaths = loadSecretVideoPaths(context)
            val hiddenFolders = loadSecretFolderPaths(context)

            libraryVideos.filter { item ->
                !hiddenPaths.contains(item.video.path) &&
                        !videoIsInsideSecretFolder(item, hiddenFolders)
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
                        onPlayNext = { nextVideo: VideoWithMetadata ->
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
                            currentEpisodeList = selectedTvGroup?.episodes ?: emptyList()
                            selectedVideo = episode.video
                        }
                    )
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

                selectedTab == 2 -> {
                    SearchScreen(
                        videos = visibleLibraryVideos,
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
                        },
                        onSecretChanged = {
                            secretRefreshKey++
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        videos = visibleLibraryVideos,
                        onScanRequest = { selectedTab = 1 },
                        onItemClick = { item ->
                            currentEpisodeList = emptyList()
                            selectedDetail = item
                        },
                        listState = homeListState
                    )
                }
            }
        }
    }
}