package com.sole.cinevault

import com.sole.cinevault.library.*

import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.sole.cinevault.subtitles.SubtitleImportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CineVaultPlayerHolder {
    var currentPlayer: ExoPlayer? = null
    // Set/cleared by VideoPlayerScreen.kt alongside currentPlayer. Bridges
    // hardware media-button next/previous (headset, Bluetooth) into the
    // app's own episode-switching logic (playNext()/playPrevious() in
    // VideoPlayerScreen.kt), since the player only ever holds one MediaItem
    // at a time rather than a real ExoPlayer playlist — see
    // CineVaultForwardingPlayer.kt for how these get wired to the actual
    // hardware key events via the MediaSession.
    var onNextRequested: (() -> Unit)? = null
    var onPreviousRequested: (() -> Unit)? = null
    // Bridges MainActivity's onPictureInPictureModeChanged (an Android
    // lifecycle callback, not something Compose can observe directly) into
    // Compose. State-backed (unlike the two above) specifically so
    // VideoPlayerScreen.kt can react to it — hiding its overlay chrome
    // (transport controls, lock button, menus, the Auto-Sync pill) while
    // in the tiny PiP window, leaving just the bare video visible. Without
    // this, the full player UI would try to render into that postage-
    // stamp-sized window, which is what "only tiny control buttons
    // visible, nothing usable" was actually describing.
    var isInPipMode by mutableStateOf(false)
}

// "Open with CineVault" support — MainActivity receives the incoming VIEW
// (open a video file / stream link) or SEND (shared link) Intent at the
// Activity level, outside Compose entirely, but the actual navigation
// (pushing a Player destination) has to happen inside CineVaultApp()'s
// Compose back-stack. This plain observable object is the bridge between
// the two: MainActivity writes to it from onCreate/onNewIntent, and a
// LaunchedEffect inside CineVaultApp() (keyed on this value) reacts to it
// and pushes the player destination, then clears it.
object IncomingIntentHolder {
    var pendingUri: android.net.Uri? by mutableStateOf(null)
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

// FIX: was ComponentActivity — changed to FragmentActivity specifically
// because androidx.biometric's stable BiometricPrompt API (used for Secret
// Folder's unlock, replacing the deprecated KeyguardManager.
// createConfirmDeviceCredentialIntent) requires a FragmentActivity host.
// FragmentActivity extends ComponentActivity, so setContent {} and
// everything else already working here is unaffected — this is additive,
// not a behavior change to anything except gaining Fragment support.
class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { /* no-op either way — the foreground service still runs without it, it just won't show a visible notification until granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash logger — writes any uncaught exception's full stack trace to
        // an app-internal file (filesDir, always readable by the app itself
        // regardless of Android version — no adb, no file-manager access,
        // no permissions needed). Settings > About > View Crash Log reads it
        // back so a crash can actually be diagnosed from a tablet-only
        // workflow instead of guessing blind. Still calls the real default
        // handler afterward so the OS crash behavior is unchanged.
        installCrashLogger(applicationContext)

        // Cleans up imported subtitle files older than a week — see
        // SubtitleImportEngine.cleanOldCache() for why this exists (that
        // directory only ever grew, never shrank, before this). Launched
        // on lifecycleScope/IO so it never blocks app startup and cancels
        // itself automatically if the Activity doesn't survive long enough
        // to finish, which is fine for a disposable-cache cleanup pass.
        lifecycleScope.launch(Dispatchers.IO) {
            SubtitleImportEngine.cleanOldCache(applicationContext)
        }

        // Required for the lock-screen/media notification (CineVaultPlaybackService.kt)
        // to actually be visible on Android 13+. The service itself still runs and
        // keeps playback alive without this permission — it just has no visible
        // controls until granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

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

        handleIncomingIntent(intent)

        setContent {
            CineVaultTheme {
                CineVaultRoot()
            }
        }
    }

    // "Open with CineVault" (file managers, browsers, other apps sharing a
    // link) and "Share to CineVault" both arrive as a fresh Intent here.
    // android:launchMode="singleTask" (AndroidManifest.xml) routes a second
    // VIEW/SEND intent while the app is already running through THIS
    // callback instead of spawning a duplicate MainActivity instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: android.net.Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    text?.let { android.net.Uri.parse(it) }
                } else {
                    // A shared video FILE (not a link) arrives as a content:// stream Uri here.
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> null
        }
        if (uri != null) {
            IncomingIntentHolder.pendingUri = uri
        }
    }

    // FIX: previously unconditionally paused playback here — this fired on
    // ANY onStop, including just the screen locking or the app being
    // backgrounded, which is why playback used to stop the moment the
    // screen turned off. CineVaultPlaybackService.kt (a foreground
    // MediaSessionService) now owns keeping playback alive through those
    // cases and provides real lock-screen controls; there's nothing left
    // for onStop to do here.
    override fun onStop() {
        super.onStop()
    }

    // FIX: this override didn't exist at all before — meaning the app
    // could ENTER PiP mode (see the explicit enterPictureInPictureMode()
    // calls in VideoPlayerScreen.kt) but had no way to react once there.
    // Combined with the configChanges gap in AndroidManifest.xml (missing
    // smallestScreenSize, now fixed — that gap could make Android
    // recreate the whole Activity on a PiP transition, which is what was
    // actually behind "movie stopped playing and went to home screen"),
    // this bridges the mode change into Compose via
    // CineVaultPlayerHolder.isInPipMode so VideoPlayerScreen.kt can hide
    // its overlay chrome and show just the bare video, the correct look
    // for a PiP window rather than a cramped full player UI.
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        CineVaultPlayerHolder.isInPipMode = isInPictureInPictureMode
    }

    override fun onDestroy() {
        // FIX: this only ever called .pause() — never .release() or
        // cleared CineVaultPlayerHolder. In the NORMAL teardown path this
        // doesn't actually leak: VideoPlayerScreen.kt's own
        // DisposableEffect already calls exoPlayer.release() and clears
        // currentPlayer/onNextRequested/onPreviousRequested when the
        // composable is disposed, which happens as part of Compose's own
        // lifecycle binding to this Activity's onDestroy. This is
        // defense-in-depth for the abnormal case — if that composable's
        // onDispose doesn't get a chance to run for some reason, the
        // ExoPlayer instance and its native MediaCodec resources would
        // otherwise stay referenced by the static CineVaultPlayerHolder
        // object indefinitely. release() is safe to call twice (idempotent
        // per Media3), so this costs nothing even when the normal path
        // already handled it.
        CineVaultPlayerHolder.currentPlayer?.apply {
            stop()
            release()
        }
        CineVaultPlayerHolder.currentPlayer = null
        CineVaultPlayerHolder.onNextRequested = null
        CineVaultPlayerHolder.onPreviousRequested = null
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
    data class RestrictedFolderPage(val folderId: String, val folderName: String, val lastPlayedVideoPath: String? = null) : Destination()
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

    // "Open with CineVault" / "Share to CineVault" — reacts whenever
    // MainActivity writes a new Uri into IncomingIntentHolder (onCreate or
    // onNewIntent). Jumps straight into the player, same as the existing
    // Stream URL flow in Settings, rather than requiring the video to
    // already be scanned into the library.
    LaunchedEffect(IncomingIntentHolder.pendingUri) {
        val uri = IncomingIntentHolder.pendingUri ?: return@LaunchedEffect
        IncomingIntentHolder.pendingUri = null
        val isNetworkStream = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
        val displayName = resolveOpenedVideoDisplayName(context, uri)
        val video = VideoFile(path = uri.toString(), name = displayName)
        push(Destination.Player(video, if (isNetworkStream) "stream" else "local", emptyList()))
    }

    fun reloadAfterSecretChange() {
        val cached = loadLibraryCache(context)
        if (cached != null) libraryVideos = cached.videos
    }

    // FIX: loadLibraryCache() was previously called directly inside this
    // LaunchedEffect, which runs on the main-safe dispatcher by default —
    // meaning a synchronous SharedPreferences/file read of a large cached
    // library (hundreds of videos with full metadata) could cause a brief
    // main-thread hitch right at cold start, exactly the kind of startup
    // jank a cache-first strategy is supposed to avoid. Wrapped in
    // Dispatchers.IO so the read itself happens off the main thread; only
    // the resulting state assignment (libraryVideos = ...) touches Compose,
    // which is safe since withContext resumes back on the calling context.
    LaunchedEffect(Unit) {
        val cached = withContext(Dispatchers.IO) { loadLibraryCache(context) }
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
                        onCuratedCollectionClick = { name -> push(Destination.CuratedCollectionPage(name)) },
                        onMetadataUpdated = { updated ->
                            // Refresh what's on screen right now, no back-out needed
                            replaceTop(Destination.Detail(updated))
                            // Patch the in-memory library so Library/Home/Search
                            // rows also show the corrected title/poster immediately,
                            // not just after the next full rescan.
                            libraryVideos = libraryVideos.map { v ->
                                if (v.video.path == updated.video.path) updated else v
                            }
                        }
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
                        onPlayClick = { item -> push(Destination.Player(item.video, item.type, items)) },
                        initialScrollTargetVideoPath = dest.lastPlayedVideoPath
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
                            onRestrictedFolderClick = { folder -> push(Destination.RestrictedFolderPage(folder.id, folder.displayName, folder.lastPlayedVideoPath)) }
                        )

                        else -> HomeScreen(
                            videos = homeVisibleVideos,
                            onScanRequest = { switchTab(1) },
                            onItemClick = { item -> push(Destination.Detail(item)) },
                            onPlayClick = { item -> push(Destination.Player(item.video, item.type, libraryVideos)) },
                            // Same lambda as LocalVideoLibraryScreen's own
                            // onVideosLoaded above — Home's big Scan Library
                            // button needs this too, so a scan started from
                            // Home actually populates libraryVideos instead
                            // of just navigating to an empty Library screen.
                            onVideosLoaded = { loadedVideos ->
                                libraryVideos = loadedVideos
                                saveLibraryCache(context = context, videos = loadedVideos)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Best-effort display name for a video opened via "Open with" / "Share to
// CineVault" — content:// Uris carry a real filename via OpenableColumns,
// everything else (file://, http://, https://) falls back to the last path
// segment. Never throws; worst case the video just shows a generic name.
private fun resolveOpenedVideoDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    if (uri.scheme.equals("content", ignoreCase = true)) {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {}
    }
    return uri.lastPathSegment?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: "Video"
}

// ── Crash logger ────────────────────────────────────────────────────────
// See onCreate() above. File lives in the app's private internal storage
// (context.filesDir) — no permissions or file-manager access required to
// write it; Settings' "View Crash Log" reads it back through the app itself.
const val CRASH_LOG_FILE_NAME = "cinevault_crash_log.txt"

fun installCrashLogger(context: Context) {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val logFile = java.io.File(context.filesDir, CRASH_LOG_FILE_NAME)
            val writer = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(writer))
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val entry = "\n\n=== Crash at $timestamp (thread: ${thread.name}) ===\n$writer"
            // Keeps the log from growing forever — trims to the last ~50KB
            // (roughly the last several crashes) before appending the new one.
            val existing = try { logFile.readText() } catch (_: Exception) { "" }
            val trimmed = if (existing.length > 50_000) existing.takeLast(50_000) else existing
            logFile.writeText(trimmed + entry)
        } catch (_: Exception) {
            // If logging itself fails, don't let that mask the real crash.
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

fun readCrashLog(context: Context): String {
    return try {
        val logFile = java.io.File(context.filesDir, CRASH_LOG_FILE_NAME)
        if (logFile.exists()) logFile.readText().trim() else ""
    } catch (_: Exception) {
        ""
    }
}

fun clearCrashLog(context: Context) {
    try {
        java.io.File(context.filesDir, CRASH_LOG_FILE_NAME).delete()
    } catch (_: Exception) {}
}
