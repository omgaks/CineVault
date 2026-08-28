package com.sole.cinevault

import com.sole.cinevault.library.*
import com.sole.cinevault.smb.*
import com.sole.cinevault.glasses.rememberExternalDisplayState as rememberGlassesDisplayState
import com.sole.cinevault.glasses.rememberExternalVideoPresentation as rememberGlassesVideoPresentation

// All subtitle-system files (search, import, sync, appearance, dual-merge,
// providers) moved to their own package on this pass. Single wildcard
// import used deliberately instead of ~45 explicit ones, since the
// cross-reference check confirmed this file is the ONLY outside caller
// into that package.
import com.sole.cinevault.subtitles.*
import com.sole.cinevault.segments.*

import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.util.TypedValue
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoPlayerScreen(
    video: VideoFile,
    episodeList: List<VideoWithMetadata>,
    mediaType: String = "local",
    onBack: () -> Unit,
    onPlayNext: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findCineActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // ── Dedicated glasses mode (Phase 1) ───────────────────────────────────
    // Detects a USB-C DisplayPort Alt Mode external display (RayNeo glasses
    // or similar) and locks the player to landscape while it's connected —
    // these devices render a fixed-aspect virtual screen, so letting the
    // player sit in portrait while one's attached just produces an
    // unnecessarily letterboxed picture. Also auto-dims the tablet's own
    // brightness to near-zero while connected — previously this had to be
    // done manually every time (the tablet screen is just mirroring the
    // glasses' output, no need for it to be bright too), while keeping the
    // screen genuinely ON and touchable (not locked), so it still works as
    // a remote/control surface — the tablet only ever LOOKS off. A real
    // Presentation now supplies the glasses with a distinct video,
    // subtitle, control and pointer surface.
    // Reverts automatically on disconnect or when leaving the player.
    val externalDisplay by rememberGlassesDisplayState()
    var showGlassesConnectedHint by remember { mutableStateOf(false) }
    LaunchedEffect(externalDisplay.isConnected) {
        if (externalDisplay.isConnected) {
            // setRequestedOrientation() throws IllegalStateException if the
            // Activity isn't in a plain fullscreen state at that moment
            // (split-screen, floating/free-form window, or a PiP
            // transition — all real states HyperOS's tablet multitasking
            // can put an app into). An orientation lock is a nice-to-have,
            // never something that should be allowed to crash the app.
            try { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE } catch (_: Exception) {}
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = 0.02f }
            showGlassesConnectedHint = true
            delay(2200)
            showGlassesConnectedHint = false
        } else {
            try { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR } catch (_: Exception) {}
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    var audioSyncMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(audioSyncMs) { AudioSyncHolder.offsetUs = audioSyncMs * 1000L }

    var audioIconX by remember { mutableFloatStateOf(0f) }
    var subIconX by remember { mutableFloatStateOf(0f) }
    var clusterHeightPx by remember { mutableFloatStateOf(0f) }

    var currentVideo by remember { mutableStateOf(video) }
    var currentMediaType by remember { mutableStateOf(mediaType) }
    var showControls by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    // Separate from showControls specifically for the locked case — see
    // the AnimatedVisibility/absorber wiring near the lock button below
    // for the full reasoning.
    var lockButtonVisibleWhileLocked by remember { mutableStateOf(true) }
    var showTopBar by remember { mutableStateOf(true) }
    var isDraggingSeekbar by remember { mutableStateOf(false) }

    // FIX: was hardcoded to 70 regardless of the device's actual current
    // volume, meaning CineVault silently overrode whatever level the
    // person had already set the moment the player opened. Reads the
    // real starting level instead. A separate, local system-service
    // lookup is used here rather than the audioManager val declared
    // later in this function — this runs before that point in
    // composition, and Kotlin doesn't allow referencing a local variable
    // before its declaration.
    val initialMusicVolumePercent = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maximum = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        ((am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f) / maximum).toInt()
    }
    var volumePercent by remember { mutableIntStateOf(initialMusicVolumePercent) }
    var brightnessPercent by remember { mutableIntStateOf(90) }
    var showVolumeCircle by remember { mutableStateOf(false) }
    var showBrightnessCircle by remember { mutableStateOf(false) }
    var brightnessGestureKey by remember { mutableIntStateOf(0) }
    var volumeGestureKey by remember { mutableIntStateOf(0) }

    var showAudioSelector by remember { mutableStateOf(false) }
    val trackUi = remember { SubtitleTrackSelectionState() }
    val searchUi = remember { SubtitleAcquisitionUiState() }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showSrtBrowser by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepTimerActive by remember { mutableStateOf(false) }

    val appearanceUi = remember { SubtitleAppearanceUiState() }
    val coreUi = remember { SubtitleCoreUiState(context) }

    val driftUi = remember { DriftCorrectionState() }
    val studioUi = remember { SubtitleStudioUiState() }
    var autoSyncStatus by remember { mutableStateOf<AutoSyncStatus>(AutoSyncStatus.Idle) }
    val autoSubtitleFetch = remember { AutoSubtitleFetchState() }
    var menuTouchKey by remember { mutableIntStateOf(0) }

    // The "true" primary subtitle source — distinct from trackUi.originalUri
    // (which sync/drift build FROM, and which becomes the DUAL-MERGED file
    // whenever dual mode is on). Kept separately so turning dual mode back
    // off can revert to the actual primary instead of getting stuck on a
    // merged file with nothing to un-merge from.
    var audioLanguageCheckedForPath by remember { mutableStateOf<String?>(null) }
    val dualUi = remember { DualSubtitleState().apply { secondaryLanguage = coreUi.behaviorPrefs.dualSecondaryLanguage } }
    // Secondary line color for dual subtitles. Injected as an HTML
    // <font color> tag directly into the merged SRT text (see
    // mergeDualSubtitles) rather than sourced from SubtitleAppearance,
    // since that governs the PRIMARY line's native CaptionStyleCompat
    // styling — a fundamentally different rendering path that can't
    // apply per-line. Chosen for reliable contrast against every built-in
    // appearance preset's foreground color (CineVault/Netflix/Cinema/
    // Minimal are white or near-white; HighContrast/ClassicYellow are
    // pure yellow) — a saturated cyan reads clearly against both without
    // being mistaken for either. Not genuinely content-aware (true
    // auto-contrast against arbitrary video would need real-time color
    // sampling, a much bigger feature) — this is a safer general-purpose
    // default, not a guarantee for every possible background.
    val dualSecondaryColorHex = "#00E5FF"

    // Which subtitle source/track is actually active right now — the single
    // source of truth for both the checkmark in SubtitleTrackSelectorSheet
    // AND the status line under the quick menu's header. Built with the
    // exact same key format SubtitleTrackChoice uses (see
    // SubtitleTrackSelector.kt) so the two files can never silently
    // disagree about what "selected" means.

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isVideoEnded by remember { mutableStateOf(false) }
    var pendingNextEpisode by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(0) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var nextEpisodeDismissed by remember { mutableStateOf(false) }
    var autoPlayEnabled by remember { mutableStateOf(true) }
    val smartSegmentRepository = remember { SmartSegmentRepository(context.applicationContext) }
    var smartSegmentResult by remember { mutableStateOf(SmartSegmentResult()) }

    var isZoomMode by remember { mutableStateOf(false) }
    // FIX (E2): pinch-to-zoom, separate from isZoomMode above — that's a
    // binary FIT/CROP toggle (double-tap), this is continuous gesture-
    // driven scale layered on top of whichever base mode is active, same
    // as how a photo viewer lets you pinch-zoom regardless of its own
    // fit setting.
    var videoScale by remember { mutableStateOf(1f) }
    var videoOffsetX by remember { mutableStateOf(0f) }
    var videoOffsetY by remember { mutableStateOf(0f) }
    var showSeekPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableLongStateOf(0L) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSeekPreviewLarge by remember { mutableStateOf(false) }
    var previewFrames by remember { mutableStateOf<List<VideoThumbnailHelper.PreviewFrame>>(emptyList()) }
    // Bumping this forces the preview-generation LaunchedEffect below to
    // rerun even when currentVideo.path/duration haven't changed — needed
    // because Auto-Sync deliberately clears previewFrames/previewBitmap
    // mid-playback (see runAutoSync) to free memory before analysis, and
    // that effect's own keys wouldn't otherwise notice anything changed.
    var previewReloadKey by remember { mutableIntStateOf(0) }
    var edgeSwipeHint by remember { mutableStateOf("") }

    var isBuffering by remember { mutableStateOf(false) }
    var showBufferingSpinner by remember { mutableStateOf(false) }
    var stuckBufferingHint by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var errorRetryCount by remember { mutableIntStateOf(0) }

    var droppedFrameNudgeCount by remember { mutableIntStateOf(0) }
    var lastNudgeAtMs by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val playerRuntime = rememberPlayerRuntime(
        context = context,
        preferredLanguage = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en",
        autoEnableEmbeddedSubtitles = coreUi.behaviorPrefs.autoEnableEmbeddedSubtitles
    )
    val trackSelector = playerRuntime.trackSelector
    val exoPlayer = playerRuntime.player

    // This is the real secondary-display surface. Presentation creation is
    // tied to the player + physical display ID, so hot-unplug disposes only
    // the external surface and the local PlayerView below immediately takes
    // ownership of the same ExoPlayer again at the same playback position.
    val externalRatingText = remember(currentVideo.path, episodeList) {
        buildExternalRatingText(currentVideo.path, episodeList)
    }
    var localPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    var glassesSessionDisabled by remember(externalDisplay.displayId) { mutableStateOf(false) }
    val activeExternalDisplay = externalDisplay
    val externalPresentation by rememberGlassesVideoPresentation(
        player = exoPlayer,
        externalDisplay = activeExternalDisplay,
        title = if (currentMediaType.equals("stream", ignoreCase = true)) currentVideo.name else cleanVideoTitle(currentVideo.path),
        ratingText = externalRatingText,
        onBack = onBack
    )
    val externalPlayerView = if (glassesSessionDisabled) null else externalPresentation?.playerView

    LaunchedEffect(externalPlayerView, localPlayerView) {
        val localView = localPlayerView
        val externalView = externalPlayerView
        when {
            externalView != null && externalView.player !== exoPlayer -> {
                PlayerView.switchTargetView(exoPlayer, localView, externalView)
                studioUi.playerView = externalView
            }
            externalView == null && localView != null && localView.player !== exoPlayer -> {
                localView.player = exoPlayer
                studioUi.playerView = localView
            }
        }
    }

    val canDownloadExternalSubtitles = currentMediaType.equals("movie", ignoreCase = true) || currentMediaType.equals("tv", ignoreCase = true) || currentMediaType.equals("restricted", ignoreCase = true)
    val isCurrentTvShow = currentMediaType.equals("tv", ignoreCase = true)
    val isStreamMedia = currentMediaType.equals("stream", ignoreCase = true)
    val isRestrictedFolderMedia = folderIdFromRestrictedMarker(currentVideo.folderPath) != null

    // Closing the player while something is actively playing now enters
    // Picture-in-Picture instead of just tearing down the full-screen view
    // — previously "closing" left the video with no visible window at all
    // while the foreground service kept it playing audio-only in the
    // background, which read as the app losing track of what was
    // happening. Falls back to a normal exit when paused, when PiP isn't
    // supported (pre-API 26), or if entering PiP throws for any device-
    // specific reason (same defensive pattern already used elsewhere on
    // this screen for orientation-lock calls).
    //
    // NOT wired to system back anymore (see removed BackHandler below) —
    // BackHandler fires on EVERY back action, including left-edge swipe
    // and the hardware back button, which are legitimate "go to the
    // previous screen" gestures and should never trigger PiP. Only
    // Home/Recents/task-switch (Activity.onUserLeaveHint(), which lives in
    // MainActivity.kt, not this file) should ever trigger PiP-on-close.
    // Left unused here until that's wired up — kept as a plain function so
    // it's ready to call from the right place once MainActivity exposes
    // that hook, instead of rebuilding this logic from scratch then.
    fun handleExitRequest() {
        if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val actions = buildPipActions(context, exoPlayer.isPlaying)
                val entered = activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions).build()
                )
                if (entered == true) return
            } catch (_: Exception) {}
        }
        onBack()
    }

    fun closeAllMenus() {
        showAudioSelector = false
        coreUi.showSettings = false
        trackUi.showSelector = false
        searchUi.showSearch = false
        driftUi.showDialog = false
        coreUi.showAppearanceStudio = false
        studioUi.showStudio = false
        showSpeedMenu = false
        showSleepMenu = false
        showSrtBrowser = false
        searchUi.showFallback = false
        searchUi.showEmbeddedBrowser = false
        searchUi.pendingImportCandidates = null
    }

    var pendingSrtUri by remember { mutableStateOf<Uri?>(null) }

    // ── Delete confirmation + undo (Security & Privacy checklist item 3) ──
    // pendingDeletePaths holds files that have been "deleted" from the
    // person's point of view (removed from every list immediately) but
    // whose actual disk/MediaStore deletion is still delayed behind the
    // undo window below. pendingDeleteConfirmFile drives the CineVault-
    // styled warning dialog that always appears BEFORE that window starts
    // — this is a full replacement for the plain white system AlertDialog
    // that used to front this flow. Note the OS-level consent prompt on
    // API 30+ for files the app doesn't own is a system dialog Android
    // itself renders — that one can't be reskinned, only pre-empted with
    // our own warning first, which is what this does.
    val pendingDeletePaths = remember { mutableStateListOf<String>() }
    var pendingDeleteConfirmFile by remember { mutableStateOf<java.io.File?>(null) }
    var pendingConsentFile by remember { mutableStateOf<java.io.File?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val deleteConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val consentedFile = pendingConsentFile
        if (result.resultCode != Activity.RESULT_OK) {
            // Person backed out of the OS consent prompt — the file was
            // never actually deleted, so bring it back into every list
            // instead of leaving it permanently hidden.
            if (consentedFile != null) pendingDeletePaths.remove(consentedFile.absolutePath)
            Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingConsentFile = null
    }

    // Keep deletion wired to the coordinator API that exists in the
    // repository this replacement file targets. Active/dual-track detach
    // handling requires coordinated changes in the subtitle layer and is
    // intentionally left pending for that separate update.
    // subtitleDeletionCoordinator is declared further below, after
    // playCurrentVideoWithSubtitle exists — its detach/restore callbacks
    // need to call it directly.

    LaunchedEffect(sleepTimerActive, sleepTimerRemainingMs) {
        if (sleepTimerActive && sleepTimerRemainingMs > 0) {
            delay(1000)
            sleepTimerRemainingMs -= 1000
            if (sleepTimerRemainingMs <= 0) {
                sleepTimerActive = false
                sleepTimerRemainingMs = 0
                exoPlayer.pause()
                Toast.makeText(context, "Sleep timer — playback paused", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        playbackSpeed = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        showSpeedMenu = false; showControls = true
        Toast.makeText(context, "${speed}x speed", Toast.LENGTH_SHORT).show()
    }

    fun setSleepTimer(minutes: Int) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        sleepTimerMinutes = minutes
        if (minutes == 0) {
            sleepTimerActive = false; sleepTimerRemainingMs = 0
            Toast.makeText(context, "Sleep timer off", Toast.LENGTH_SHORT).show()
        } else {
            sleepTimerRemainingMs = minutes * 60 * 1000L
            sleepTimerActive = true
            Toast.makeText(context, "Sleep timer: ${minutes}min", Toast.LENGTH_SHORT).show()
        }
        showSleepMenu = false; showControls = true
    }

    // FIX: these three functions used to be plain local functions defined
    // right here — now orchestration glue calling into
    // PlaybackNavigationCoordinator (see that file for the full
    // reasoning). Every one of the 13+ call sites elsewhere in this file
    // continues to work unchanged.
    val playbackNavigationCoordinator = remember(exoPlayer) {
        PlaybackNavigationCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            trackUi = trackUi,
            coreUi = coreUi,
            getEpisodeList = { episodeList },
            getCurrentVideo = { currentVideo },
            getIsStreamMedia = { isStreamMedia },
            getPlaybackSpeed = { playbackSpeed },
            setCurrentVideo = { currentVideo = it },
            setCurrentMediaType = { currentMediaType = it },
            setEdgeSwipeHint = { edgeSwipeHint = it },
            setPlayerErrorMessage = { playerErrorMessage = it },
            setIsVideoEnded = { isVideoEnded = it },
            onPlayNext = onPlayNext
        )
    }
    fun playPrevious() = playbackNavigationCoordinator.playPrevious()
    fun playNext() = playbackNavigationCoordinator.playNext()
    fun playCurrentVideoWithSubtitle(subtitleUri: Uri? = null, resumePosition: Long = 0L, isOriginalSubtitle: Boolean = true) =
        playbackNavigationCoordinator.playCurrentVideoWithSubtitle(subtitleUri, resumePosition, isOriginalSubtitle)

    // FIX: deleting the currently-active subtitle used to leave it
    // playing from memory even after the file was gone — Media3 keeps
    // rendering whatever cues it already parsed until something
    // explicitly tells the player to drop them. onDeleteRequested fires
    // the moment deletion is requested (before the file is actually
    // gone, so Undo can cleanly restore it), detaching the subtitle
    // immediately and clearing every piece of "this is the active
    // track" state. onDeleteUndone reverses all of it if Undo is tapped
    // in time, or if the underlying file deletion itself fails.
    var detachedSubtitleForUndo by remember { mutableStateOf<java.io.File?>(null) }
    val subtitleDeletionCoordinator = remember(exoPlayer, playbackNavigationCoordinator) {
        SubtitleDeletionCoordinator(
            context = context,
            scope = scope,
            pendingDeletePaths = pendingDeletePaths,
            snackbarHostState = snackbarHostState,
            deleteConsentLauncher = deleteConsentLauncher,
            setPendingConsentFile = { pendingConsentFile = it },
            setPendingDeleteConfirmFile = { pendingDeleteConfirmFile = it },
            onDeleteRequested = { file ->
                val isActive = trackUi.selectedKey == "local:${file.absolutePath}" ||
                    trackUi.selectedKey == "downloaded" || trackUi.originalUri?.path == file.absolutePath ||
                    trackUi.primaryUri?.path == file.absolutePath
                if (isActive) {
                    detachedSubtitleForUndo = file
                    val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                    playCurrentVideoWithSubtitle(null, resumeAt, false)
                    trackUi.primaryUri = null; trackUi.originalUri = null
                    trackUi.selectedKey = "off"; trackUi.selectedLabel = ""; trackUi.selectedSource = ""
                    coreUi.subtitlesEnabled = false
                }
            },
            onDeleteUndone = { file ->
                if (detachedSubtitleForUndo?.absolutePath == file.absolutePath && file.exists()) {
                    val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                    coreUi.subtitlesEnabled = true
                    trackUi.primaryUri = Uri.fromFile(file); trackUi.originalUri = Uri.fromFile(file)
                    trackUi.selectedKey = "local:${file.absolutePath}"
                    trackUi.selectedLabel = file.nameWithoutExtension; trackUi.selectedSource = "Local"
                    playCurrentVideoWithSubtitle(Uri.fromFile(file), resumeAt, true)
                }
                detachedSubtitleForUndo = null
            }
        )
    }
    fun deleteWithUndo(file: java.io.File) = subtitleDeletionCoordinator.deleteWithUndo(file)
    fun requestDeleteSubtitle(file: java.io.File) = subtitleDeletionCoordinator.requestDeleteSubtitle(file)

    // Validated handoff for both the website-fallback flow and the
    // (now-validated) local file picker below — reuses the exact same
    // cleaning + playback pipeline every other subtitle source already
    // goes through, so this isn't a parallel/divergent code path.
    // FIX: these three functions used to be plain local functions defined
    // right here — now orchestration glue calling into
    // SubtitleSearchCoordinator (see that file for the full reasoning).
    // Every call site elsewhere in this file continues to work unchanged.
    val subtitleSearchCoordinator = remember(exoPlayer, trackSelector) {
        SubtitleSearchCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            trackSelector = trackSelector,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            studioUi = studioUi,
            getCurrentVideoPath = { currentVideo.path },
            setShowControls = { showControls = it },
            setPendingSrtUri = { pendingSrtUri = it },
            playSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
                playCurrentVideoWithSubtitle(subtitleUri, resumePosition, isOriginalSubtitle)
            }
        )
    }
    fun applyImportedWebsiteSubtitle(imported: ImportedSubtitle) = subtitleSearchCoordinator.applyImportedWebsiteSubtitle(imported)

    // FIX: fresh picks from the system file picker now go through
    // SubtitleImportEngine's real content validation (rejects HTML/binary,
    // ranks candidates inside a ZIP) instead of the old flow, which
    // assumed any picked file was already a trustworthy subtitle. Re-
    // selecting an ALREADY-KNOWN local file (nearby-discovered or
    // previously imported) still goes through the simpler pendingSrtUri
    // path elsewhere in this file — that file doesn't need re-validating.
    val srtPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        scope.launch {
            val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                SubtitleImportEngine.import(
                    context = context,
                    input = stream,
                    suggestedName = uri.lastPathSegment,
                    releaseHint = currentVideo.path,
                    preferredLanguage = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en"
                )
            } ?: SubtitleImportResult.Failure("CineVault couldn't open that file.")

            when (result) {
                is SubtitleImportResult.Success -> {
                    if (result.alternatives.isEmpty()) {
                        applyImportedWebsiteSubtitle(result.selected)
                    } else {
                        searchUi.pendingImportCandidates = result
                    }
                }
                is SubtitleImportResult.Failure -> Toast.makeText(context, result.userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // FIX: findHttpStatusDetail/friendlyPlaybackError/isTransientPlaybackError
    // used to be defined right here — now plain top-level functions in
    // PlaybackErrorFormatting.kt (see that file for the full reasoning).
    // Same package (com.sole.cinevault), so every call site below still
    // resolves with no changes at all — not even a wrapper function was
    // needed here, unlike the earlier slices, since these never touched
    // any state to begin with.

    fun performSubtitleSearch(query: String, seasonText: String, episodeText: String, language: String = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en") =
        subtitleSearchCoordinator.performSubtitleSearch(query, seasonText, episodeText, language)
    fun applySearchResult(result: SubtitleSearchResult, alsoPlay: Boolean) = subtitleSearchCoordinator.applySearchResult(result, alsoPlay)



    LaunchedEffect(currentVideo.path) {
        val savedPosition = if (isStreamMedia) 0L else loadPlaybackPosition(context, currentVideo.path)
        position = savedPosition; duration = 1L; showControls = true; showTopBar = true
        showAudioSelector = false; coreUi.showSettings = false; trackUi.showSelector = false; searchUi.showSearch = false; showSpeedMenu = false; showSleepMenu = false; showSrtBrowser = false
        searchUi.showFallback = false; searchUi.showEmbeddedBrowser = false; searchUi.pendingImportCandidates = null
        searchUi.searchResults = emptyList(); searchUi.searchStatus = ""; searchUi.searchLoading = false
        pendingNextEpisode = null; nextEpisodeCountdown = 0; showNextEpisodeOverlay = false
        nextEpisodeDismissed = false
        smartSegmentResult = SmartSegmentResult()
        previewBitmap = null; previewFrames = emptyList(); isVideoEnded = false
        playerErrorMessage = null; errorRetryCount = 0; stuckBufferingHint = false
        trackUi.originalUri = null; trackUi.appliedOffsetMs = 0L; coreUi.syncOffset = 0.0f
        driftUi.scale = 1.0f; driftUi.appliedScale = 1.0f; driftUi.pointA = null; driftUi.pointB = null
        coreUi.dialogueSyncArmed = false; coreUi.dialogueSyncReferenceMs = null; driftUi.showDialog = false
        dualUi.enabled = false; dualUi.statusText = ""; trackUi.primaryUri = null; trackUi.primaryLanguage = null; audioLanguageCheckedForPath = null
        appearanceUi.preserveOriginalStyling = false
        studioUi.gestureFeedback = ""
        autoSyncStatus = AutoSyncStatus.Idle
        trackUi.selectedKey = null; trackUi.selectedLabel = ""; trackUi.selectedSource = ""
        droppedFrameNudgeCount = 0; lastNudgeAtMs = 0L
        if (!isStreamMedia) recordWatchHistory(context, currentVideo.path, cleanVideoTitle(currentVideo.path))
        if (isRestrictedFolderMedia) updateRestrictedFolderLastPlayed(context, currentVideo.path, currentVideo.folderPath)

        // FIX: local-file match is now checked BEFORE the cached network
        // subtitle, not after — previously an old cached OpenSubtitles
        // download always won even when a local .srt sitting right next to
        // the video (almost always more release-accurate) was available.
        // A local match is also generally free/instant to check, so trying
        // it first doesn't cost anything even when it doesn't pan out.
        val localMatch = if (!isStreamMedia && coreUi.behaviorPrefs.autoLoadMatchingLocalFile) {
            withContext(Dispatchers.IO) { findBestMatchingLocalSubtitle(currentVideo.path, coreUi.behaviorPrefs.preferredLanguages) }
        } else null

        val cachedSubtitle = if (localMatch == null && !isStreamMedia && canDownloadExternalSubtitles) {
            withContext(Dispatchers.IO) { OpenSubtitlesClient.findCachedSubtitle(context, currentVideo.path, coreUi.behaviorPrefs.preferredLanguages) }
        } else null

        when {
            localMatch != null -> {
                coreUi.subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                val localUri = Uri.fromFile(localMatch.file)
                val cleanedLocalUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, localUri, coreUi.cleaningOptions) } ?: localUri
                trackUi.primaryUri = cleanedLocalUri
                trackUi.primaryLanguage = localMatch.languageCode
                playCurrentVideoWithSubtitle(cleanedLocalUri, savedPosition)
                autoSubtitleFetch.attemptedForPath = currentVideo.path
                trackUi.selectedKey = "local:${localMatch.file.absolutePath}"
                trackUi.selectedLabel = localMatch.file.name; trackUi.selectedSource = "Local file"
            }
            cachedSubtitle != null -> {
                coreUi.subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                val cleanedCachedUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, cachedSubtitle.uri, coreUi.cleaningOptions) } ?: cachedSubtitle.uri
                trackUi.primaryUri = cleanedCachedUri
                trackUi.primaryLanguage = cachedSubtitle.language
                playCurrentVideoWithSubtitle(cleanedCachedUri, savedPosition)
                autoSubtitleFetch.attemptedForPath = currentVideo.path
                trackUi.selectedKey = "downloaded"
                trackUi.selectedLabel = friendlyLanguageName(cachedSubtitle.language); trackUi.selectedSource = "OpenSubtitles"
            }
            else -> {
                playCurrentVideoWithSubtitle(resumePosition = savedPosition)
            }
        }

        if (!isStreamMedia && canDownloadExternalSubtitles && !isRestrictedFolderMedia &&
            coreUi.behaviorPrefs.autoDownloadWhenMissing && cachedSubtitle == null && localMatch == null &&
            autoSubtitleFetch.attemptedForPath != currentVideo.path
        ) {
            autoSubtitleFetch.attemptedForPath = currentVideo.path
            scope.launch {
                delay(1200); if (autoSubtitleFetch.downloadInProgress) return@launch
                autoSubtitleFetch.downloadInProgress = true
                autoSubtitleFetch.status = "Searching subtitles..."
                try {
                    val result = OpenSubtitlesClient.downloadBestSubtitleDetailed(context, currentVideo.path, coreUi.behaviorPrefs.preferredLanguages)
                    if (result is SubtitleDownloadResult.Success) {
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        coreUi.subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                        autoSubtitleFetch.status = "Subtitle loaded"
                        val cleanedResultUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, result.uri, coreUi.cleaningOptions) } ?: result.uri
                        trackUi.primaryUri = cleanedResultUri
                        trackUi.primaryLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playCurrentVideoWithSubtitle(cleanedResultUri, resumeAt)
                        trackUi.selectedKey = "downloaded"
                        trackUi.selectedLabel = friendlyLanguageName(result.language); trackUi.selectedSource = "OpenSubtitles"
                        delay(1400); autoSubtitleFetch.status = ""
                    } else {
                        autoSubtitleFetch.status = result.summary(); delay(3500); autoSubtitleFetch.status = ""
                    }
                } catch (e: Exception) {
                    autoSubtitleFetch.status = "Subtitle failed: ${e.message ?: e.javaClass.simpleName}"; delay(3500); autoSubtitleFetch.status = ""
                }
                finally { autoSubtitleFetch.downloadInProgress = false }
            }
        }
    }

    PlayerSessionLifecycle(
        context = context,
        activity = activity,
        player = exoPlayer,
        videoPath = currentVideo.path,
        isStreamMedia = isStreamMedia,
        onNextRequested = { playNext() },
        onPreviousRequested = { playPrevious() },
        onInitialBrightnessChanged = { brightnessPercent = it },
    )

    PlayerEventListener(
        context = context,
        scope = scope,
        player = exoPlayer,
        trackSelector = trackSelector,
        currentVideoPath = currentVideo.path,
        currentMediaType = currentMediaType,
        isStreamMedia = isStreamMedia,
        episodeList = episodeList,
        autoPlayEnabled = autoPlayEnabled,
        errorRetryCount = errorRetryCount,
        coreUi = coreUi,
        trackUi = trackUi,
        audioLanguageCheckedForPath = audioLanguageCheckedForPath,
        onAudioLanguageCheckedForPathChanged = { audioLanguageCheckedForPath = it },
        onBufferingChanged = { isBuffering = it },
        onErrorRetryCountChanged = { errorRetryCount = it },
        onPlayerErrorMessageChanged = { playerErrorMessage = it },
        onVideoEndedChanged = { isVideoEnded = it },
        onPlayingChanged = { isPlaying = it },
        onQueueNextEpisode = { next ->
            pendingNextEpisode = next
            nextEpisodeCountdown = 15
            showNextEpisodeOverlay = true
            showControls = true
            showTopBar = true
        },
        onAdvanceImmediately = { next ->
            currentMediaType = next.type
            currentVideo = next.video
            onPlayNext(next)
        },
        onShowControls = {
            showControls = true
            showTopBar = true
        },
        onRetryPlayback = { subtitleUri, resumePosition ->
            playCurrentVideoWithSubtitle(
                subtitleUri = subtitleUri,
                resumePosition = resumePosition,
                isOriginalSubtitle = false,
            )
        },
    )

    PlayerTimelineEffects(
        context = context,
        player = exoPlayer,
        videoPath = currentVideo.path,
        isStreamMedia = isStreamMedia,
        isDraggingSeekbar = isDraggingSeekbar,
        isBuffering = isBuffering,
        showSeekPreview = showSeekPreview,
        previewPosition = previewPosition,
        duration = duration,
        previewReloadKey = previewReloadKey,
        droppedFrameNudgeCount = droppedFrameNudgeCount,
        lastNudgeAtMs = lastNudgeAtMs,
        onPositionChanged = { position = it },
        onDurationChanged = { duration = it },
        onPlayingChanged = { isPlaying = it },
        onBufferingSpinnerChanged = { showBufferingSpinner = it },
        onStuckBufferingChanged = { stuckBufferingHint = it },
        onDroppedFrameNudgeCountChanged = { droppedFrameNudgeCount = it },
        onLastNudgeAtMsChanged = { lastNudgeAtMs = it },
        onPreviewFramesChanged = { previewFrames = it },
        onPreviewBitmapChanged = { previewBitmap = it },
        onSeekPreviewLargeChanged = { isSeekPreviewLarge = it },
    )

    PlayerPipWindowEffect(
        activity = activity,
        context = context,
        isPlaying = isPlaying
    )

    // FEATURE: minimal, correct PiP — CineVault's own overlay chrome
    // (transport controls, lock button, Auto-Sync pill) is now hidden
    // while in PiP (see the AnimatedVisibility/if conditions gated on
    // CineVaultPlayerHolder.isInPipMode elsewhere in this file), relying
    // entirely on Android's own system-drawn PiP controls instead — the
    // same standard approach most video apps use. This closes out
    // whatever menus/Studio happened to be open the moment PiP is
    // entered, so the window is guaranteed to show just clean video no
    // matter what was on screen right before minimizing.
    LaunchedEffect(CineVaultPlayerHolder.isInPipMode) {
        if (CineVaultPlayerHolder.isInPipMode) closeAllMenus()
    }

    PlayerPipActionReceiverEffect(
        context = context,
        player = exoPlayer
    )


    PlayerAutoHideEffects(
        showControls = showControls,
        showTopBar = showTopBar,
        controlsLocked = controlsLocked,
        lockButtonVisibleWhileLocked = lockButtonVisibleWhileLocked,
        isDraggingSeekbar = isDraggingSeekbar,
        showAudioSelector = showAudioSelector,
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        showSrtBrowser = showSrtBrowser,
        menuTouchKey = menuTouchKey,
        brightnessGestureKey = brightnessGestureKey,
        volumeGestureKey = volumeGestureKey,
        coreUi = coreUi,
        trackUi = trackUi,
        searchUi = searchUi,
        driftUi = driftUi,
        studioUi = studioUi,
        onHideControls = { showControls = false },
        onHideTopBar = { showTopBar = false },
        onHideLockedButton = { lockButtonVisibleWhileLocked = false },
        onHideAudioSelector = { showAudioSelector = false },
        onHideSpeedMenu = { showSpeedMenu = false },
        onHideSleepMenu = { showSleepMenu = false },
        onHideSrtBrowser = { showSrtBrowser = false },
        onHideBrightnessHud = { showBrightnessCircle = false },
        onHideVolumeHud = { showVolumeCircle = false },
    )

    // Shared by both the standalone Track Selector sheet and the Subtitle
    // Studio's Track tab — previously duplicated verbatim in both places,
    // which is exactly how the Downloaded case would have silently NOT
    // gotten cleaning applied in one of the two copies if edited by hand.
    // One function, both call sites use it.
    fun selectSubtitleTrack(choice: SubtitleTrackChoice) = subtitleSearchCoordinator.selectSubtitleTrack(choice)

    LaunchedEffect(pendingSrtUri) {
        val uri = pendingSrtUri ?: return@LaunchedEffect
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        coreUi.subtitlesEnabled = true
        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        // FIX: previously hardcoded "SRT loaded"/"SRT file loaded" even
        // when the picked file was .vtt/.ass/.ssa/.ttml — now reflects
        // what was actually loaded.
        val pickedFormat = detectSubtitleFormat(uri)
        val formatLabel = if (pickedFormat == SubtitleFormat.SRT || pickedFormat == SubtitleFormat.UNKNOWN) "Subtitle" else pickedFormat.label.substringBefore(" (")
        autoSubtitleFetch.status = "$formatLabel loaded"
        val cleanedSrtUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, uri, coreUi.cleaningOptions) } ?: uri
        trackUi.primaryUri = cleanedSrtUri
        val pickedFile = uri.path?.let { java.io.File(it) }
        // Best-effort language detection from the filename itself (e.g.
        // "Movie.hi.srt") using the same parser the auto-matcher uses —
        // stays null (unknown) for a bare "Movie.srt" with no language
        // token, which is a safe/honest fallback rather than guessing.
        trackUi.primaryLanguage = pickedFile?.name?.let { name -> parseSubtitleFilename(name).first }
        playCurrentVideoWithSubtitle(subtitleUri = cleanedSrtUri, resumePosition = resumeAt)
        trackUi.selectedKey = "local:${pickedFile?.absolutePath ?: uri.toString()}"
        trackUi.selectedLabel = pickedFile?.name ?: "Subtitle file"; trackUi.selectedSource = "Local file"
        coreUi.showSettings = false; trackUi.showSelector = false; showControls = true
        Toast.makeText(context, "$formatLabel file loaded", Toast.LENGTH_SHORT).show()
        delay(1400); autoSubtitleFetch.status = ""
        pendingSrtUri = null
    }

    val activeSubtitleFormat = remember(trackUi.originalUri) { trackUi.originalUri?.let { detectSubtitleFormat(it) } ?: SubtitleFormat.UNKNOWN }
    val isAssOrSsaFormat = activeSubtitleFormat == SubtitleFormat.ASS || activeSubtitleFormat == SubtitleFormat.SSA

    LaunchedEffect(studioUi.playerView, appearanceUi.textSizeSp, appearanceUi.bottomPadding, appearanceUi.appearance, dualUi.enabled, appearanceUi.preserveOriginalStyling, isAssOrSsaFormat) {
        val sv = studioUi.playerView?.subtitleView
        sv?.setUserDefaultStyle()
        // Embedded styling is enabled in TWO cases: dual mode (needs the
        // injected <font color> tag to render) or the person explicitly
        // asked to preserve an ASS/SSA file's own styling. Off otherwise,
        // so CineVault's own styling stays authoritative for plain SRT/VTT.
        val useEmbeddedStyles = dualUi.enabled || (appearanceUi.preserveOriginalStyling && isAssOrSsaFormat)
        sv?.setApplyEmbeddedStyles(useEmbeddedStyles); sv?.setApplyEmbeddedFontSizes(false)
        sv?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, appearanceUi.textSizeSp)
        sv?.setBottomPaddingFraction(appearanceUi.bottomPadding)
        sv?.setStyle(
            CaptionStyleCompat(
                appearanceUi.appearance.foregroundColor,
                appearanceUi.appearance.backgroundColor,
                AndroidColor.TRANSPARENT,
                appearanceUi.appearance.edgeType,
                appearanceUi.appearance.edgeColor,
                null
            )
        )
    }

    LaunchedEffect(coreUi.syncOffset, driftUi.scale, trackUi.originalUri) {
        val baseUri = trackUi.originalUri ?: return@LaunchedEffect
        if (!coreUi.subtitlesEnabled) return@LaunchedEffect
        val offsetMs = (coreUi.syncOffset * 1000f).toLong()
        if (offsetMs == trackUi.appliedOffsetMs && driftUi.scale == driftUi.appliedScale) return@LaunchedEffect
        delay(350)
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shiftedUri = withContext(Dispatchers.IO) { buildShiftedSubtitleFile(context, baseUri, offsetMs, driftUi.scale) }
        if (shiftedUri != null) {
            trackUi.appliedOffsetMs = offsetMs
            driftUi.appliedScale = driftUi.scale
            playCurrentVideoWithSubtitle(subtitleUri = shiftedUri, resumePosition = resumeAt, isOriginalSubtitle = false)
        }
    }

    // ── Dialogue Tap Sync ─────────────────────────────────────────────
    // Step 1: person pauses on a subtitle line they can read, taps "Start"
    // (armDialogueSync below) — we record the position they paused at as
    // the reference, then resume playback automatically.
    // Step 2: they tap "Tap Now" on DialogueTapSyncBar the instant they
    // HEAR that same line spoken. The additional correction needed is just
    // (where they tapped) - (where the subtitle visually appeared),
    // stacked on top of whatever sync offset was already active.
    // FIX: these eight functions used to be plain local functions defined
    // right here, inline — now orchestration glue calling into
    // SubtitleSyncToolsCoordinator (see that file for the full reasoning).
    // Every field read/written matches exactly what the original inline
    // functions touched; only where the code lives changed.
    val subtitleSyncTools = remember(exoPlayer) {
        SubtitleSyncToolsCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            coreUi = coreUi,
            driftUi = driftUi,
            dualUi = dualUi,
            trackUi = trackUi,
            dualSecondaryColorHex = dualSecondaryColorHex,
            getCurrentVideoPath = { currentVideo.path },
            playSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
                playCurrentVideoWithSubtitle(subtitleUri, resumePosition, isOriginalSubtitle)
            }
        )
    }
    fun armDialogueSync() = subtitleSyncTools.armDialogueSync()
    fun cancelDialogueSync() = subtitleSyncTools.cancelDialogueSync()
    fun confirmDialogueSyncTap() = subtitleSyncTools.confirmDialogueSyncTap()
    fun markDriftPointA(correctionSeconds: Float) = subtitleSyncTools.markDriftPointA(correctionSeconds)
    fun markDriftPointB(correctionSeconds: Float) = subtitleSyncTools.markDriftPointB(correctionSeconds)
    fun applyDriftFix() = subtitleSyncTools.applyDriftFix()
    fun fetchAndApplyDualSecondary() = subtitleSyncTools.fetchAndApplyDualSecondary()
    fun disableDualSubtitles() = subtitleSyncTools.disableDualSubtitles()

    // ── Auto-Sync (Phase 1: speech-timing only) ──────────────────────────
    // Runs entirely off-main-thread (audio decode + VAD are real CPU work,
    // not something to do on the composition thread). Reads the CURRENTLY
    // SELECTED audio track's language so analysis matches what's actually
    // playing, not just track 0 — a subtitle can be right for the main
    // audio and wrong for a commentary track.
    // FIX: previously only checked "a primary subtitle exists" + "not
    // SMB" — didn't verify the subtitle was actually SRT (the ONLY format
    // AutoSyncEngine's cue parser understands; a .vtt/.ass primary would
    // silently fail deep inside the engine instead of being caught here)
    // or that the video itself is a genuinely readable local/content
    // source rather than some other unplayable state.
    val primarySubtitleForAutoSync = trackUi.primaryUri
    val autoSyncAvailable = primarySubtitleForAutoSync != null &&
        supportsCustomTextPipeline(detectSubtitleFormat(primarySubtitleForAutoSync)) &&
        !isStreamMedia &&
        !currentVideo.path.startsWith("smb://", ignoreCase = true) &&
        (currentVideo.path.startsWith("content://", ignoreCase = true) || java.io.File(currentVideo.path).exists())

    // FIX: runAutoSync()/applyAutoSyncResult() used to be plain local
    // functions defined right here, inline in this composable's body —
    // now just orchestration glue calling into AutoSyncCoordinator (see
    // that file for the full reasoning on why AutoSync was the first
    // piece extracted). Every lambda below reads/writes the exact same
    // state the original inline functions did — nothing about the
    // actual behavior changed, only where the code that does it lives.
    // Reads trackUi.primaryUri fresh via the lambda each time, not the
    // primarySubtitleForAutoSync snapshot above (which is only for the
    // availability check right above it) — matching exactly what the
    // original runAutoSync() did.
    val autoSyncCoordinator = remember(exoPlayer) {
        AutoSyncCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            getPrimarySubtitleUri = { trackUi.primaryUri },
            getCurrentVideoPath = { currentVideo.path },
            getAutoSyncStatus = { autoSyncStatus },
            setAutoSyncStatus = { autoSyncStatus = it },
            setStudioVisible = { studioUi.showStudio = it },
            resetPreviewFrames = { previewFrames = emptyList(); previewBitmap = null },
            incrementPreviewReloadKey = { previewReloadKey++ },
            setSyncOffsetSeconds = { coreUi.syncOffset = it },
            setDriftScale = { driftUi.scale = it },
            incrementStudioMenuTouchKey = { studioUi.menuTouchKey++ }
        )
    }
    fun runAutoSync() = autoSyncCoordinator.runAutoSync()
    fun applyAutoSyncResult(result: SubtitleSyncResult) = autoSyncCoordinator.applyAutoSyncResult(result)

    LaunchedEffect(showNextEpisodeOverlay, pendingNextEpisode) {
        if (showNextEpisodeOverlay && pendingNextEpisode != null) {
            var count = 15
            while (count > 0) {
                nextEpisodeCountdown = count
                delay(1000)
                if (!showNextEpisodeOverlay || pendingNextEpisode == null) return@LaunchedEffect
                if (isPlaying || isVideoEnded) count--
            }
            val next = pendingNextEpisode
            if (next != null) { showNextEpisodeOverlay = false; pendingNextEpisode = null; currentMediaType = next.type; currentVideo = next.video; onPlayNext(next) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Captured as plain local vals (not referenced as the implicit
        // BoxWithConstraintsScope receiver) specifically so they can be
        // used unambiguously from inside further-nested Box {} scopes
        // later in this composable (e.g. AutoSyncFloatingIndicator's
        // wrapping Box) — Kotlin's implicit-receiver resolution can
        // become ambiguous once there's more than one Box-like receiver
        // in scope at a given point, even though maxWidth/maxHeight are
        // only actually defined on this outer one.
        val playerMaxWidth = maxWidth
        val playerMaxHeight = maxHeight
        val displayLayout = calculatePlayerDisplayLayout(maxWidth, maxHeight)
        val isLandscape = displayLayout.isLandscape
        val isSmallPhone = displayLayout.isSmallPhone
        val isCompactLandscape = displayLayout.isCompactLandscape
        val scale = displayLayout.scale
        val playButton = displayLayout.playButton
        val smallButton = displayLayout.smallButton
        val hudSize = displayLayout.hudSize
        val sidePadding = displayLayout.sidePadding
        val bottomDockPadding = displayLayout.bottomDockPadding
        val seekBottomPadding = displayLayout.seekBottomPadding
        val topClusterPaddingTop = displayLayout.topClusterPaddingTop

        // ── Per-display subtitle profiles ──────────────────────────────
        // Which profile applies right now — external (RayNeo/DP Alt Mode)
        // always wins over phone/tablet since it's a distinct viewing
        // surface, regardless of what the tablet's own screen size says.
        // TV isn't reachable yet (see DisplayProfiles.kt) so it never
        // appears here.
        val displayProfileType = RememberPlayerSubtitleDisplayProfile(
            context = context,
            externalDisplayConnected = externalDisplay.isConnected,
            isSmallPhone = isSmallPhone,
            isLandscape = isLandscape,
            appearanceUi = appearanceUi,
        )

        val popupDimensions = calculatePlayerPopupDimensions(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isLandscape = isLandscape,
            isCompactLandscape = isCompactLandscape,
            bottomDockPadding = bottomDockPadding,
            playButton = playButton
        )
        val uiScale = popupDimensions.uiScale

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val popupBottomPadding = popupDimensions.bottomPadding
        val subtitlePopupWidth = popupDimensions.subtitlePopupWidth
        val subtitlePopupHeightEstimate = popupDimensions.subtitlePopupHeightEstimate
        val trackSelectorWidth = popupDimensions.trackSelectorWidth
        val trackSelectorMaxHeight = popupDimensions.trackSelectorMaxHeight
        val srtPopupWidth = popupDimensions.srtPopupWidth
        val srtPopupMaxHeight = popupDimensions.srtPopupMaxHeight
        val audioPopupWidth = popupDimensions.audioPopupWidth
        val smallMenuWidth = popupDimensions.smallMenuWidth
        val smallMenuMaxHeight = popupDimensions.smallMenuMaxHeight
        val topIconSize = calculatePlayerTopIconSize(
            uiScale = uiScale,
            playerScale = scale
        )

        val playlistNavigation = remember(
            currentVideo.path,
            currentVideo.name,
            episodeList,
            isCurrentTvShow,
            isRestrictedFolderMedia
        ) {
            derivePlayerPlaylistNavigation(
                currentVideo = currentVideo,
                episodeList = episodeList,
                isCurrentTvShow = isCurrentTvShow,
                isRestrictedFolderMedia = isRestrictedFolderMedia
            )
        }
        val currentMeta = playlistNavigation.currentMeta

        LaunchedEffect(currentMeta?.video?.path, duration > 60_000L) {
            val meta = currentMeta ?: return@LaunchedEffect
            if (!shouldLoadSmartSegments(meta, duration)) return@LaunchedEffect
            smartSegmentResult = smartSegmentRepository.load(meta, duration)
        }

        val smartPlaybackSegments = deriveSmartPlaybackSegments(
            result = smartSegmentResult,
            position = position
        )
        val activeSmartSegment = smartPlaybackSegments.activeSegment
        val exactSceneSegment = smartPlaybackSegments.exactSceneSegment
        val creditsSegment = smartPlaybackSegments.creditsSegment

        LaunchedEffect(currentVideo.path, position, creditsSegment?.startMs, showNextEpisodeOverlay) {
            val next = findNextEpisodeForCredits(
                currentVideoPath = currentVideo.path,
                episodeList = episodeList,
                isCurrentTvShow = isCurrentTvShow,
                showNextEpisodeOverlay = showNextEpisodeOverlay,
                nextEpisodeDismissed = nextEpisodeDismissed,
                creditsStartMs = creditsSegment?.startMs,
                position = position
            ) ?: return@LaunchedEffect

            pendingNextEpisode = next
            nextEpisodeCountdown = 15
            showNextEpisodeOverlay = true
        }

        LaunchedEffect(position, creditsSegment?.startMs) {
            if (shouldResetNextEpisodeOverlay(
                    showNextEpisodeOverlay = showNextEpisodeOverlay,
                    creditsStartMs = creditsSegment?.startMs,
                    position = position
                )
            ) {
                showNextEpisodeOverlay = false
                pendingNextEpisode = null
                nextEpisodeCountdown = 0
            }
        }

        val showPrevNextButtons = playlistNavigation.showPrevNextButtons
        val currentEpisodeIndex = playlistNavigation.currentIndex
        val hasNextVideo = playlistNavigation.hasNextVideo

        PlayerVideoSurface(
            player = exoPlayer,
            externalDisplayActive = externalPlayerView != null,
            isZoomMode = isZoomMode,
            videoScale = videoScale,
            videoOffsetX = videoOffsetX,
            videoOffsetY = videoOffsetY,
            onPlayerViewChanged = { pv ->
                localPlayerView = pv
                studioUi.playerView = externalPlayerView ?: pv
            },
            onResizeModeChanged = { resizeMode ->
                externalPresentation?.updateResizeMode(resizeMode)
            }
        )

        val view = LocalView.current
        // Clears any exclusion rect this screen set once it's gone, so it
        // never lingers and affects some other screen's back gesture.
        DisposableEffect(Unit) {
            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    view.systemGestureExclusionRects = emptyList()
                }
            }
        }

        val playbackGestureModifier = if (externalPlayerView != null) {
            Modifier.rayNeoTouchpadGestures(
                    view = view,
                    // Recreate the controller when the tablet rotates so
                    // left/centre/right zones follow the current screen.
                    gestureKey = currentVideo.path to isLandscape,
                    controlsVisible = { externalPresentation?.controlsVisible?.value == true },
                    canChangeEpisode = { showPrevNextButtons },
                    onSingleTap = {
                        externalPresentation?.showTouchPulse()
                        if (externalPresentation?.controlsVisible?.value == true) {
                            if (externalPresentation?.clickPointer() != true) externalPresentation?.showControls()
                        } else {
                            externalPresentation?.showControls()
                        }
                    },
                    onDoubleTap = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        externalPresentation?.showGestureHud("Playback", if (exoPlayer.isPlaying) "PLAY" else "PAUSE")
                    },
                    onLongPress = { externalPresentation?.openQuickSubtitles() },
                    onSeekStart = {
                        isDraggingSeekbar = true
                        previewPosition = exoPlayer.currentPosition
                        previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(previewFrames, previewPosition)
                        externalPresentation?.updateSeekPreview(previewBitmap, previewPosition, true)
                    },
                    onSeekDelta = { fraction ->
                        val safeDuration = exoPlayer.duration.coerceAtLeast(1L)
                        previewPosition = calculatePlayerSeekPreviewPosition(previewPosition, fraction, safeDuration)
                        previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(previewFrames, previewPosition)
                        externalPresentation?.updateSeekPreview(previewBitmap, previewPosition, true)
                    },
                    onSeekEnd = {
                        exoPlayer.seekTo(previewPosition)
                        position = previewPosition
                        isDraggingSeekbar = false
                        externalPresentation?.updateSeekPreview(previewBitmap, previewPosition, false)
                    },
                    onBrightnessDrag = { deltaY ->
                        brightnessPercent = adjustPlayerBrightnessPercent(brightnessPercent, deltaY)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightnessPercent / 100f }
                        showBrightnessCircle = true
                        externalPresentation?.showGestureHud("Tablet brightness", "$brightnessPercent%", brightnessPercent)
                    },
                    onVolumeDrag = { deltaY ->
                        volumePercent = adjustPlayerVolumePercent(volumePercent, deltaY, maxPercent = 100)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, playerSystemVolumeIndex(volumePercent, maxVol), 0)
                        showVolumeCircle = true
                        externalPresentation?.showGestureHud("Volume", "$volumePercent%", volumePercent)
                    },
                    onPrevious = { externalPresentation?.showGestureHud("Episode", "PREVIOUS"); playPrevious() },
                    onNext = { externalPresentation?.showGestureHud("Episode", "NEXT"); playNext() },
                    onPointerMove = { externalPresentation?.movePointer(it.x, it.y) },
                    onPointerClick = {
                        externalPresentation?.showTouchPulse()
                        externalPresentation?.clickPointer() ?: false
                    },
                    onPinchZoomPan = { zoom, pan ->
                        externalPresentation?.applyViewportTransform(zoom, pan.x, pan.y)
                        val shownZoom = (videoScale * zoom).coerceIn(1f, 3f)
                        videoScale = shownZoom
                        externalPresentation?.showGestureHud("Screen size", "${(shownZoom * 100).toInt()}%", (((shownZoom - 1f) / 2f) * 100).toInt())
                    },
                    onEmergencyReturnToTablet = {
                        externalPresentation?.showGestureHud("Emergency return", "TABLET")
                        externalPresentation?.enterTabletStandby()
                        glassesSessionDisabled = true
                        android.widget.Toast.makeText(
                            context,
                            "Glasses Mode ended — playback returned to tablet",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    onGestureEnd = { brightnessGestureKey++; volumeGestureKey++ }
                )
        } else {
            Modifier.videoPlaybackGestures(
                    view = view,
                    videoPathKey = currentVideo.path,
                    episodeListKey = episodeList,
                    edgeSwipeNextEnabled = { showPrevNextButtons },
                    onTap = {
                        when {
                            showAudioSelector -> showAudioSelector = false
                            coreUi.showSettings -> coreUi.showSettings = false
                            trackUi.showSelector -> trackUi.showSelector = false
                            searchUi.showSearch -> searchUi.showSearch = false
                            driftUi.showDialog -> driftUi.showDialog = false
                            coreUi.showAppearanceStudio -> coreUi.showAppearanceStudio = false
                            studioUi.showStudio -> studioUi.showStudio = false
                            coreUi.dialogueSyncArmed -> {}
                            showSpeedMenu -> showSpeedMenu = false
                            showSleepMenu -> showSleepMenu = false
                            showSrtBrowser -> showSrtBrowser = false
                            else -> {
                                if (externalPlayerView != null) {
                                    externalPresentation?.showControls()
                                    showControls = false
                                    showTopBar = false
                                } else {
                                    val v = !showControls; showControls = v; showTopBar = v
                                }
                            }
                        }
                    },
                    onSeekBack = {
                        exoPlayer.seekTo(playerSeekBackPosition(exoPlayer.currentPosition))
                        position = exoPlayer.currentPosition
                        showControls = true; showTopBar = true
                    },
                    onSeekForward = {
                        exoPlayer.seekTo(playerSeekForwardPosition(exoPlayer.currentPosition, exoPlayer.duration))
                        position = exoPlayer.currentPosition
                        showControls = true; showTopBar = true
                    },
                    onToggleZoomMode = {
                        isZoomMode = !isZoomMode; showControls = true; showTopBar = true
                    },
                    onDragSettled = { brightnessGestureKey++; volumeGestureKey++ },
                    onEdgeSwipeNext = { playNext() },
                    onBrightnessDrag = { deltaY ->
                        brightnessPercent = adjustPlayerBrightnessPercent(brightnessPercent, deltaY)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightnessPercent / 100f }
                        showBrightnessCircle = true
                    },
                    onVolumeDrag = { deltaY ->
                        volumePercent = adjustPlayerVolumePercent(volumePercent, deltaY, maxPercent = 150)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, playerSystemVolumeIndex(volumePercent, maxVol), 0)
                        showVolumeCircle = true
                    },
                    onPinchZoomPan = { zoom, pan ->
                        val transform = calculatePlayerZoomPanTransform(
                            currentScale = videoScale,
                            currentOffsetX = videoOffsetX,
                            currentOffsetY = videoOffsetY,
                            zoomFactor = zoom,
                            panX = pan.x,
                            panY = pan.y,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx
                        )
                        videoScale = transform.scale
                        videoOffsetX = transform.offsetX
                        videoOffsetY = transform.offsetY
                    },
                )
        }

        Box(modifier = Modifier.fillMaxSize().then(playbackGestureModifier))

        PlayerSubtitleGestureOverlay(
            player = exoPlayer,
            haptics = haptics,
            isStreamMedia = isStreamMedia,
            bottomDockPadding = bottomDockPadding,
            playButtonSize = playButton,
            coreUi = coreUi,
            appearanceUi = appearanceUi,
            studioUi = studioUi,
            onShowControls = { showControls = true },
        )

        PlayerPlaybackStatusOverlays(
            isLandscape = isLandscape,
            hudSize = hudSize,
            showBrightnessCircle = showBrightnessCircle,
            brightnessPercent = brightnessPercent,
            showVolumeCircle = showVolumeCircle,
            volumePercent = volumePercent,
            edgeSwipeHint = edgeSwipeHint,
            showGlassesConnectedHint = showGlassesConnectedHint,
            showBufferingSpinner = showBufferingSpinner,
            stuckBufferingHint = stuckBufferingHint,
            playerErrorMessage = playerErrorMessage,
            sleepTimerActive = sleepTimerActive,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onBack = onBack,
            onRetry = {
                errorRetryCount = 0
                playCurrentVideoWithSubtitle(
                    subtitleUri = trackUi.originalUri,
                    resumePosition = position,
                    isOriginalSubtitle = false,
                )
            },
        )

        PlayerSpeedAndSleepMenus(
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            playbackSpeed = playbackSpeed,
            sleepTimerMinutes = sleepTimerMinutes,
            topClusterPaddingTop = topClusterPaddingTop,
            clusterHeightPx = clusterHeightPx,
            isLandscape = isLandscape,
            sidePadding = sidePadding,
            smallMenuWidth = smallMenuWidth,
            smallMenuMaxHeight = smallMenuMaxHeight,
            onSpeedSelected = { setPlaybackSpeed(it) },
            onDismissSpeedMenu = { showSpeedMenu = false },
            onSleepSelected = { setSleepTimer(it) },
            onDismissSleepMenu = { showSleepMenu = false },
        )

        val srtFiles = rememberAvailableLocalSubtitleFiles(
            videoPath = currentVideo.path,
            selectorVisible = showSrtBrowser,
            pendingDeletePaths = pendingDeletePaths
        )
        val audioTracksForPopup = buildAudioTrackRows(
            player = exoPlayer,
            trackSelector = trackSelector,
            onTrackSelected = {
                showAudioSelector = false
                showControls = true
            }
        )
        SrtAndAudioTrackPopups(
            showSrtBrowser = showSrtBrowser,
            srtFiles = srtFiles,
            srtPopupWidth = srtPopupWidth,
            srtPopupMaxHeight = srtPopupMaxHeight,
            srtBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            srtOffsetX = calculatePlayerPopupOffsetX(subIconX, srtPopupWidth, screenWidthPx, density),
            onPickSrt = { file -> showSrtBrowser = false; pendingSrtUri = Uri.fromFile(file) },
            onDeleteSrt = { file -> requestDeleteSubtitle(file) },
            onSystemPicker = { showSrtBrowser = false; srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
            onCloseSrtBrowser = { showSrtBrowser = false; showControls = true },
            showAudioSelector = showAudioSelector,
            audioTracks = audioTracksForPopup,
            audioPopupWidth = audioPopupWidth,
            audioBottomPadding = popupBottomPadding,
            audioOffsetX = calculatePlayerPopupOffsetX(audioIconX, audioPopupWidth, screenWidthPx, density),
            audioSyncMs = audioSyncMs,
            onAudioSyncChange = { audioSyncMs = it; menuTouchKey++ },
            onAudioMenuInteraction = { menuTouchKey++ },
            onCloseAudioSelector = { showAudioSelector = false; showControls = true },
        )

        val hasInternalSubtitles = hasInternalSubtitleTracks(exoPlayer.currentTracks)

        // ── Track Selector data, built fresh from live player + disk state
        // every time it's shown. Embedded tracks read straight off
        // ExoPlayer's current track groups (source of truth for what's
        // actually IN the file); downloaded/local read off disk the same
        // way the existing SRT browser and OpenSubtitlesClient cache
        // already do — no new scanning logic, just reused in one place.
        val embeddedTrackChoices = remember(exoPlayer.currentTracks) {
            buildEmbeddedSubtitleChoices(exoPlayer.currentTracks)
        }
        val downloadedTrackChoice = rememberDownloadedSubtitleChoice(
            context = context,
            videoPath = currentVideo.path,
            preferredLanguages = coreUi.behaviorPrefs.preferredLanguages,
            selectorVisible = trackUi.showSelector,
            canDownloadExternalSubtitles = canDownloadExternalSubtitles
        )
        val localFileChoices = rememberAvailableLocalSubtitleFiles(
            videoPath = currentVideo.path,
            selectorVisible = trackUi.showSelector,
            pendingDeletePaths = pendingDeletePaths
        )

        val subtitleQuickMenuStatusText = buildSubtitleQuickMenuStatusText(
            subtitlesEnabled = coreUi.subtitlesEnabled,
            selectedLabel = trackUi.selectedLabel,
            selectedSource = trackUi.selectedSource,
            hasInternalSubtitles = hasInternalSubtitles
        )
        SubtitleQuickMenuAndTrackSelector(
            showSubtitleSettings = coreUi.showSettings,
            showTrackSelector = trackUi.showSelector,
            subtitlesEnabled = coreUi.subtitlesEnabled,
            activeTrackStatusText = subtitleQuickMenuStatusText,
            quickMenuBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            quickMenuOffsetX = calculatePlayerPopupOffsetX(subIconX, subtitlePopupWidth, screenWidthPx, density),
            subtitleTextSizeSp = appearanceUi.textSizeSp,
            subtitleBottomPadding = appearanceUi.bottomPadding,
            onFindClick = {
                studioUi.menuTouchKey++
                coreUi.showSettings = false
                searchUi.showSearch = true
                showControls = true
                if (searchUi.searchResults.isEmpty() && !searchUi.searchLoading) {
                    performSubtitleSearch(playerSubtitleSearchQuery(currentVideo.path), "", "")
                }
            },
            onTracksClick = { coreUi.showSettings = false; trackUi.showSelector = true; showControls = true; studioUi.menuTouchKey++ },
            onToggleSubtitles = {
                coreUi.subtitlesEnabled = !coreUi.subtitlesEnabled
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !coreUi.subtitlesEnabled).build()
                if (!coreUi.subtitlesEnabled) { trackUi.selectedKey = "off"; trackUi.selectedLabel = ""; trackUi.selectedSource = "" }
                showControls = true; studioUi.menuTouchKey++
            },
            onDismissSettings = { coreUi.showSettings = false; showControls = true },
            onFontSizeChange = { appearanceUi.textSizeSp = it; showControls = true; studioUi.menuTouchKey++ },
            onVerticalPositionChange = { appearanceUi.bottomPadding = it; showControls = true; studioUi.menuTouchKey++ },
            onSyncClick = { coreUi.showSettings = false; studioUi.initialTab = SubtitleStudioTab.TIMING; studioUi.showStudio = true; showControls = true },
            onStyleClick = { coreUi.showSettings = false; coreUi.showAppearanceStudio = true; showControls = true },
            onResetSubtitleSettings = {
                clearSubtitleProfileSettings(context, displayProfileType, isLandscape)
                val defaults = defaultSubtitleProfileSettings(displayProfileType, isLandscape)
                appearanceUi.textSizeSp = defaults.fontSizeSp
                appearanceUi.bottomPadding = defaults.bottomPadding
                appearanceUi.preset = defaults.presetName
                appearanceUi.appearance = SubtitleAppearance(defaults.foregroundColor, defaults.edgeType, defaults.edgeColor, defaults.backgroundColor)
                appearanceUi.preserveOriginalStyling = false
                coreUi.syncOffset = 0f
                trackUi.appliedOffsetMs = 0L
                driftUi.scale = 1f; driftUi.appliedScale = 1f
                driftUi.pointA = null; driftUi.pointB = null
                AudioSyncHolder.offsetUs = 0L; audioSyncMs = 0
                Toast.makeText(context, "Subtitle settings reset for ${displayProfileType.label}", Toast.LENGTH_SHORT).show()
                showControls = true; studioUi.menuTouchKey++
            },
            onSettingsUserInteraction = { studioUi.menuTouchKey++; showControls = true },
            trackSelectorBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            trackSelectorOffsetX = calculatePlayerPopupOffsetX(subIconX, trackSelectorWidth, screenWidthPx, density),
            trackSelectorWidth = trackSelectorWidth,
            trackSelectorMaxHeight = trackSelectorMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            embeddedTrackChoices = embeddedTrackChoices,
            downloadedTrackChoice = downloadedTrackChoice,
            localFileChoices = localFileChoices,
            selectedTrackKey = trackUi.selectedKey,
            onSelectTrack = { choice -> selectSubtitleTrack(choice); trackUi.showSelector = false; showControls = true },
            onDeleteLocalTrack = { file -> requestDeleteSubtitle(file) },
            onOpenFilePickerFromTrackSelector = { trackUi.showSelector = false; srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
            onDismissTrackSelector = { trackUi.showSelector = false; showControls = true },
            onTrackSelectorUserInteraction = { studioUi.menuTouchKey++ },
        )

        val subtitleSearchLayout = calculateSubtitleSearchLayout(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isLandscape = isLandscape,
            isCompactLandscape = isCompactLandscape
        )
        val searchWidth = subtitleSearchLayout.width
        val searchMaxHeight = subtitleSearchLayout.maxHeight
        val subtitleWebQuery = playerSubtitleSearchQuery(currentVideo.path)
        SubtitleAcquisitionFlow(
            showSubtitleSearch = searchUi.showSearch,
            searchWidth = searchWidth,
            searchMaxHeight = searchMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            initialSearchQuery = remember(currentVideo.path) { playerSubtitleSearchQuery(currentVideo.path) },
            searchResults = searchUi.searchResults,
            isSearching = searchUi.searchLoading,
            searchStatusText = searchUi.searchStatus,
            onSearchUserInteraction = { studioUi.menuTouchKey++ },
            onSearch = { q, s, e -> studioUi.menuTouchKey++; performSubtitleSearch(q, s, e) },
            onDownloadAndApply = { result -> studioUi.menuTouchKey++; applySearchResult(result, alsoPlay = true) },
            onDownloadOnly = { result -> studioUi.menuTouchKey++; applySearchResult(result, alsoPlay = false) },
            onWebsiteFallbackFromSearch = { searchUi.showSearch = false; searchUi.showFallback = true; showControls = true },
            onDismissSearch = { searchUi.showSearch = false; showControls = true },
            showSubtitleFallback = searchUi.showFallback,
            fallbackSearchQuery = subtitleWebQuery,
            fallbackStatusText = searchUi.searchStatus,
            onSecureBrowser = {
                exoPlayer.pause()
                launchSubtitleCustomTab(context, subtitleWebQuery)
                searchUi.showFallback = false
                Toast.makeText(context, "After downloading, return and choose Import downloaded subtitle", Toast.LENGTH_LONG).show()
            },
            onEmbeddedBrowser = {
                exoPlayer.pause()
                searchUi.showFallback = false
                searchUi.showEmbeddedBrowser = true
            },
            onImportFile = {
                exoPlayer.pause()
                srtPickerLauncher.launch(
                    arrayOf(
                        "application/x-subrip",
                        "text/vtt",
                        "text/plain",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                    )
                )
            },
            onDismissFallback = { searchUi.showFallback = false },
            showEmbeddedSubtitleBrowser = searchUi.showEmbeddedBrowser,
            embeddedBrowserQuery = playerSubtitleSearchQuery(currentVideo.path),
            embeddedBrowserPreferredLanguage = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en",
            onImported = { result ->
                if (result.alternatives.isEmpty()) {
                    applyImportedWebsiteSubtitle(result.selected)
                } else {
                    searchUi.pendingImportCandidates = result
                    searchUi.showEmbeddedBrowser = false
                }
            },
            onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
            onDismissEmbeddedBrowser = { searchUi.showEmbeddedBrowser = false; showControls = true },
            pendingImportedCandidates = searchUi.pendingImportCandidates,
            onCandidateSelected = { applyImportedWebsiteSubtitle(it) },
            onDismissCandidateSheet = { searchUi.pendingImportCandidates = null },
        )

        SubtitleSyncAndAppearancePopups(
            dialogueSyncArmed = coreUi.dialogueSyncArmed,
            isLandscape = isLandscape,
            onDialogueSyncTap = { confirmDialogueSyncTap() },
            onDialogueSyncCancel = { cancelDialogueSync() },
            showDriftDialog = driftUi.showDialog,
            driftPopupWidth = trackSelectorWidth.coerceAtLeast(220.dp),
            videoDurationMs = duration,
            currentPositionMs = position,
            driftPointA = driftUi.pointA,
            driftPointB = driftUi.pointB,
            onMarkPointA = { correction -> markDriftPointA(correction) },
            onMarkPointB = { correction -> markDriftPointB(correction) },
            onApplyDrift = { applyDriftFix() },
            onDismissDrift = { driftUi.showDialog = false; showControls = true },
            showAppearanceStudio = coreUi.showAppearanceStudio,
            appearanceBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            appearanceOffsetX = calculatePlayerPopupOffsetX(subIconX, trackSelectorWidth, screenWidthPx, density),
            appearancePopupWidth = trackSelectorWidth,
            appearancePopupMaxHeight = trackSelectorMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            appearancePresetName = appearanceUi.preset,
            appearance = appearanceUi.appearance,
            appearanceFontSizeSp = appearanceUi.textSizeSp,
            onApplyPreset = { name, preset -> appearanceUi.preset = name; appearanceUi.appearance = preset },
            onForegroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(foregroundColor = c) },
            onEdgeTypeChange = { t -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeType = t) },
            onEdgeColorChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeColor = c) },
            onBackgroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(backgroundColor = c) },
            isAssOrSsaFormat = isAssOrSsaFormat,
            preserveOriginalStyling = appearanceUi.preserveOriginalStyling,
            onPreserveOriginalStylingChange = { appearanceUi.preserveOriginalStyling = it },
            onDismissAppearanceStudio = { coreUi.showAppearanceStudio = false; showControls = true },
            onAppearanceUserInteraction = { studioUi.menuTouchKey++ },
        )

        // FIX: sizing only ever reacted to ORIENTATION (isLandscape/
        // isCompactLandscape), never to actual physical screen size — so a
        // phone in landscape got the exact same width/height caps as a
        // tablet in landscape, even though the phone has far less real
        // estate. isTabletSized uses the SMALLER of the two dimensions
        // (Android's own sw600dp convention for "this is a 7"+ tablet"),
        // which stays correct regardless of which way the device is held
        // — unlike maxWidth alone, which would misclassify a phone turned
        // sideways as tablet-sized. Phones now get meaningfully smaller
        // caps in every orientation; tablets get meaningfully bigger ones.
        // Safe to shrink on phones specifically because Studio already
        // scrolls internally (every tab's content is in a
        // verticalScroll'd Column) and is already independently
        // draggable (its own long-press-drag handle, not this sizing) —
        // nothing gets cut off, it just needs to scroll a bit more.
        val subtitleStudioLayout = calculateSubtitleStudioLayout(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isLandscape = isLandscape,
            isCompactLandscape = isCompactLandscape
        )
        val studioWidth = subtitleStudioLayout.width
        val studioMaxHeight = subtitleStudioLayout.maxHeight
        SubtitleStudioOverlay(
            showSubtitleStudio = studioUi.showStudio,
            studioWidth = studioWidth,
            studioMaxHeight = studioMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            initialTab = studioUi.initialTab,
            videoPath = currentVideo.path,
            onOpenSearch = {
                studioUi.showStudio = false
                searchUi.showSearch = true
                showControls = true
                if (searchUi.searchResults.isEmpty() && !searchUi.searchLoading) {
                    performSubtitleSearch(playerSubtitleSearchQuery(currentVideo.path), "", "")
                }
            },
            onOpenManualSearch = {
                studioUi.showStudio = false
                searchUi.showFallback = true
                showControls = true
            },
            embeddedTracks = embeddedTrackChoices,
            downloadedTrack = downloadedTrackChoice,
            localFiles = localFileChoices,
            selectedTrackKey = trackUi.selectedKey,
            onSelectTrack = { choice -> selectSubtitleTrack(choice) },
            onDeleteLocalTrack = { file -> requestDeleteSubtitle(file) },
            onOpenFilePicker = { srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
            currentSyncOffset = coreUi.syncOffset,
            onSyncOffsetChange = { coreUi.syncOffset = it; studioUi.menuTouchKey++ },
            onDialogueSyncClick = { armDialogueSync() },
            onDriftFixClick = { studioUi.showStudio = false; driftUi.showDialog = true },
            autoSyncStatus = autoSyncStatus,
            autoSyncAvailable = autoSyncAvailable,
            onAutoSyncClick = { runAutoSync() },
            onApplyAutoSync = { result -> applyAutoSyncResult(result) },
            onCancelAutoSync = { autoSyncStatus = AutoSyncStatus.Idle },
            presetName = appearanceUi.preset,
            appearance = appearanceUi.appearance,
            fontSizeSp = appearanceUi.textSizeSp,
            onFontSizeChange = { appearanceUi.textSizeSp = it },
            onApplyPreset = { name, preset -> appearanceUi.preset = name; appearanceUi.appearance = preset },
            onForegroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(foregroundColor = c) },
            onEdgeTypeChange = { t -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeType = t) },
            onEdgeColorChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeColor = c) },
            onBackgroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(backgroundColor = c) },
            isAssOrSsaFormat = isAssOrSsaFormat,
            preserveOriginalStyling = appearanceUi.preserveOriginalStyling,
            onPreserveOriginalStylingChange = { appearanceUi.preserveOriginalStyling = it },
            bottomPadding = appearanceUi.bottomPadding,
            onBottomPaddingChange = { appearanceUi.bottomPadding = it },
            behaviorPrefs = coreUi.behaviorPrefs,
            onBehaviorPrefsChange = { coreUi.behaviorPrefs = it; saveSubtitleBehaviorPrefs(context, it) },
            cleaningOptions = coreUi.cleaningOptions,
            onCleaningOptionsChange = { coreUi.cleaningOptions = it; saveSubtitleCleaningOptions(context, it) },
            dualSubtitlesEnabled = dualUi.enabled,
            dualCanEnable = trackUi.primaryUri != null,
            dualSecondaryLanguage = dualUi.secondaryLanguage,
            dualGapLines = dualUi.gapLines,
            dualStatusText = dualUi.statusText,
            onToggleDual = { enabled ->
                dualUi.enabled = enabled
                if (enabled) fetchAndApplyDualSecondary() else disableDualSubtitles()
            },
            onDualSecondaryLanguageChange = { lang ->
                dualUi.secondaryLanguage = lang
                coreUi.behaviorPrefs = coreUi.behaviorPrefs.copy(dualSecondaryLanguage = lang)
                saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                if (dualUi.enabled) fetchAndApplyDualSecondary()
            },
            onDualGapLinesChange = { gap ->
                dualUi.gapLines = gap
                if (dualUi.enabled) fetchAndApplyDualSecondary()
            },
            onDismiss = { studioUi.showStudio = false; showControls = true },
            onUserInteraction = { studioUi.menuTouchKey++ },
        )

        // Studio and Search are large, self-contained sheets that cover
        // most of the screen — showing the transport dock/seek bar/top
        // cluster underneath them just doubles up the UI for no reason
        // (confirmed on-device: Vivo X300 Pro screenshots showed both
        // layers competing for the same space). Both are explicitly
        // EXCLUDED from the trigger list and explicitly HIDE this whole
        // block via the trailing && clause, unlike the smaller anchored
        // popups (Track Selector, Drift, Appearance, quick menu) which
        // were designed to sit alongside visible controls and still do.
        val mainControlsVisible = shouldShowMainPlayerControls(
            externalDisplayActive = externalPlayerView != null,
            showControls = showControls,
            isDraggingSeekbar = isDraggingSeekbar,
            showAudioSelector = showAudioSelector,
            showSubtitleSettings = coreUi.showSettings,
            showTrackSelector = trackUi.showSelector,
            showDriftDialog = driftUi.showDialog,
            showAppearanceStudio = coreUi.showAppearanceStudio,
            dialogueSyncArmed = coreUi.dialogueSyncArmed,
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            showSubtitleStudio = studioUi.showStudio,
            showSubtitleSearch = searchUi.showSearch,
            isInPipMode = CineVaultPlayerHolder.isInPipMode
        )
        PlayerControlsVisibilityShell(
            visible = mainControlsVisible
        ) {

                PlayerTopControlCluster(
                    isLandscape = isLandscape,
                    topRowVisible = !showSeekPreview,
                    topClusterPaddingTop = topClusterPaddingTop,
                    sidePadding = sidePadding,
                    topIconSize = topIconSize,
                    currentMeta = currentMeta,
                    title = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path),
                    playbackSpeed = playbackSpeed,
                    sleepTimerActive = sleepTimerActive,
                    showSpeedMenu = showSpeedMenu,
                    showSleepMenu = showSleepMenu,
                    onSpeedClick = {
                        val wasOpen = showSpeedMenu
                        closeAllMenus()
                        showSpeedMenu = !wasOpen
                        showControls = true
                    },
                    onSleepClick = {
                        val wasOpen = showSleepMenu
                        closeAllMenus()
                        showSleepMenu = !wasOpen
                        showControls = true
                    },
                    onPipClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val actions = buildPipActions(context, exoPlayer.isPlaying)
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .setActions(actions)
                                    .build()
                            )
                        }
                    },
                    onClusterHeightMeasured = { clusterHeightPx = it }
                )

                PlayerTransientStatusPills(
                    autoSubtitleStatus = autoSubtitleFetch.status,
                    showSeekPreview = showSeekPreview,
                    isLandscape = isLandscape,
                    isZoomMode = isZoomMode
                )

                val anyMenuOpenForSmartSkip = showAudioSelector || coreUi.showSettings || trackUi.showSelector || searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio || studioUi.showStudio || coreUi.dialogueSyncArmed || showSpeedMenu || showSleepMenu || showSrtBrowser
                val suppressCreditsPillForScene = activeSmartSegment?.type == SegmentType.CREDITS &&
                    (smartSegmentResult.hasMidCreditsScene || smartSegmentResult.hasPostCreditsScene)
                val creditNoticeVisible = !isCurrentTvShow && creditsSegment != null && position >= creditsSegment.startMs &&
                    (smartSegmentResult.hasMidCreditsScene || smartSegmentResult.hasPostCreditsScene) &&
                    (exactSceneSegment == null || position < exactSceneSegment.startMs)

                PlayerSmartPlaybackOverlays(
                    sidePadding = sidePadding,
                    showSeekPreview = showSeekPreview,
                    isDraggingSeekbar = isDraggingSeekbar,
                    showNextEpisodeOverlay = showNextEpisodeOverlay,
                    pendingNextEpisode = pendingNextEpisode,
                    nextEpisodeCountdown = nextEpisodeCountdown,
                    activeSmartSegment = activeSmartSegment,
                    suppressCreditsPillForScene = suppressCreditsPillForScene,
                    anyMenuOpenForSmartSkip = anyMenuOpenForSmartSkip,
                    creditNoticeVisible = creditNoticeVisible,
                    exactSceneSegment = exactSceneSegment,
                    hasMidCreditsScene = smartSegmentResult.hasMidCreditsScene,
                    hasPostCreditsScene = smartSegmentResult.hasPostCreditsScene,
                    position = position,
                    isLandscape = isLandscape,
                    onPlayNextEpisode = { n ->
                        showNextEpisodeOverlay = false
                        pendingNextEpisode = null
                        currentMediaType = n.type
                        currentVideo = n.video
                        onPlayNext(n)
                    },
                    onCancelNextEpisode = {
                        showNextEpisodeOverlay = false
                        pendingNextEpisode = null
                        nextEpisodeCountdown = 0
                        nextEpisodeDismissed = true
                        showControls = true
                    },
                    onSkipSegment = { segment ->
                        exoPlayer.seekTo(segment.endMs)
                        position = segment.endMs
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showControls = true
                    },
                    onJumpToCreditScene = { scene ->
                        exoPlayer.seekTo(scene.startMs)
                        position = scene.startMs
                    }
                )

                PlayerBottomTransportDock(
                    visible = !showSeekPreview && !isDraggingSeekbar,
                    bottomDockPadding = bottomDockPadding,
                    sidePadding = sidePadding,
                    scale = scale,
                    smallButton = smallButton,
                    playButton = playButton,
                    isPlaying = isPlaying,
                    isVideoEnded = isVideoEnded,
                    showPrevNextButtons = showPrevNextButtons,
                    hasNextVideo = hasNextVideo,
                    autoPlayEnabled = autoPlayEnabled,
                    showAudioSelector = showAudioSelector,
                    showSubtitleActive = coreUi.showSettings || trackUi.showSelector || searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio || studioUi.showStudio,
                    isStreamMedia = isStreamMedia,
                    onBack = onBack,
                    onReplay10 = {
                        exoPlayer.seekTo(playerSeekBackPosition(exoPlayer.currentPosition))
                        position = exoPlayer.currentPosition
                        showControls = true
                    },
                    onPlayPause = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isVideoEnded) {
                            exoPlayer.seekTo(0)
                            exoPlayer.play()
                            isVideoEnded = false
                            showControls = true
                        } else {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        }
                    },
                    onForward10 = {
                        exoPlayer.seekTo(playerSeekForwardPosition(exoPlayer.currentPosition, exoPlayer.duration))
                        position = exoPlayer.currentPosition
                        showControls = true
                    },
                    onNext = {
                        if (hasNextVideo) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playNext()
                        }
                    },
                    onToggleAutoplay = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        autoPlayEnabled = !autoPlayEnabled
                        showControls = true
                        Toast.makeText(context, if (autoPlayEnabled) "Autoplay on" else "Autoplay off", Toast.LENGTH_SHORT).show()
                    },
                    onAudioClick = {
                        val wasOpen = showAudioSelector
                        closeAllMenus()
                        showAudioSelector = !wasOpen
                        showControls = true
                        menuTouchKey++
                    },
                    onAudioCenterMeasured = { audioIconX = it },
                    onSubtitleClick = {
                        val wasOpen = coreUi.showSettings || trackUi.showSelector || searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio || studioUi.showStudio
                        closeAllMenus()
                        coreUi.showSettings = !wasOpen
                        showControls = true
                        menuTouchKey++
                    },
                    onSubtitleLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        closeAllMenus()
                        studioUi.initialTab = null
                        studioUi.showStudio = true
                        showControls = true
                    },
                    onSubtitleCenterMeasured = { subIconX = it }
                )

                PlayerSeekDock(
                    showSeekPreview = showSeekPreview,
                    previewBitmap = previewBitmap,
                    previewPosition = previewPosition,
                    duration = duration,
                    isLandscape = isLandscape,
                    isSeekPreviewLarge = isSeekPreviewLarge,
                    seekBottomPadding = seekBottomPadding,
                    sidePadding = sidePadding,
                    scale = scale,
                    position = position,
                    isDraggingSeekbar = isDraggingSeekbar,
                    seed = currentVideo.path.hashCode(),
                    onPreviewPositionChanged = { pos ->
                        isDraggingSeekbar = true
                        showSeekPreview = true
                        showControls = true
                        showTopBar = true
                        position = pos.coerceIn(0L, duration)
                        previewPosition = position
                        VideoThumbnailHelper.nearestPreviewFrame(previewFrames, previewPosition)?.let {
                            previewBitmap = it
                        }
                    },
                    onSeekFinished = { finalPos ->
                        val safe = finalPos.coerceIn(0L, duration)
                        position = safe
                        previewPosition = safe
                        exoPlayer.seekTo(safe)
                        isDraggingSeekbar = false
                        previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(previewFrames, safe) ?: previewBitmap
                        showSeekPreview = true
                        if (isStreamMedia) {
                            scope.launch {
                                delay(360)
                                if (!isDraggingSeekbar) showSeekPreview = false
                            }
                        } else {
                            scope.launch {
                                val bmp = VideoThumbnailHelper.generateFrameAtTime(context, currentVideo.path, safe)
                                if (bmp != null && previewPosition == safe) previewBitmap = bmp
                                delay(620)
                                if (previewPosition == safe && !isDraggingSeekbar) showSeekPreview = false
                            }
                        }
                        showControls = true
                        showTopBar = true
                    }
                )

        }

        PlayerControlsLockLayer(
            controlsLocked = controlsLocked,
            lockButtonVisible = externalPlayerView == null &&
                (if (controlsLocked) lockButtonVisibleWhileLocked else showControls) &&
                !CineVaultPlayerHolder.isInPipMode,
            isLandscape = isLandscape,
            onLockedSurfaceTap = { lockButtonVisibleWhileLocked = true },
            onToggleLock = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                controlsLocked = !controlsLocked
                lockButtonVisibleWhileLocked = true
            }
        )

        PlayerAutoSyncFloatingOverlay(
            visible = !studioUi.showStudio && !CineVaultPlayerHolder.isInPipMode,
            containerWidth = playerMaxWidth,
            containerHeight = playerMaxHeight,
            status = autoSyncStatus,
            onApply = { result -> applyAutoSyncResult(result) },
            onCancel = { autoSyncStatus = AutoSyncStatus.Idle },
            onRetry = { runAutoSync() }
        )

        PlayerSubtitleDeleteFeedback(
            pendingFile = pendingDeleteConfirmFile,
            snackbarHostState = snackbarHostState,
            snackbarBottomPadding = bottomDockPadding + playButton + 26.dp,
            onDismissDelete = { pendingDeleteConfirmFile = null },
            onConfirmDelete = { file ->
                pendingDeleteConfirmFile = null
                deleteWithUndo(file)
            }
        )

    }
}
