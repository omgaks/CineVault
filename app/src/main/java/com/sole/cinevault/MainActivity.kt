package com.sole.cinevault

import android.os.Build
import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import com.sole.cinevault.ui.theme.CineVaultTheme
import kotlinx.coroutines.delay

object CineVaultPlayerHolder {
    var currentPlayer: ExoPlayer? = null
}

// FIX: Player screen hides system bars completely (true immersive/fullscreen)
// NOTE: findCineActivity() already exists in Screens.kt — reused here, not redefined.
fun Activity.enterImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

// FIX: Every other screen (Home/Library/Search/Settings) shows normal system bars
fun Activity.exitImmersiveModeForPlayer() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.attributes = window.attributes.apply {
            screenBrightness = 1.0f
        }

        // Required: allows player to draw behind system bars for true immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
        delay(2200)
        showSplash = false
    }

    Crossfade(
        targetState = showSplash,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
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
        delay(120)
        started = true
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    val logoRise by animateFloatAsState(
        targetValue = if (started) 0f else 60f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "logoRise"
    )

    val taglineAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 900,
            delayMillis = 600,
            easing = FastOutSlowInEasing
        ),
        label = "taglineAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glowBreathe")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer { alpha = glowAlpha * logoAlpha }
                .blur(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE8A020).copy(alpha = 0.9f),
                            Color(0xFFB07818).copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(160.dp)
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        translationY = logoRise
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.cinevault_circle_logo),
                    contentDescription = "CineVault Logo",
                    modifier = Modifier.size(148.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            androidx.compose.material3.Text(
                text = "CINEVAULT",
                color = Color.White.copy(alpha = logoAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.material3.Text(
                text = "Your Personal Cinema",
                color = Color(0xFFE8A020).copy(alpha = taglineAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .graphicsLayer { alpha = taglineAlpha }
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFE8A020).copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun CineVaultApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var selectedVideo by remember { mutableStateOf<VideoFile?>(null) }
    var selectedMediaType by remember { mutableStateOf("local") }
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

    // FIX: Only the player screen should be immersive (nav bar hidden).
    // Every other screen (Home/Library/Search/Settings) must show normal system bars.
    val activity = context.findCineActivity()

    LaunchedEffect(selectedVideo) {
        if (selectedVideo != null) {
            activity?.enterImmersiveModeForPlayer()
        } else {
            activity?.exitImmersiveModeForPlayer()
        }
    }

    Scaffold(
        containerColor = Color(0xFF080808),
        bottomBar = {
            if (selectedVideo == null) {
                CineBottomBar(selectedTab) { tab ->
                    selectedTab = tab
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
                        mediaType = selectedMediaType,
                        onBack = { selectedVideo = null },
                        onPlayNext = { nextVideo ->
                            selectedMediaType = nextVideo.type
                            selectedVideo = nextVideo.video
                        }
                    )
                }

                selectedTvGroup != null -> {
                    TvShowDetailScreen(
                        group = selectedTvGroup!!,
                        onBack = { selectedTvGroup = null },
                        onEpisodeClick = { episode ->
                            currentEpisodeList = selectedTvGroup?.episodes ?: emptyList()
                            selectedMediaType = episode.type
                            selectedVideo = episode.video
                        }
                    )
                }

                selectedDetail != null -> {
                    DetailScreen(
                        item = selectedDetail!!,
                        onBack = { selectedDetail = null },
                        onPlay = {
                            // Pass full library so autoplay can find next video
                            currentEpisodeList = libraryVideos
                            selectedMediaType = selectedDetail!!.type
                            selectedVideo = selectedDetail!!.video
                        }
                    )
                }

                selectedTab == 3 -> SettingsScreen(
                    onOpenScanSources = { selectedTab = 1 },
                    onOpenStreamUrl = { selectedTab = 1 }
                )

                selectedTab == 2 -> {
                    SearchScreen(
                        videos = libraryVideos,
                        query = searchQuery,
                        onQueryChange = { newQuery -> searchQuery = newQuery },
                        onVideoClick = { item ->
                            currentEpisodeList = libraryVideos
                            selectedDetail = item
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
                            currentEpisodeList = libraryVideos
                            selectedDetail = item
                        },
                        onTvGroupClick = { group ->
                            selectedTvGroup = group
                        },
                        onSecretChanged = {
                            val cached = loadLibraryCache(context)
                            if (cached != null) libraryVideos = cached.videos
                        }
                    )
                }

                else -> {
                    HomeScreen(
                        videos = libraryVideos,
                        onScanRequest = { selectedTab = 1 },
                        onItemClick = { item ->
                            currentEpisodeList = libraryVideos
                            selectedDetail = item
                        },
                        onPlayClick = { item ->
                            currentEpisodeList = libraryVideos
                            selectedMediaType = item.type
                            selectedVideo = item.video
                        }
                    )
                }
            }
        }
    }
}
