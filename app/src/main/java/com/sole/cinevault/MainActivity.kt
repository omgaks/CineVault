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

        // Previously forced screenBrightness = 1.0f here at app launch,
        // app-wide — same anti-pattern already removed from the video
        // player and from ForceCineVaultBrightness() in Screens.kt (see
        // that file for the full explanation). This was actually the root
        // cause of "Library looks dim compared to Home": Home/Search were
        // artificially forcing max brightness the whole time they were on
        // screen and only reverting it on exit, so leaving them just
        // revealed the real (non-inflated) brightness for the first time.
        // Removing all three forcing points means the app now consistently
        // respects whatever the person's actual device brightness is,
        // everywhere, all the time — and stops silently burning battery on
        // Home/Search too.

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

// ── Navigation ───────────────────────────────────────────────────────────────
// Real back-stack, replacing the previous flat set of nullable state flags
// (selectedVideo / selectedDetail / selectedTvGroup with a hardcoded priority
// order in BackHandler). That approach happened to work for simple two-deep
// paths but wasn't actually tracking navigation history — it couldn't
// correctly express "TV show -> episode player -> back -> back" or any
// deeper chain, which is needed once Actor/Director/Genre/Collection pages
// (coming next) can push several layers deep from a Detail screen.
//
// Tab is always the BOTTOM of the stack for whichever tab is active; pushing
// a new destination (Detail, TvShow, Player, and — next round — Actor/
// Director/Genre/Collection) adds on top of it. Switching tabs via the
// bottom bar resets the stack to just that tab's root, matching standard
// bottom-nav behavior (each tab keeps its own root, not a deep independent
// history when you jump tabs).
sealed class Destination {
    data class Tab(val index: Int) : Destination()
    data class Detail(val item: VideoWithMetadata) : Destination()
    data class TvShow(val group: TvGroup) : Destination()
    data class Player(val video: VideoFile, val mediaType: String, val episodeList: List<VideoWithMetadata>) : Destination()
    data class GenrePage(val genreName: String) : Destination()
    data class DirectorPage(val directorName: String) : Destination()
    data class ActorPage(val actorId: Int, val actorName: String, val profilePath: String?) : Destination()
    data class NativeCollectionPage(val collectionId: Int, val collectionName: String) : Destination()
    data class CuratedCollectionPage(val collectionName: String) : Destination()
    data class RestrictedFolderPage(val folderId: String, val folderName: String) : Destination()
}

@Composable
fun CineVaultApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var backStack by remember { mutableStateOf<List<Destination>>(listOf(Destination.Tab(0))) }
    var libraryVideos by remember { mutableStateOf<List<VideoWithMetadata>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    val current = backStack.last()
    val activeTabIndex = (backStack.firstOrNull() as? Destination.Tab)?.index ?: 0

    fun push(dest: Destination) { backStack = backStack + dest }
    fun pop() { if (backStack.size > 1) backStack = backStack.dropLast(1) }
    fun switchTab(index: Int) { backStack = listOf(Destination.Tab(index)) }
    fun replaceTop(dest: Destination) { backStack = backStack.dropLast(1) + dest }

    fun reloadAfterSecretChange() {
        val cached = loadLibraryCache(context)
        if (cached != null) libraryVideos = cached.videos
    }

    LaunchedEffect(Unit) {
        val cached = loadLibraryCache(context)
        if (cached != null && cached.videos.isNotEmpty()) {
            libraryVideos = cached.videos
        }
    }

    // Home-visible subset — excludes BOTH Secret-folder content and
    // restricted-folder content. This is also the actual fix for a
    // pre-existing leak: HomeScreen previously received the raw, unfiltered
    // libraryVideos directly. The hidden/secret filtering only ever existed
    // INSIDE LocalVideoLibraryScreen for its own local display and was never
    // propagated back up — so Secret-folder items could already have been
    // showing up in Home's Continue Watching / Featured rows this whole
    // time. Computed fresh (not remembered) on every recomposition — cheap
    // enough for a personal media library, and avoids any staleness risk
    // from a memoization key that doesn't actually track secret-folder
    // changes made via SharedPreferences.
    val secretVideoPaths = loadSecretVideoPaths(context)
    val secretFolderPaths = loadSecretFolderPaths(context)
    val homeVisibleVideos = libraryVideos.filter { item ->
        !secretVideoPaths.contains(item.video.path) &&
            !videoIsInsideSecretFolder(item, secretFolderPaths) &&
            !isRestrictedFolderItem(item)
    }

    // FIX: previously, swiping back at the root of ANY tab (Library, Search,
    // Settings) exited the app immediately. Now: pop any pushed screen first
    // (unchanged), then if sitting at a tab's root and it's not Home, fall
    // back to Home instead of exiting — only Home's own root actually exits.
    BackHandler(enabled = backStack.size > 1 || activeTabIndex != 0) {
        if (backStack.size > 1) pop() else switchTab(0)
    }

    // FIX: Only the player screen should be immersive (nav bar hidden).
    // Every other screen (Home/Library/Search/Settings) must show normal system bars.
    val activity = context.findCineActivity()
    val isPlayerActive = current is Destination.Player

    LaunchedEffect(isPlayerActive) {
        if (isPlayerActive) {
            activity?.enterImmersiveModeForPlayer()
        } else {
            activity?.exitImmersiveModeForPlayer()
        }
    }

    Scaffold(
        containerColor = Color(0xFF080808),
        bottomBar = {
            if (!isPlayerActive) {
                CineBottomBar(activeTabIndex) { tab -> switchTab(tab) }
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val dest = current) {
                is Destination.Player -> {
                    VideoPlayerScreen(
                        video = dest.video,
                        episodeList = dest.episodeList,
                        mediaType = dest.mediaType,
                        onBack = { pop() },
                        onPlayNext = { nextVideo ->
                            // Advancing to the next episode/video REPLACES the
                            // current Player entry rather than pushing a new
                            // one — otherwise Back during a long autoplay
                            // binge would have to step through every
                            // previously auto-played episode one at a time.
                            replaceTop(Destination.Player(nextVideo.video, nextVideo.type, dest.episodeList))
                        }
                    )
                }

                is Destination.TvShow -> {
                    TvShowDetailScreen(
                        group = dest.group,
                        onBack = { pop() },
                        onEpisodeClick = { episode ->
                            push(Destination.Player(episode.video, episode.type, dest.group.episodes))
                        },
                        onSecretChanged = { reloadAfterSecretChange() }
                    )
                }

                is Destination.Detail -> {
                    DetailScreen(
                        item = dest.item,
                        onBack = { pop() },
                        onPlay = {
                            // Pass full library so autoplay can find next video
                            push(Destination.Player(dest.item.video, dest.item.type, libraryVideos))
                        },
                        onGenreClick = { genreName -> push(Destination.GenrePage(genreName)) },
                        onDirectorClick = { directorName -> push(Destination.DirectorPage(directorName)) },
                        onActorClick = { actorId, actorName, profilePath -> push(Destination.ActorPage(actorId, actorName, profilePath)) },
                        onNativeCollectionClick = { id, name -> push(Destination.NativeCollectionPage(id, name)) },
                        onCuratedCollectionClick = { name -> push(Destination.CuratedCollectionPage(name)) }
                    )
                }

                is Destination.GenrePage -> {
                    val items = libraryVideos.filter { v -> v.genres.any { it.equals(dest.genreName, ignoreCase = true) } }
                    GenreScreen(
                        genreName = dest.genreName,
                        videos = libraryVideos,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.DirectorPage -> {
                    val items = libraryVideos.filter { it.director?.equals(dest.directorName, ignoreCase = true) == true }
                    DirectorScreen(
                        directorName = dest.directorName,
                        videos = libraryVideos,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.ActorPage -> {
                    val items = libraryVideos.filter { v -> v.cast.any { it.id == dest.actorId } }
                    ActorScreen(
                        actorId = dest.actorId,
                        actorName = dest.actorName,
                        profilePath = dest.profilePath,
                        videos = libraryVideos,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.NativeCollectionPage -> {
                    val items = libraryVideos.filter { it.collectionId == dest.collectionId }
                    CollectionScreen(
                        title = dest.collectionName,
                        items = items,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.CuratedCollectionPage -> {
                    val items = libraryVideos.filter { it.curatedCollections.contains(dest.collectionName) }
                    CollectionScreen(
                        title = dest.collectionName,
                        items = items,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.RestrictedFolderPage -> {
                    val items = libraryVideos.filter { folderIdFromRestrictedMarker(it.video.folderPath) == dest.folderId }
                    CollectionScreen(
                        title = dest.folderName,
                        items = items,
                        onBack = { pop() },
                        onItemClick = { item -> push(Destination.Detail(item)) },
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) }
                    )
                }

                is Destination.Tab -> {
                    when (dest.index) {
                        3 -> SettingsScreen(
                            onOpenScanSources = { switchTab(1) },
                            // FIX: previously just switched to the Library tab and
                            // discarded the typed URL entirely — Play did nothing.
                            // Now it actually pushes a Player destination for it.
                            onOpenStreamUrl = { url ->
                                val streamName = url.substringAfterLast("/").substringBefore("?").ifBlank { "Stream" }
                                push(Destination.Player(VideoFile(path = url, name = streamName), "stream", emptyList()))
                            }
                        )

                        2 -> SearchScreen(
                            videos = libraryVideos,
                            query = searchQuery,
                            onQueryChange = { newQuery -> searchQuery = newQuery },
                            onVideoClick = { item -> push(Destination.Detail(item)) }
                        )

                        1 -> LocalVideoLibraryScreen(
                            videos = libraryVideos,
                            onVideosLoaded = { loadedVideos ->
                                libraryVideos = loadedVideos
                                saveLibraryCache(context = context, videos = loadedVideos)
                            },
                            onItemClick = { item -> push(Destination.Detail(item)) },
                            onPlayClick = { item -> push(Destination.Player(item.video, item.type, libraryVideos)) },
                            onTvGroupClick = { group -> push(Destination.TvShow(group)) },
                            onSecretChanged = { reloadAfterSecretChange() },
                            onGenreClick = { genreName -> push(Destination.GenrePage(genreName)) },
                            onNativeCollectionClick = { id, name -> push(Destination.NativeCollectionPage(id, name)) },
                            onCuratedCollectionClick = { name -> push(Destination.CuratedCollectionPage(name)) },
                            onRestrictedFolderClick = { folder -> push(Destination.RestrictedFolderPage(folder.id, folder.displayName)) }
                        )

                        else -> HomeScreen(
                            videos = homeVisibleVideos,
                            onScanRequest = { switchTab(1) },
                            onItemClick = { item -> push(Destination.Detail(item)) },
                            onPlayClick = { item -> push(Destination.Player(item.video, item.type, libraryVideos)) }
                        )
                    }
                }
            }
        }
    }
}
