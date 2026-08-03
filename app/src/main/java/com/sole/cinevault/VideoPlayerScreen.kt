package com.sole.cinevault

import com.sole.cinevault.library.*

// All subtitle-system files (search, import, sync, appearance, dual-merge,
// providers) moved to their own package on this pass. Single wildcard
// import used deliberately instead of ~45 explicit ones, since the
// cross-reference check confirmed this file is the ONLY outside caller
// into that package.
import com.sole.cinevault.subtitles.*

import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.unit.IntOffset
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
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Duration cache — saves real video duration so progress % is accurate
private fun saveDuration(context: Context, videoPath: String, durationMs: Long) {
    if (durationMs <= 0L) return
    context.getSharedPreferences("cinevault_durations", Context.MODE_PRIVATE)
        .edit().putLong(videoPath, durationMs).apply()
}

fun loadDuration(context: Context, videoPath: String): Long {
    return context.getSharedPreferences("cinevault_durations", Context.MODE_PRIVATE)
        .getLong(videoPath, 0L)
}

@OptIn(UnstableApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
// Reusable floating-window wrapper for popups whose own composable body
// can't be modified directly (SubtitleTrackSelectorSheet and
// SubtitleAppearanceStudioSheet are both ALSO rendered as tabs embedded
// inside Studio itself — adding drag logic straight into them would
// double up with Studio's own dragging when used that way). This wraps
// the popup at its STANDALONE call site only, layering a user-draggable
// offset on top of whatever initial position the call site already
// establishes (anchored-near-icon for Track Selector, centered for
// Appearance Studio) — same proven long-press-then-drag + bounds-
// clamping + activity-ping pattern already used for Studio and Search.
@Composable
private fun DraggableFloatingPopup(
    containerWidth: Dp,
    containerHeight: Dp,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onUserInteraction: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxOffsetXPx = with(density) { ((containerWidth - popupWidth) / 2).coerceAtLeast(0.dp).toPx() }
    val maxOffsetYPx = with(density) { ((containerHeight - popupMaxHeight) / 2).coerceAtLeast(0.dp).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            // Root containment — same A1 fix, applied here too: nothing
            // inside this popup should ever be able to leak a touch
            // through to the video surface behind it.
            .pointerInput(Unit) { detectTapGestures { } }
            // Any touch anywhere in the popup resets its auto-close timer.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserInteraction()
                }
            }
            // Long-press ANYWHERE in the popup (not just a header, since
            // this wrapper has no header of its own to isolate) starts a
            // drag. Quick taps/scrolls inside the shared composable's own
            // content resolve well before the long-press timeout elapses,
            // so this doesn't compete with normal clicking or scrolling.
            .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                ) { change, dragAmount ->
                    change.consume()
                    dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxOffsetXPx, maxOffsetXPx)
                    dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-maxOffsetYPx, maxOffsetYPx)
                }
            }
    ) {
        content()
    }
}

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
    // a remote/control surface — the tablet only ever LOOKS off. True full
    // blackout with the glasses continuing to show a distinct feed needs
    // Phase 2 (Presentation-based separate rendering), not built yet.
    // Reverts automatically on disconnect or when leaving the player.
    val externalDisplay by rememberExternalDisplayState()
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
            try { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } catch (_: Exception) {}
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
    var showTopBar by remember { mutableStateOf(true) }
    var isDraggingSeekbar by remember { mutableStateOf(false) }

    var volumePercent by remember { mutableIntStateOf(70) }
    var brightnessPercent by remember { mutableIntStateOf(90) }
    var showVolumeCircle by remember { mutableStateOf(false) }
    var showBrightnessCircle by remember { mutableStateOf(false) }
    var brightnessGestureKey by remember { mutableIntStateOf(0) }
    var volumeGestureKey by remember { mutableIntStateOf(0) }

    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var showSubtitleSearch by remember { mutableStateOf(false) }
    var subtitleSearchResults by remember { mutableStateOf<List<SubtitleSearchResult>>(emptyList()) }
    var subtitleSearchLoading by remember { mutableStateOf(false) }
    var subtitleSearchStatus by remember { mutableStateOf("") }
    // Website fallback (last-resort manual search) — see SubtitleWebFallback.kt
    var showSubtitleFallback by remember { mutableStateOf(false) }
    var showEmbeddedSubtitleBrowser by remember { mutableStateOf(false) }
    var pendingImportedCandidates by remember { mutableStateOf<SubtitleImportResult.Success?>(null) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showSrtBrowser by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepTimerActive by remember { mutableStateOf(false) }

    var subtitleTextSizeSp by remember { mutableFloatStateOf(22f) }
    var subtitleBottomPadding by remember { mutableFloatStateOf(0.02f) }
    var subtitleSyncOffset by remember { mutableFloatStateOf(0.0f) }
    var subtitleDriftScale by remember { mutableFloatStateOf(1.0f) }
    var appliedSubtitleDriftScale by remember { mutableFloatStateOf(1.0f) }
    var dialogueSyncArmed by remember { mutableStateOf(false) }
    var dialogueSyncReferenceMs by remember { mutableStateOf<Long?>(null) }
    var showDriftDialog by remember { mutableStateOf(false) }
    var driftPointA by remember { mutableStateOf<DriftPoint?>(null) }
    var driftPointB by remember { mutableStateOf<DriftPoint?>(null) }
    var showAppearanceStudio by remember { mutableStateOf(false) }
    var subtitleAppearancePreset by remember { mutableStateOf("CineVault") }
    var subtitleAppearance by remember { mutableStateOf(SubtitlePresets.CineVault) }
    var subtitlePreserveOriginalStyling by remember { mutableStateOf(false) }
    var subtitleGestureFeedback by remember { mutableStateOf("") }
    var autoSyncStatus by remember { mutableStateOf<AutoSyncStatus>(AutoSyncStatus.Idle) }
    var showSubtitleStudio by remember { mutableStateOf(false) }
    var subtitleStudioInitialTab by remember { mutableStateOf<SubtitleStudioTab?>(null) }
    var subtitleBehaviorPrefs by remember { mutableStateOf(loadSubtitleBehaviorPrefs(context)) }
    var subtitleCleaningOptions by remember { mutableStateOf(loadSubtitleCleaningOptions(context)) }
    var subtitleMenuTouchKey by remember { mutableIntStateOf(0) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var autoSubtitleAttemptedForPath by remember { mutableStateOf<String?>(null) }
    var autoSubtitleStatus by remember { mutableStateOf("") }
    var subtitleDownloadInProgress by remember { mutableStateOf(false) }
    var menuTouchKey by remember { mutableIntStateOf(0) }
    var playerViewForSubtitleStyle by remember { mutableStateOf<PlayerView?>(null) }

    var originalSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var appliedSubtitleOffsetMs by remember { mutableLongStateOf(0L) }

    // The "true" primary subtitle source — distinct from originalSubtitleUri
    // (which sync/drift build FROM, and which becomes the DUAL-MERGED file
    // whenever dual mode is on). Kept separately so turning dual mode back
    // off can revert to the actual primary instead of getting stuck on a
    // merged file with nothing to un-merge from.
    var primarySubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var primarySubtitleLanguage by remember { mutableStateOf<String?>(null) }
    var audioLanguageCheckedForPath by remember { mutableStateOf<String?>(null) }
    var dualSubtitlesEnabled by remember { mutableStateOf(false) }
    var dualSecondaryLanguage by remember { mutableStateOf("hi") }
    var dualGapLines by remember { mutableStateOf(1) }
    var dualStatusText by remember { mutableStateOf("") }
    val dualSecondaryColorHex = "#7FDBFF"

    // Which subtitle source/track is actually active right now — the single
    // source of truth for both the checkmark in SubtitleTrackSelectorSheet
    // AND the status line under the quick menu's header. Built with the
    // exact same key format SubtitleTrackChoice uses (see
    // SubtitleTrackSelector.kt) so the two files can never silently
    // disagree about what "selected" means.
    var selectedSubtitleTrackKey by remember { mutableStateOf<String?>(null) }
    var selectedSubtitleTrackLabel by remember { mutableStateOf("") }
    var selectedSubtitleTrackSource by remember { mutableStateOf("") }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isVideoEnded by remember { mutableStateOf(false) }
    var pendingNextEpisode by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(0) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var autoPlayEnabled by remember { mutableStateOf(true) }

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
    var edgeSwipeHint by remember { mutableStateOf("") }

    var isBuffering by remember { mutableStateOf(false) }
    var showBufferingSpinner by remember { mutableStateOf(false) }
    var stuckBufferingHint by remember { mutableStateOf(false) }
    var playerErrorMessage by remember { mutableStateOf<String?>(null) }
    var errorRetryCount by remember { mutableIntStateOf(0) }

    var droppedFrameNudgeCount by remember { mutableIntStateOf(0) }
    var lastNudgeAtMs by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage(subtitleBehaviorPrefs.preferredLanguages.firstOrNull() ?: "en")
                .setPreferredTextLanguage(subtitleBehaviorPrefs.preferredLanguages.firstOrNull() ?: "en")
                .setSelectUndeterminedTextLanguage(true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleBehaviorPrefs.autoEnableEmbeddedSubtitles).build()
        }
    }

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setBackBuffer(30_000, true)
            .build()
    }

    val mediaSourceFactory = remember { cineVaultMediaSourceFactory(context) }

    val exoPlayer: ExoPlayer = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(CineRenderersFactory(context).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON))
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
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
        showSubtitleSettings = false
        showTrackSelector = false
        showSubtitleSearch = false
        showDriftDialog = false
        showAppearanceStudio = false
        showSubtitleStudio = false
        showSpeedMenu = false
        showSleepMenu = false
        showSrtBrowser = false
        showSubtitleFallback = false
        showEmbeddedSubtitleBrowser = false
        pendingImportedCandidates = null
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

    // Performs the actual deletion — only ever called once the undo window
    // (deleteWithUndo, below) has expired without the person tapping Undo.
    fun finalizeSubtitleDeletion(file: java.io.File) {
        pendingConsentFile = file
        FileManagementHelper.deleteFile(
            context = context,
            file = file,
            onNeedsConsent = { intentSender -> deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) },
            onDeleted = { pendingConsentFile = null },
            onFailed = { e ->
                pendingDeletePaths.remove(file.absolutePath)
                pendingConsentFile = null
                Toast.makeText(context, "Couldn't delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Hides the file immediately and shows the amber Undo snackbar. Real
    // deletion only happens if the snackbar times out or is dismissed
    // without the person tapping Undo.
    fun deleteWithUndo(file: java.io.File) {
        pendingDeletePaths.add(file.absolutePath)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Subtitle deleted",
                actionLabel = "UNDO",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeletePaths.remove(file.absolutePath)
            } else {
                finalizeSubtitleDeletion(file)
            }
        }
    }

    // Entry point every delete button in this screen now calls — opens the
    // styled warning dialog rather than deleting immediately.
    fun requestDeleteSubtitle(file: java.io.File) {
        pendingDeleteConfirmFile = file
    }

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

    fun playPrevious() {
        val idx = episodeList.indexOfFirst { it.video.path == currentVideo.path }
        val prev = episodeList.getOrNull(idx - 1)
        if (prev != null) { currentMediaType = prev.type; currentVideo = prev.video; onPlayNext(prev); edgeSwipeHint = "◀ Previous" }
        else edgeSwipeHint = "No previous video"
        scope.launch { delay(1200); edgeSwipeHint = "" }
    }

    fun playNext() {
        val idx = episodeList.indexOfFirst { it.video.path == currentVideo.path }
        val next = episodeList.getOrNull(idx + 1)
        if (next != null) { currentMediaType = next.type; currentVideo = next.video; onPlayNext(next); edgeSwipeHint = "Next ▶" }
        else edgeSwipeHint = "No next video"
        scope.launch { delay(1200); edgeSwipeHint = "" }
    }

    fun playCurrentVideoWithSubtitle(subtitleUri: Uri? = null, resumePosition: Long = 0L, isOriginalSubtitle: Boolean = true) {
        val isSmbMedia = currentVideo.path.startsWith("smb://", ignoreCase = true)
        val isContentUriMedia = currentVideo.path.startsWith("content://", ignoreCase = true)
        if (!isStreamMedia && !isSmbMedia && !isContentUriMedia && !java.io.File(currentVideo.path).exists()) {
            playerErrorMessage = "File not found. It may have been moved, renamed, or the drive it's on was disconnected."
            return
        }
        try {
            playerErrorMessage = null
            if (subtitleUri != null && isOriginalSubtitle) {
                originalSubtitleUri = subtitleUri
                appliedSubtitleOffsetMs = 0L
                subtitleSyncOffset = 0f
            }
            val mediaItemBuilder = MediaItem.Builder().setUri(currentVideo.path)
            if (subtitleUri != null) {
                // MIME type now reflects the SUBTITLE FILE'S actual format
                // rather than always claiming SubRip — files our own sync/
                // clean/dual pipeline generates (cinevault_synced_subtitle,
                // cinevault_cleaned_subtitle, cinevault_dual_merged) are
                // always genuine SRT regardless of the original source
                // format, since those pipelines only operate on SRT text,
                // so they still correctly report as SRT here.
                val detectedFormat = detectSubtitleFormat(subtitleUri)
                val subtitleMimeType = detectedFormat.mimeType ?: MimeTypes.APPLICATION_SUBRIP
                mediaItemBuilder.setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(subtitleMimeType).setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
                ))
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.seekTo(resumePosition.coerceAtLeast(0L))
            exoPlayer.playWhenReady = true; exoPlayer.play()
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            isVideoEnded = false
        } catch (e: Exception) {
            playerErrorMessage = "Couldn't start playback: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // Validated handoff for both the website-fallback flow and the
    // (now-validated) local file picker below — reuses the exact same
    // cleaning + playback pipeline every other subtitle source already
    // goes through, so this isn't a parallel/divergent code path.
    fun applyImportedWebsiteSubtitle(imported: ImportedSubtitle) {
        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            val cleanedUri = withContext(Dispatchers.IO) {
                if (supportsCustomTextPipeline(imported.format)) {
                    buildCleanedSubtitleFile(context, imported.uri, subtitleCleaningOptions)
                } else {
                    null
                }
            } ?: imported.uri

            subtitlesEnabled = true
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            primarySubtitleUri = cleanedUri
            primarySubtitleLanguage = imported.language
            selectedSubtitleTrackKey = "downloaded"
            selectedSubtitleTrackLabel = friendlyLanguageName(imported.language ?: "en")
            selectedSubtitleTrackSource = "Website import"
            playCurrentVideoWithSubtitle(cleanedUri, resumeAt)
            showSubtitleFallback = false
            showEmbeddedSubtitleBrowser = false
            pendingImportedCandidates = null
            showControls = true
            Toast.makeText(context, "Subtitle loaded", Toast.LENGTH_SHORT).show()
        }
    }

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
                    preferredLanguage = subtitleBehaviorPrefs.preferredLanguages.firstOrNull() ?: "en"
                )
            } ?: SubtitleImportResult.Failure("CineVault couldn't open that file.")

            when (result) {
                is SubtitleImportResult.Success -> {
                    if (result.alternatives.isEmpty()) {
                        applyImportedWebsiteSubtitle(result.selected)
                    } else {
                        pendingImportedCandidates = result
                    }
                }
                is SubtitleImportResult.Failure -> Toast.makeText(context, result.userMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun findHttpStatusDetail(error: Throwable): String? {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < 10) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val msg = cause.responseMessage
                return if (!msg.isNullOrBlank()) "${cause.responseCode} $msg" else "${cause.responseCode}"
            }
            cause = cause.cause
            depth++
        }
        return null
    }

    fun friendlyPlaybackError(error: PlaybackException): String {
        val detail = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "File not found. It may have been moved, renamed, or the drive it's on was disconnected."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "Permission denied reading this file."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Connection problem reading this file. If it's on a USB drive or network share, check the connection."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "The server rejected the request for this stream." + (findHttpStatusDetail(error)?.let { " (HTTP $it)" } ?: "")
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                "The server returned this link as something other than a playable video (wrong content type)."
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "This is an unencrypted (http://) link — Android blocks plain http streams by default. Try an https:// link instead."
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
                "Couldn't read this stream at the expected position — the file may be shorter than reported or still uploading."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "This link's format isn't a container CineVault recognizes."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "This device can't decode this file's video or audio format."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "This file appears to be corrupted or incomplete."
            PlaybackException.ERROR_CODE_TIMEOUT ->
                "Timed out trying to start playback."
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> {
                // FIX: was a hardcoded generic string regardless of what
                // ExoPlayer's actual cause chain contained — every OTHER
                // error code in this function falls through to
                // error.message when unhandled, but this specific one
                // discarded real diagnostic detail. "Unspecified" is
                // ExoPlayer's own catch-all bucket for I/O failures that
                // don't fit a more specific code, meaning the REAL reason
                // almost always lives one level down in the cause chain —
                // now surfaced instead of thrown away.
                val causeDetail = error.cause?.message?.takeIf { it.isNotBlank() }
                if (causeDetail != null) "Couldn't read this source: $causeDetail"
                else "Couldn't read this source (unspecified I/O error)."
            }
            else -> error.message ?: "Playback error"
        }
        return "$detail (${error.errorCodeName})"
    }

    fun isTransientPlaybackError(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    fun performSubtitleSearch(query: String, seasonText: String, episodeText: String, language: String = subtitleBehaviorPrefs.preferredLanguages.firstOrNull() ?: "en") {
        subtitleSearchLoading = true
        subtitleSearchStatus = ""
        scope.launch {
            // Both providers queried concurrently rather than one after
            // the other — they're independent network calls, no reason to
            // make the person wait twice as long for a merged list.
            val openSubsDeferred = async {
                OpenSubtitlesClient.searchSubtitlesDetailed(
                    query = query,
                    season = seasonText.toIntOrNull(),
                    episode = episodeText.toIntOrNull(),
                    language = language,
                    preferForced = subtitleBehaviorPrefs.preferForced,
                    preferSdh = subtitleBehaviorPrefs.preferSdh
                )
            }
            val subDlDeferred = async {
                SubDlClient.search(query, seasonText.toIntOrNull(), episodeText.toIntOrNull(), language)
            }
            val openSubsResult = openSubsDeferred.await()
            val subDlResult = subDlDeferred.await()

            subtitleSearchLoading = false
            val openSubsList = (openSubsResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            val subDlList = (subDlResult as? SubtitleSearchListResult.Success)?.results.orEmpty()
            // OpenSubtitles first (already ranked by its own match-scoring
            // above), SubDL appended after — the two providers' relevance
            // scores aren't directly comparable, so concatenating rather
            // than trying to cross-rank them is the honest choice here.
            // FIX (B6): SubDL results now sorted to the top of the
            // combined list — previously appended after all of
            // OpenSubtitles' (often 40-50) results, effectively burying
            // them at the bottom where they were easy to miss entirely.
            val merged = subDlList + openSubsList

            when {
                merged.isNotEmpty() -> { subtitleSearchResults = merged; subtitleSearchStatus = "" }
                openSubsResult is SubtitleSearchListResult.HttpError -> { subtitleSearchResults = emptyList(); subtitleSearchStatus = "Search error: ${openSubsResult.detail}" }
                else -> { subtitleSearchResults = emptyList(); subtitleSearchStatus = "No subtitles found for this search" }
            }
        }
    }

    fun applySearchResult(result: SubtitleSearchResult, alsoPlay: Boolean) {
        scope.launch {
            // Provider-specific download flow — OpenSubtitles uses a
            // numeric file_id needing a separate link-request step, SubDL
            // hands back a ready-to-use relative URL straight from search.
            val downloadResult = if (result.provider == "SubDL" && result.subDlDownloadPath != null) {
                SubDlClient.downloadSubtitle(context, currentVideo.path, result.subDlDownloadPath, result.language)
            } else {
                OpenSubtitlesClient.downloadSubtitleByFileId(context, currentVideo.path, result.fileId, result.language, result.provider)
            }
            when (downloadResult) {
                is SubtitleDownloadResult.Success -> {
                    if (alsoPlay) {
                        // FIX: active-track state (subtitlesEnabled, track
                        // selector, selectedSubtitleTrackKey/label/source,
                        // and the remember-last-language promotion) used to
                        // be set unconditionally above this check — meaning
                        // "Save only" incorrectly marked this subtitle as
                        // the ACTIVE one in the UI even though playback was
                        // never touched. All of that now only happens when
                        // the person actually chose to apply it.
                        subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                        selectedSubtitleTrackKey = "downloaded"
                        // FIX: was hardcoded to "OpenSubtitles" regardless
                        // of which provider actually supplied this result
                        // — meaning a successfully-applied SubDL subtitle
                        // would show up in Track Selector mislabeled as
                        // OpenSubtitles, with no SubDL entry ever visible.
                        // From the outside that looks exactly like "SubDL
                        // apply does nothing," when the download and apply
                        // may have genuinely worked the whole time.
                        selectedSubtitleTrackLabel = friendlyLanguageName(result.language); selectedSubtitleTrackSource = result.provider
                        if (subtitleBehaviorPrefs.rememberLastSelectedLanguage && result.language.isNotBlank()) {
                            subtitleBehaviorPrefs = promoteLanguageToFront(subtitleBehaviorPrefs, result.language.take(2).lowercase())
                            saveSubtitleBehaviorPrefs(context, subtitleBehaviorPrefs)
                        }
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val cleanedApplyUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, downloadResult.uri, subtitleCleaningOptions) } ?: downloadResult.uri
                        primarySubtitleUri = cleanedApplyUri
                        primarySubtitleLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playCurrentVideoWithSubtitle(subtitleUri = cleanedApplyUri, resumePosition = resumeAt)
                        showSubtitleSearch = false; showControls = true
                        Toast.makeText(context, "Subtitle applied", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Subtitle saved — apply it from Tracks", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Toast.makeText(context, downloadResult.summary(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    LaunchedEffect(currentVideo.path) {
        val savedPosition = if (isStreamMedia) 0L else loadPlaybackPosition(context, currentVideo.path)
        position = savedPosition; duration = 1L; showControls = true; showTopBar = true
        showAudioSelector = false; showSubtitleSettings = false; showTrackSelector = false; showSubtitleSearch = false; showSpeedMenu = false; showSleepMenu = false; showSrtBrowser = false
        showSubtitleFallback = false; showEmbeddedSubtitleBrowser = false; pendingImportedCandidates = null
        subtitleSearchResults = emptyList(); subtitleSearchStatus = ""; subtitleSearchLoading = false
        pendingNextEpisode = null; nextEpisodeCountdown = 0; showNextEpisodeOverlay = false
        previewBitmap = null; previewFrames = emptyList(); isVideoEnded = false
        playerErrorMessage = null; errorRetryCount = 0; stuckBufferingHint = false
        originalSubtitleUri = null; appliedSubtitleOffsetMs = 0L; subtitleSyncOffset = 0.0f
        subtitleDriftScale = 1.0f; appliedSubtitleDriftScale = 1.0f; driftPointA = null; driftPointB = null
        dialogueSyncArmed = false; dialogueSyncReferenceMs = null; showDriftDialog = false
        dualSubtitlesEnabled = false; dualStatusText = ""; primarySubtitleUri = null; primarySubtitleLanguage = null; audioLanguageCheckedForPath = null
        subtitlePreserveOriginalStyling = false
        subtitleGestureFeedback = ""
        autoSyncStatus = AutoSyncStatus.Idle
        selectedSubtitleTrackKey = null; selectedSubtitleTrackLabel = ""; selectedSubtitleTrackSource = ""
        droppedFrameNudgeCount = 0; lastNudgeAtMs = 0L
        if (!isStreamMedia) recordWatchHistory(context, currentVideo.path, cleanVideoTitle(currentVideo.path))
        if (isRestrictedFolderMedia) updateRestrictedFolderLastPlayed(context, currentVideo.path, currentVideo.folderPath)

        // FIX: local-file match is now checked BEFORE the cached network
        // subtitle, not after — previously an old cached OpenSubtitles
        // download always won even when a local .srt sitting right next to
        // the video (almost always more release-accurate) was available.
        // A local match is also generally free/instant to check, so trying
        // it first doesn't cost anything even when it doesn't pan out.
        val localMatch = if (!isStreamMedia && subtitleBehaviorPrefs.autoLoadMatchingLocalFile) {
            withContext(Dispatchers.IO) { findBestMatchingLocalSubtitle(currentVideo.path, subtitleBehaviorPrefs.preferredLanguages) }
        } else null

        val cachedSubtitle = if (localMatch == null && !isStreamMedia && canDownloadExternalSubtitles) {
            withContext(Dispatchers.IO) { OpenSubtitlesClient.findCachedSubtitle(context, currentVideo.path, subtitleBehaviorPrefs.preferredLanguages) }
        } else null

        when {
            localMatch != null -> {
                subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                val localUri = Uri.fromFile(localMatch.file)
                val cleanedLocalUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, localUri, subtitleCleaningOptions) } ?: localUri
                primarySubtitleUri = cleanedLocalUri
                primarySubtitleLanguage = localMatch.languageCode
                playCurrentVideoWithSubtitle(cleanedLocalUri, savedPosition)
                autoSubtitleAttemptedForPath = currentVideo.path
                selectedSubtitleTrackKey = "local:${localMatch.file.absolutePath}"
                selectedSubtitleTrackLabel = localMatch.file.name; selectedSubtitleTrackSource = "Local file"
            }
            cachedSubtitle != null -> {
                subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                val cleanedCachedUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, cachedSubtitle.uri, subtitleCleaningOptions) } ?: cachedSubtitle.uri
                primarySubtitleUri = cleanedCachedUri
                primarySubtitleLanguage = cachedSubtitle.language
                playCurrentVideoWithSubtitle(cleanedCachedUri, savedPosition)
                autoSubtitleAttemptedForPath = currentVideo.path
                selectedSubtitleTrackKey = "downloaded"
                selectedSubtitleTrackLabel = friendlyLanguageName(cachedSubtitle.language); selectedSubtitleTrackSource = "OpenSubtitles"
            }
            else -> {
                playCurrentVideoWithSubtitle(resumePosition = savedPosition)
            }
        }

        if (!isStreamMedia && canDownloadExternalSubtitles && !isRestrictedFolderMedia &&
            subtitleBehaviorPrefs.autoDownloadWhenMissing && cachedSubtitle == null && localMatch == null &&
            autoSubtitleAttemptedForPath != currentVideo.path
        ) {
            autoSubtitleAttemptedForPath = currentVideo.path
            scope.launch {
                delay(1200); if (subtitleDownloadInProgress) return@launch
                subtitleDownloadInProgress = true
                autoSubtitleStatus = "Searching subtitles..."
                try {
                    val result = OpenSubtitlesClient.downloadBestSubtitleDetailed(context, currentVideo.path, subtitleBehaviorPrefs.preferredLanguages)
                    if (result is SubtitleDownloadResult.Success) {
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                        subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                        autoSubtitleStatus = "Subtitle loaded"
                        val cleanedResultUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, result.uri, subtitleCleaningOptions) } ?: result.uri
                        primarySubtitleUri = cleanedResultUri
                        primarySubtitleLanguage = SubtitleLanguageRegistry.normalize(result.language)
                        playCurrentVideoWithSubtitle(cleanedResultUri, resumeAt)
                        selectedSubtitleTrackKey = "downloaded"
                        selectedSubtitleTrackLabel = friendlyLanguageName(result.language); selectedSubtitleTrackSource = "OpenSubtitles"
                        delay(1400); autoSubtitleStatus = ""
                    } else {
                        autoSubtitleStatus = result.summary(); delay(3500); autoSubtitleStatus = ""
                    }
                } catch (e: Exception) {
                    autoSubtitleStatus = "Subtitle failed: ${e.message ?: e.javaClass.simpleName}"; delay(3500); autoSubtitleStatus = ""
                }
                finally { subtitleDownloadInProgress = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer
        // Bridges hardware media-button next/previous (headset, Bluetooth)
        // to this screen's own episode-switching logic — see
        // CineVaultForwardingPlayer.kt for why a direct player command
        // doesn't work here.
        CineVaultPlayerHolder.onNextRequested = { playNext() }
        CineVaultPlayerHolder.onPreviousRequested = { playPrevious() }
        // Starts (or re-attaches) the foreground playback service — this is
        // what keeps playback alive and gives lock-screen media controls
        // once the screen locks or the app backgrounds, instead of the
        // previous behavior where MainActivity.onStop() unconditionally
        // paused playback the moment the screen turned off. The service
        // reads CineVaultPlayerHolder.currentPlayer itself (just set above)
        // rather than the player being handed to it directly.
        androidx.core.content.ContextCompat.startForegroundService(
            context, Intent(context, CineVaultPlaybackService::class.java)
        )
        brightnessPercent = try {
            val raw = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
            ((raw / 255f) * 100f).toInt().coerceIn(5, 100)
        } catch (_: Exception) { 70 }
        activity?.enterImmersiveModeForPlayer()
    }

    DisposableEffect(exoPlayer, currentVideo.path, episodeList) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    errorRetryCount = 0
                    playerErrorMessage = null
                    val realDuration = exoPlayer.duration
                    if (realDuration > 0L && !isStreamMedia) {
                        saveDuration(context, currentVideo.path, realDuration)
                    }
                    // "Disable subtitles when audio matches preferred
                    // language" — checked once per video (guarded by
                    // audioLanguageCheckedForPath) right when tracks first
                    // become available, so it sets the DEFAULT state rather
                    // than fighting a choice the person makes afterward.
                    if (subtitleBehaviorPrefs.disableWhenAudioMatchesPreferred && audioLanguageCheckedForPath != currentVideo.path) {
                        audioLanguageCheckedForPath = currentVideo.path
                        val audioLang = exoPlayer.currentTracks.groups
                            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { idx -> g.getTrackFormat(idx).language } }
                        val preferred = subtitleBehaviorPrefs.preferredLanguages.firstOrNull()
                        if (audioLang != null && preferred != null && audioLang.take(2).equals(preferred.take(2), ignoreCase = true)) {
                            subtitlesEnabled = false
                            trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        }
                    }
                }
                if (state == Player.STATE_ENDED) {
                    isVideoEnded = true
                    if (autoPlayEnabled && episodeList.isNotEmpty()) {
                        val idx = episodeList.indexOfFirst { it.video.path == currentVideo.path }
                        val next = episodeList.getOrNull(idx + 1)
                        if (next != null) {
                            if (currentMediaType.equals("tv", ignoreCase = true)) {
                                pendingNextEpisode = next; nextEpisodeCountdown = 5
                                showNextEpisodeOverlay = true; showControls = true; showTopBar = true
                            } else {
                                currentMediaType = next.type; currentVideo = next.video; onPlayNext(next)
                            }
                        }
                    }
                    showControls = true; showTopBar = true
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }

            override fun onPlayerError(error: PlaybackException) {
                val posAtError = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (isTransientPlaybackError(error) && errorRetryCount < 2) {
                    errorRetryCount++
                    scope.launch {
                        delay(1000L * errorRetryCount)
                        playCurrentVideoWithSubtitle(subtitleUri = originalSubtitleUri, resumePosition = posAtError, isOriginalSubtitle = false)
                    }
                } else {
                    playerErrorMessage = friendlyPlaybackError(error)
                    isPlaying = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(exoPlayer, currentVideo.path) {
        val analyticsListener = object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                if (droppedFrames < 8) return
                if (droppedFrameNudgeCount >= 3) return
                val now = System.currentTimeMillis()
                if (now - lastNudgeAtMs < 90_000L) return
                lastNudgeAtMs = now
                droppedFrameNudgeCount++
                exoPlayer.seekTo(exoPlayer.currentPosition)
            }
        }
        exoPlayer.addAnalyticsListener(analyticsListener)
        onDispose { exoPlayer.removeAnalyticsListener(analyticsListener) }
    }

    LaunchedEffect(isBuffering) {
        if (isBuffering) { delay(400); if (isBuffering) showBufferingSpinner = true }
        else { showBufferingSpinner = false; stuckBufferingHint = false }
    }

    LaunchedEffect(isBuffering, currentVideo.path) {
        if (isBuffering) {
            delay(15_000)
            if (isBuffering) stuckBufferingHint = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isStreamMedia) savePlaybackPosition(context, currentVideo.path, exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.release()
            AudioSyncHolder.offsetUs = 0L
            if (CineVaultPlayerHolder.currentPlayer == exoPlayer) CineVaultPlayerHolder.currentPlayer = null
            CineVaultPlayerHolder.onNextRequested = null
            CineVaultPlayerHolder.onPreviousRequested = null
            // Only reached on an actual exit from the player (Back pressed,
            // navigated away) — NOT fired just because the screen locked or
            // the app backgrounded, since Compose disposal and Activity
            // onStop/onPause are different things. Safe to stop the service
            // here since we've already cleared currentPlayer above.
            context.stopService(Intent(context, CineVaultPlaybackService::class.java))
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
            // This runs on every exit from the player — button, swipe, or
            // hardware back — so if setRequestedOrientation() throws here
            // (see comment above near the glasses-connect effect), it would
            // crash on literally any way of leaving the player. Wrapped for
            // the same reason.
            try { activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } catch (_: Exception) {}
            activity?.exitImmersiveModeForPlayer()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSeekbar) position = exoPlayer.currentPosition.coerceAtLeast(0)
            duration = exoPlayer.duration.coerceAtLeast(1)
            isPlaying = exoPlayer.isPlaying
            delay(350)
        }
    }

    LaunchedEffect(currentVideo.path, duration) {
        if (!isStreamMedia && duration > 1000L) {
            previewFrames = emptyList()
            val quick = VideoThumbnailHelper.generatePreviewCache(context, currentVideo.path, duration, 18)
            if (quick.isNotEmpty()) { previewFrames = quick; previewBitmap = quick.firstOrNull()?.bitmap ?: previewBitmap }
            val dense = VideoThumbnailHelper.generatePreviewCache(context, currentVideo.path, duration, 72)
            if (dense.isNotEmpty()) previewFrames = dense
        } else { previewFrames = emptyList(); previewBitmap = null }
    }

    LaunchedEffect(showSeekPreview, previewPosition) {
        if (showSeekPreview) { isSeekPreviewLarge = false; delay(650); if (showSeekPreview) isSeekPreviewLarge = true }
        else isSeekPreviewLarge = false
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setActions(buildPipActions(context, isPlaying))
                        .build()
                )
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(exoPlayer) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val player = CineVaultPlayerHolder.currentPlayer ?: return
                when (intent.getIntExtra("pip_action", -1)) {
                    0 -> { if (player.isPlaying) player.pause() else { player.play(); player.playWhenReady = true } }
                    1 -> { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }
                    2 -> { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration.coerceAtLeast(0))) }
                }
            }
        }
        val filter = IntentFilter("com.sole.cinevault.PIP_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(receiver, filter)
        }
        onDispose { try { context.applicationContext.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    LaunchedEffect(currentVideo.path) {
        while (true) {
            delay(5000)
            val current = exoPlayer.currentPosition.coerceAtLeast(0L)
            val total = exoPlayer.duration.coerceAtLeast(1L)
            if (!isStreamMedia && current > 5000L && current < total - 5000L) {
                savePlaybackPosition(context, currentVideo.path, current)
                recordWatchHistory(context, currentVideo.path, cleanVideoTitle(currentVideo.path))
            }
        }
    }

    LaunchedEffect(showControls, showAudioSelector, showSubtitleSettings, showTrackSelector, showSubtitleSearch, showSpeedMenu, showSleepMenu, showSrtBrowser, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showTrackSelector || showSubtitleSearch || showDriftDialog || showAppearanceStudio || showSubtitleStudio || dialogueSyncArmed || showSpeedMenu || showSleepMenu || showSrtBrowser
        if (showControls && !anyMenuOpen && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !anyMenuOpen) showControls = false
        }
    }

    LaunchedEffect(showTopBar, showAudioSelector, showSubtitleSettings, showTrackSelector, showSubtitleSearch, showSpeedMenu, showSleepMenu, showSrtBrowser, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showTrackSelector || showSubtitleSearch || showDriftDialog || showAppearanceStudio || showSubtitleStudio || dialogueSyncArmed || showSpeedMenu || showSleepMenu || showSrtBrowser
        if (showTopBar && !anyMenuOpen && !isDraggingSeekbar) {
            delay(2800)
            if (!isDraggingSeekbar && !anyMenuOpen) showTopBar = false
        }
    }

    LaunchedEffect(showAudioSelector, menuTouchKey) { if (showAudioSelector) { delay(9000); showAudioSelector = false } }
    LaunchedEffect(showSubtitleSettings, subtitleMenuTouchKey) { if (showSubtitleSettings) { delay(9000); showSubtitleSettings = false } }
    // FIX: shortened idle timeouts — these only ever fire on genuine
    // inactivity now that C3's activity-detection correctly resets them
    // on every real interaction (verified: Appearance Studio does close,
    // just felt slow at the old duration). Track Selector (12s) and SRT
    // Browser (20s) weren't flagged as an issue, left unchanged.
    LaunchedEffect(showTrackSelector, subtitleMenuTouchKey) { if (showTrackSelector) { delay(12000); showTrackSelector = false } }
    LaunchedEffect(showSubtitleSearch, subtitleMenuTouchKey) { if (showSubtitleSearch) { delay(18000); showSubtitleSearch = false } }
    LaunchedEffect(showAppearanceStudio, subtitleMenuTouchKey) { if (showAppearanceStudio) { delay(15000); showAppearanceStudio = false } }
    LaunchedEffect(showSubtitleStudio, subtitleMenuTouchKey) { if (showSubtitleStudio) { delay(30000); showSubtitleStudio = false } }
    LaunchedEffect(showSrtBrowser) { if (showSrtBrowser) { delay(20000); showSrtBrowser = false } }
    LaunchedEffect(showSpeedMenu) { if (showSpeedMenu) { delay(8000); showSpeedMenu = false } }
    LaunchedEffect(showSleepMenu) { if (showSleepMenu) { delay(8000); showSleepMenu = false } }
    LaunchedEffect(brightnessGestureKey) { if (brightnessGestureKey > 0) { delay(1400); showBrightnessCircle = false } }
    LaunchedEffect(volumeGestureKey) { if (volumeGestureKey > 0) { delay(1400); showVolumeCircle = false } }

    // Shared by both the standalone Track Selector sheet and the Subtitle
    // Studio's Track tab — previously duplicated verbatim in both places,
    // which is exactly how the Downloaded case would have silently NOT
    // gotten cleaning applied in one of the two copies if edited by hand.
    // One function, both call sites use it.
    fun selectSubtitleTrack(choice: SubtitleTrackChoice) {
        subtitleMenuTouchKey++
        when (choice) {
            is SubtitleTrackChoice.Off -> {
                subtitlesEnabled = false
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                selectedSubtitleTrackKey = choice.key; selectedSubtitleTrackLabel = ""; selectedSubtitleTrackSource = ""
            }
            is SubtitleTrackChoice.Embedded -> {
                subtitlesEnabled = true
                val group = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.getOrNull(choice.groupIndex)
                if (group != null) {
                    trackSelector.parameters = trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(choice.trackIndexInGroup)))
                        .build()
                }
                selectedSubtitleTrackKey = choice.key
                selectedSubtitleTrackLabel = friendlyLanguageName(choice.language)
                selectedSubtitleTrackSource = "Embedded"
                if (subtitleBehaviorPrefs.rememberLastSelectedLanguage && choice.language.isNotBlank() && choice.language != "und") {
                    subtitleBehaviorPrefs = promoteLanguageToFront(subtitleBehaviorPrefs, choice.language.take(2).lowercase())
                    saveSubtitleBehaviorPrefs(context, subtitleBehaviorPrefs)
                }
            }
            is SubtitleTrackChoice.Downloaded -> {
                subtitlesEnabled = true
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                scope.launch {
                    val cleaned = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, Uri.fromFile(choice.file), subtitleCleaningOptions) } ?: Uri.fromFile(choice.file)
                    primarySubtitleUri = cleaned
                    primarySubtitleLanguage = SubtitleLanguageRegistry.normalize(choice.language)
                    playCurrentVideoWithSubtitle(subtitleUri = cleaned, resumePosition = resumeAt)
                }
                selectedSubtitleTrackKey = choice.key
                selectedSubtitleTrackLabel = friendlyLanguageName(choice.language); selectedSubtitleTrackSource = "OpenSubtitles"
            }
            is SubtitleTrackChoice.Local -> {
                pendingSrtUri = Uri.fromFile(choice.file)
            }
        }
    }

    LaunchedEffect(pendingSrtUri) {
        val uri = pendingSrtUri ?: return@LaunchedEffect
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        subtitlesEnabled = true
        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        // FIX: previously hardcoded "SRT loaded"/"SRT file loaded" even
        // when the picked file was .vtt/.ass/.ssa/.ttml — now reflects
        // what was actually loaded.
        val pickedFormat = detectSubtitleFormat(uri)
        val formatLabel = if (pickedFormat == SubtitleFormat.SRT || pickedFormat == SubtitleFormat.UNKNOWN) "Subtitle" else pickedFormat.label.substringBefore(" (")
        autoSubtitleStatus = "$formatLabel loaded"
        val cleanedSrtUri = withContext(Dispatchers.IO) { buildCleanedSubtitleFile(context, uri, subtitleCleaningOptions) } ?: uri
        primarySubtitleUri = cleanedSrtUri
        val pickedFile = uri.path?.let { java.io.File(it) }
        // Best-effort language detection from the filename itself (e.g.
        // "Movie.hi.srt") using the same parser the auto-matcher uses —
        // stays null (unknown) for a bare "Movie.srt" with no language
        // token, which is a safe/honest fallback rather than guessing.
        primarySubtitleLanguage = pickedFile?.name?.let { name -> parseSubtitleFilename(name).first }
        playCurrentVideoWithSubtitle(subtitleUri = cleanedSrtUri, resumePosition = resumeAt)
        selectedSubtitleTrackKey = "local:${pickedFile?.absolutePath ?: uri.toString()}"
        selectedSubtitleTrackLabel = pickedFile?.name ?: "Subtitle file"; selectedSubtitleTrackSource = "Local file"
        showSubtitleSettings = false; showTrackSelector = false; showControls = true
        Toast.makeText(context, "$formatLabel file loaded", Toast.LENGTH_SHORT).show()
        delay(1400); autoSubtitleStatus = ""
        pendingSrtUri = null
    }

    val activeSubtitleFormat = remember(originalSubtitleUri) { originalSubtitleUri?.let { detectSubtitleFormat(it) } ?: SubtitleFormat.UNKNOWN }
    val isAssOrSsaFormat = activeSubtitleFormat == SubtitleFormat.ASS || activeSubtitleFormat == SubtitleFormat.SSA

    LaunchedEffect(playerViewForSubtitleStyle, subtitleTextSizeSp, subtitleBottomPadding, subtitleAppearance, dualSubtitlesEnabled, subtitlePreserveOriginalStyling, isAssOrSsaFormat) {
        val sv = playerViewForSubtitleStyle?.subtitleView
        sv?.setUserDefaultStyle()
        // Embedded styling is enabled in TWO cases: dual mode (needs the
        // injected <font color> tag to render) or the person explicitly
        // asked to preserve an ASS/SSA file's own styling. Off otherwise,
        // so CineVault's own styling stays authoritative for plain SRT/VTT.
        val useEmbeddedStyles = dualSubtitlesEnabled || (subtitlePreserveOriginalStyling && isAssOrSsaFormat)
        sv?.setApplyEmbeddedStyles(useEmbeddedStyles); sv?.setApplyEmbeddedFontSizes(false)
        sv?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
        sv?.setBottomPaddingFraction(subtitleBottomPadding)
        sv?.setStyle(
            CaptionStyleCompat(
                subtitleAppearance.foregroundColor,
                subtitleAppearance.backgroundColor,
                AndroidColor.TRANSPARENT,
                subtitleAppearance.edgeType,
                subtitleAppearance.edgeColor,
                null
            )
        )
    }

    LaunchedEffect(subtitleSyncOffset, subtitleDriftScale, originalSubtitleUri) {
        val baseUri = originalSubtitleUri ?: return@LaunchedEffect
        if (!subtitlesEnabled) return@LaunchedEffect
        val offsetMs = (subtitleSyncOffset * 1000f).toLong()
        if (offsetMs == appliedSubtitleOffsetMs && subtitleDriftScale == appliedSubtitleDriftScale) return@LaunchedEffect
        delay(350)
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shiftedUri = withContext(Dispatchers.IO) { buildShiftedSubtitleFile(context, baseUri, offsetMs, subtitleDriftScale) }
        if (shiftedUri != null) {
            appliedSubtitleOffsetMs = offsetMs
            appliedSubtitleDriftScale = subtitleDriftScale
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
    fun armDialogueSync() {
        dialogueSyncReferenceMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        dialogueSyncArmed = true
        exoPlayer.play()
        showSubtitleSettings = false
    }
    fun cancelDialogueSync() {
        dialogueSyncArmed = false
        dialogueSyncReferenceMs = null
    }
    fun confirmDialogueSyncTap() {
        val reference = dialogueSyncReferenceMs
        if (reference != null) {
            val deltaMs = exoPlayer.currentPosition - reference
            subtitleSyncOffset = (subtitleSyncOffset + deltaMs / 1000f).coerceIn(-10f, 10f)
            Toast.makeText(context, "Sync adjusted by ${if (deltaMs >= 0) "+" else ""}${String.format("%.1f", deltaMs / 1000f)}s", Toast.LENGTH_SHORT).show()
        }
        dialogueSyncArmed = false
        dialogueSyncReferenceMs = null
    }

    // ── Progressive Drift Correction ─────────────────────────────────
    fun markDriftPointA(correctionSeconds: Float) { driftPointA = DriftPoint(exoPlayer.currentPosition.coerceAtLeast(0L), correctionSeconds) }
    fun markDriftPointB(correctionSeconds: Float) { driftPointB = DriftPoint(exoPlayer.currentPosition.coerceAtLeast(0L), correctionSeconds) }
    fun applyDriftFix() {
        val a = driftPointA; val b = driftPointB
        if (a == null || b == null || a.positionMs == b.positionMs) return
        val (scale, shiftMs) = computeDriftTransform(a, b)
        subtitleDriftScale = scale
        subtitleSyncOffset = (shiftMs / 1000f).coerceIn(-30f, 30f)
        showDriftDialog = false
        Toast.makeText(context, "Drift correction applied", Toast.LENGTH_SHORT).show()
    }

    // ── Dual Subtitles ─────────────────────────────────────────────────
    // Finds a genuine secondary-language subtitle (not machine-translated
    // — see SubtitleDualMerge.kt), downloads it to a LANGUAGE-SPECIFIC
    // cache file (never the primary's cache slot), merges it under the
    // primary via overlap-join, and applies the merged file as the active
    // subtitle. Rebuilds automatically if the secondary language changes
    // while dual mode is already on.
    fun fetchAndApplyDualSecondary() {
        val primary = primarySubtitleUri
        if (primary == null) {
            Toast.makeText(context, "Dual subtitles need a downloaded or local subtitle as the primary track", Toast.LENGTH_LONG).show()
            dualSubtitlesEnabled = false
            return
        }
        if (!supportsCustomTextPipeline(detectSubtitleFormat(primary))) {
            Toast.makeText(context, "Dual subtitles currently only work with .srt as the primary track", Toast.LENGTH_LONG).show()
            dualSubtitlesEnabled = false
            return
        }
        // FIX: previously nothing stopped the secondary language from
        // being the SAME as the primary — picking, say, English as both
        // would silently "merge" a subtitle with itself. Now blocked with
        // a clear message, using the real tracked primary language rather
        // than guessing from preferences.
        val normalizedSecondary = SubtitleLanguageRegistry.normalize(dualSecondaryLanguage)
        if (primarySubtitleLanguage != null && normalizedSecondary != null && primarySubtitleLanguage == normalizedSecondary) {
            Toast.makeText(context, "Secondary language can't be the same as the primary (${friendlyLanguageName(primarySubtitleLanguage)}) — pick a different one", Toast.LENGTH_LONG).show()
            dualSubtitlesEnabled = false
            return
        }
        dualStatusText = "Searching ${friendlyLanguageName(dualSecondaryLanguage)} subtitles..."
        scope.launch {
            val searchQuery = OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path)
            val searchResult = OpenSubtitlesClient.searchSubtitlesDetailed(searchQuery, language = dualSecondaryLanguage)
            val bestFileId = (searchResult as? SubtitleSearchListResult.Success)?.results?.firstOrNull()?.fileId
            if (bestFileId == null) {
                dualStatusText = "No ${friendlyLanguageName(dualSecondaryLanguage)} subtitle found for this video"
                Toast.makeText(context, dualStatusText, Toast.LENGTH_LONG).show()
                dualSubtitlesEnabled = false
                return@launch
            }
            dualStatusText = "Downloading ${friendlyLanguageName(dualSecondaryLanguage)} subtitle..."
            // FIX: secondaryLanguageCacheFile() no longer exists — the main
            // subtitleCacheFile() is itself language-aware now, so every
            // language (primary or secondary) gets its own slot by
            // construction and there's nothing special-case needed here
            // anymore to avoid a collision.
            val targetFile = OpenSubtitlesClient.subtitleCacheFile(context, currentVideo.path, dualSecondaryLanguage)
            val downloadResult = OpenSubtitlesClient.downloadSubtitleToFile(targetFile, bestFileId, dualSecondaryLanguage)
            if (downloadResult !is SubtitleDownloadResult.Success) {
                dualStatusText = downloadResult.summary()
                Toast.makeText(context, dualStatusText, Toast.LENGTH_LONG).show()
                dualSubtitlesEnabled = false
                return@launch
            }
            val merged = withContext(Dispatchers.IO) {
                val primaryText = readTextFromUri(context, primary) ?: return@withContext null
                val secondaryText = readTextFromUri(context, downloadResult.uri) ?: return@withContext null
                val mergedText = mergeDualSubtitles(primaryText, secondaryText, dualSecondaryColorHex, dualGapLines)
                try {
                    // FIX: was a single fixed filename shared by every video
                    // and every dual-merge request ("cinevault_dual_merged.
                    // srt") — rapid changes or overlapping coroutines could
                    // have one request's write clobber a file another
                    // request/ExoPlayer was still reading. Now unique per
                    // video + secondary language, matching the same fix
                    // applied to the sync/clean temp files below.
                    val uniqueName = "cinevault_dual_${OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path).hashCode()}_$normalizedSecondary.srt"
                    val outFile = java.io.File(context.cacheDir, uniqueName)
                    outFile.writeText(mergedText)
                    Uri.fromFile(outFile)
                } catch (e: Exception) { null }
            }
            if (merged == null) {
                dualStatusText = "Couldn't merge subtitles"
                Toast.makeText(context, dualStatusText, Toast.LENGTH_LONG).show()
                dualSubtitlesEnabled = false
                return@launch
            }
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            playCurrentVideoWithSubtitle(subtitleUri = merged, resumePosition = resumeAt, isOriginalSubtitle = false)
            originalSubtitleUri = merged
            appliedSubtitleOffsetMs = (subtitleSyncOffset * 1000f).toLong()
            dualStatusText = "Dual subtitles: ${if (primarySubtitleLanguage != null) friendlyLanguageName(primarySubtitleLanguage) else "Primary"} + ${friendlyLanguageName(dualSecondaryLanguage)}"
        }
    }

    fun disableDualSubtitles() {
        dualSubtitlesEnabled = false
        dualStatusText = ""
        val primary = primarySubtitleUri ?: return
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        playCurrentVideoWithSubtitle(subtitleUri = primary, resumePosition = resumeAt)
    }

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
    val primarySubtitleForAutoSync = primarySubtitleUri
    val autoSyncAvailable = primarySubtitleForAutoSync != null &&
        supportsCustomTextPipeline(detectSubtitleFormat(primarySubtitleForAutoSync)) &&
        !isStreamMedia &&
        !currentVideo.path.startsWith("smb://", ignoreCase = true) &&
        (currentVideo.path.startsWith("content://", ignoreCase = true) || java.io.File(currentVideo.path).exists())

    fun runAutoSync() {
        val primary = primarySubtitleUri
        if (primary == null) {
            Toast.makeText(context, "Auto-Sync needs a downloaded or local subtitle loaded first", Toast.LENGTH_LONG).show()
            return
        }
        autoSyncStatus = AutoSyncStatus.Analyzing("Extracting audio…")
        scope.launch {
            val srtText = withContext(Dispatchers.IO) { readTextFromUri(context, primary) }
            if (srtText == null) {
                autoSyncStatus = AutoSyncStatus.Failed("Couldn't read the subtitle file")
                return@launch
            }
            autoSyncStatus = AutoSyncStatus.Analyzing("Analysing dialogue…")
            val audioLang = exoPlayer.currentTracks.groups
                .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { idx -> g.getTrackFormat(idx).language } }
            // FIX: real crash — "Player is accessed on the wrong thread."
            // exoPlayer.duration was previously read INSIDE the
            // withContext(Dispatchers.Default) block below, as part of
            // the argument expression — meaning it evaluated on that
            // background thread when the lambda actually ran, not before.
            // ExoPlayer requires every access, even a simple property
            // read, to happen on the thread it was created on. Read here,
            // on the main thread, before switching dispatchers.
            val videoDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            val result = withContext(Dispatchers.Default) {
                AutoSyncEngine.run(context, currentVideo.path, videoDurationMs, audioLang, srtText)
            }
            autoSyncStatus = result
        }
    }

    fun applyAutoSyncResult(result: SubtitleSyncResult) {
        // FIX: previously only ever applied initialOffsetMs — a drift
        // result (timeScale != 1.0) would compute correctly but then
        // silently have its scale discarded on Apply, leaving only the
        // flat-offset portion in effect. Now applies both, through the
        // exact same subtitleDriftScale state the manual "Fix Gradual
        // Drift" tool already uses, so it goes through the identical
        // reactive rebuild path.
        subtitleSyncOffset = (result.initialOffsetMs / 1000f).coerceIn(-10f, 10f)
        subtitleDriftScale = result.timeScale.toFloat()
        autoSyncStatus = AutoSyncStatus.Idle
        subtitleMenuTouchKey++
        Toast.makeText(context, if (result.timeScale != 1.0) "Auto-Sync applied (drift correction)" else "Auto-Sync applied", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(showNextEpisodeOverlay, pendingNextEpisode) {
        if (showNextEpisodeOverlay && pendingNextEpisode != null) {
            for (count in 5 downTo 1) {
                nextEpisodeCountdown = count; delay(1000)
                if (!showNextEpisodeOverlay || pendingNextEpisode == null) return@LaunchedEffect
            }
            val next = pendingNextEpisode
            if (next != null) { showNextEpisodeOverlay = false; pendingNextEpisode = null; currentMediaType = next.type; currentVideo = next.video; onPlayNext(next) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val isLandscape = maxWidth > maxHeight
        val isSmallPhone = maxWidth < 430.dp || maxHeight < 760.dp
        val isCompactLandscape = isLandscape && maxHeight < 430.dp
        val layoutScale = when { isCompactLandscape -> 0.70f; isSmallPhone && !isLandscape -> 0.78f; isSmallPhone -> 0.82f; isLandscape -> 0.90f; else -> 1f }
        val deckNaturalWidth = 66f * 6 + 98f + 7f * 7 + 24f
        val fitScale = ((maxWidth.value - 32f) / deckNaturalWidth).coerceAtMost(1f)
        val scale = minOf(layoutScale, fitScale).coerceAtLeast(0.42f)
        val playButton = (98 * scale).dp
        val smallButton = (66 * scale).dp
        val hudSize = (72 * scale).dp
        val sidePadding = if (isCompactLandscape) 8.dp else 16.dp
        val bottomDockPadding = when { isCompactLandscape -> 76.dp; isLandscape -> 90.dp; else -> 152.dp }
        val seekBottomPadding = when { isCompactLandscape -> 13.dp; isLandscape -> 17.dp; else -> 92.dp }
        val showIntroSkip = isCurrentTvShow && position in 5_000L..95_000L
        val topClusterPaddingTop = if (isLandscape) 10.dp else 18.dp

        // ── Per-display subtitle profiles ──────────────────────────────
        // Which profile applies right now — external (RayNeo/DP Alt Mode)
        // always wins over phone/tablet since it's a distinct viewing
        // surface, regardless of what the tablet's own screen size says.
        // TV isn't reachable yet (see DisplayProfiles.kt) so it never
        // appears here.
        val displayProfileType = when {
            externalDisplay.isConnected -> DisplayProfileType.EXTERNAL
            isSmallPhone -> DisplayProfileType.PHONE
            else -> DisplayProfileType.TABLET
        }
        val currentProfileId = remember(displayProfileType, isLandscape) { displayProfileId(displayProfileType, isLandscape) }
        var profileLoadedFor by remember { mutableStateOf<String?>(null) }

        // Loads this profile's saved size/position/style the moment the
        // profile changes (rotation, or a RayNeo connecting/disconnecting)
        // — e.g. switching from tablet-landscape to external mid-playback
        // instantly swaps in whatever subtitle look was last used on the
        // glasses, not whatever was active a second ago on the tablet.
        LaunchedEffect(currentProfileId) {
            val settings = loadSubtitleProfileSettings(context, displayProfileType, isLandscape)
            subtitleTextSizeSp = settings.fontSizeSp
            subtitleBottomPadding = settings.bottomPadding
            subtitleAppearancePreset = settings.presetName
            subtitleAppearance = SubtitleAppearance(settings.foregroundColor, settings.edgeType, settings.edgeColor, settings.backgroundColor)
            profileLoadedFor = currentProfileId
        }

        // Persists back to the SAME profile whenever the person adjusts
        // size/position/style — guarded by profileLoadedFor so the values
        // freshly loaded above (for a brand new profile) don't immediately
        // get written back over themselves, and so a value still carrying
        // over from the PREVIOUS profile during the one-frame transition
        // can't leak into the new profile's saved settings.
        LaunchedEffect(currentProfileId, subtitleTextSizeSp, subtitleBottomPadding, subtitleAppearancePreset, subtitleAppearance) {
            if (profileLoadedFor != currentProfileId) return@LaunchedEffect
            delay(400)
            saveSubtitleProfileSettings(
                context, displayProfileType, isLandscape,
                SubtitleProfileSettings(
                    fontSizeSp = subtitleTextSizeSp,
                    bottomPadding = subtitleBottomPadding,
                    presetName = subtitleAppearancePreset,
                    foregroundColor = subtitleAppearance.foregroundColor,
                    edgeType = subtitleAppearance.edgeType,
                    edgeColor = subtitleAppearance.edgeColor,
                    backgroundColor = subtitleAppearance.backgroundColor
                )
            )
        }

        val uiScale = (maxWidth.value / 400f).coerceIn(0.85f, 1.25f)

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        fun anchoredX(iconCenterX: Float, popupWidth: Dp): Int {
            val pw = with(density) { popupWidth.toPx() }
            val pad = with(density) { 8.dp.toPx() }
            return (iconCenterX - pw / 2f).coerceIn(pad, (screenWidthPx - pw - pad).coerceAtLeast(pad)).roundToInt()
        }
        fun anchoredY(desiredBottomPadding: Dp, popupHeightEstimate: Dp): Dp = desiredBottomPadding
        val popupBottomPadding = bottomDockPadding + playButton + 18.dp

        val subtitlePopupWidthBase = if (isLandscape) (maxWidth.value * 0.30f).dp.coerceIn(210.dp, 270.dp) else (maxWidth.value * 0.62f).dp.coerceIn(220.dp, 300.dp)
        val subtitlePopupWidth = subtitleMenuWidth(maxWidth.value, isLandscape)
        val subtitlePopupHeightEstimate = (((if (isCompactLandscape || isLandscape) 220f else 360f) * uiScale).dp).coerceAtMost(maxHeight * 0.45f)
        val trackSelectorWidth = subtitlePopupWidth
        val trackSelectorMaxHeight = (((if (isCompactLandscape || isLandscape) 230f else 380f) * uiScale).dp).coerceAtMost(maxHeight * 0.55f)
        val srtPopupWidth = (subtitlePopupWidthBase.value * uiScale).dp.coerceAtMost(maxWidth * 0.86f)
        val srtPopupMaxHeight = (((if (isCompactLandscape) 160f else if (isLandscape) 200f else 280f) * uiScale).dp).coerceAtMost(maxHeight * 0.5f)
        val audioPopupWidth = ((((if (isCompactLandscape) 175f else if (isLandscape) 190f else 205f) * uiScale).dp).coerceAtMost(maxWidth * 0.75f)) * 0.6f
        val smallMenuWidth = (((165f * uiScale).dp).coerceAtMost(maxWidth * 0.6f)) * 0.6f
        val smallMenuHeightScale = if (isLandscape) 0.95f else 0.6f
        val smallMenuMaxHeight = ((((if (isCompactLandscape) 150f else if (isLandscape) 190f else 230f) * uiScale).dp).coerceAtMost(maxHeight * 0.55f)) * smallMenuHeightScale
        val topIconSize = (44 * uiScale * scale.coerceAtLeast(0.75f)).dp

        val currentMeta = remember(currentVideo.path, episodeList) {
            episodeList.firstOrNull { it.video.path == currentVideo.path }
                ?: episodeList.firstOrNull { it.video.name == currentVideo.name }
        }

        // Previous/Next availability — shown ONLY for TV episodes and
        // Select-Folder videos (where "next in the group" is a meaningful
        // concept), never for a plain movie played from the general library
        // list, where episodeList can be the whole library and "next" would
        // be an unrelated, arbitrary title.
        val showPrevNextButtons = (isCurrentTvShow || isRestrictedFolderMedia) && episodeList.size > 1
        val currentEpisodeIndex = remember(currentVideo.path, episodeList) {
            episodeList.indexOfFirst { it.video.path == currentVideo.path }
        }
        val hasNextVideo = episodeList.size > 1 && currentEpisodeIndex in 0 until episodeList.lastIndex

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = videoScale, scaleY = videoScale, translationX = videoOffsetX, translationY = videoOffsetY),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer; useController = false
                    resizeMode = if (isZoomMode) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
                    playerViewForSubtitleStyle = this
                }
            },
            update = { pv ->
                pv.player = exoPlayer
                pv.resizeMode = if (isZoomMode) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerViewForSubtitleStyle = pv
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

        Box(
            modifier = Modifier.fillMaxSize()
                .videoPlaybackGestures(
                    view = view,
                    videoPathKey = currentVideo.path,
                    episodeListKey = episodeList,
                    edgeSwipeNextEnabled = { showPrevNextButtons },
                    onTap = {
                        when {
                            showAudioSelector -> showAudioSelector = false
                            showSubtitleSettings -> showSubtitleSettings = false
                            showTrackSelector -> showTrackSelector = false
                            showSubtitleSearch -> showSubtitleSearch = false
                            showDriftDialog -> showDriftDialog = false
                            showAppearanceStudio -> showAppearanceStudio = false
                            showSubtitleStudio -> showSubtitleStudio = false
                            dialogueSyncArmed -> {}
                            showSpeedMenu -> showSpeedMenu = false
                            showSleepMenu -> showSleepMenu = false
                            showSrtBrowser -> showSrtBrowser = false
                            else -> { val v = !showControls; showControls = v; showTopBar = v }
                        }
                    },
                    onSeekBack = {
                        exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        position = exoPlayer.currentPosition
                        showControls = true; showTopBar = true
                    },
                    onSeekForward = {
                        exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0)))
                        position = exoPlayer.currentPosition
                        showControls = true; showTopBar = true
                    },
                    onToggleZoomMode = {
                        isZoomMode = !isZoomMode; showControls = true; showTopBar = true
                    },
                    onDragSettled = { brightnessGestureKey++; volumeGestureKey++ },
                    onEdgeSwipeNext = { playNext() },
                    onBrightnessDrag = { deltaY ->
                        brightnessPercent = (brightnessPercent - deltaY.toInt() / 8).coerceIn(5, 100)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightnessPercent / 100f }
                        showBrightnessCircle = true
                    },
                    onVolumeDrag = { deltaY ->
                        volumePercent = (volumePercent - deltaY.toInt() / 8).coerceIn(0, 150)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volumePercent.coerceAtMost(100) / 100f) * maxVol).toInt(), 0)
                        showVolumeCircle = true
                    },
                    onPinchZoomPan = { zoom, pan ->
                        // Both zoom and pan applied together, matching how
                        // a real pinch/pan gesture always carries some of
                        // each — see PlayerGestureModifiers.kt for why.
                        videoScale = (videoScale * zoom).coerceIn(1f, 3f)
                        videoOffsetX += pan.x
                        videoOffsetY += pan.y
                        val maxOffsetX = (screenWidthPx * (videoScale - 1f) / 2f).coerceAtLeast(0f)
                        val maxOffsetY = (screenHeightPx * (videoScale - 1f) / 2f).coerceAtLeast(0f)
                        videoOffsetX = videoOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
                        videoOffsetY = videoOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                        if (videoScale <= 1.02f) {
                            videoScale = 1f
                            videoOffsetX = 0f
                            videoOffsetY = 0f
                        }
                    },
                )
        )

        LaunchedEffect(subtitleGestureFeedback) {
            if (subtitleGestureFeedback.isBlank()) return@LaunchedEffect
            delay(900)
            subtitleGestureFeedback = ""
        }

        // ── Subtitle gestures (opt-in, off by default) ──────────────────
        // Deliberately NOT pixel-tracking the subtitle's actual rendered
        // position (which depends on subtitleBottomPadding, itself
        // adjustable 0.02-0.90) — at low padding values that would sit
        // directly on top of the transport dock and seek bar, guaranteeing
        // touch conflicts with existing controls. Instead this is a FIXED
        // band positioned safely above the dock, spanning most of the
        // width but leaving generous margin so it doesn't compete with the
        // brightness/volume vertical-swipe zones on the far left/right
        // edges of the full screen. A deliberate simplification, not an
        // attempt at exact subtitle-position tracking.
        if (subtitleBehaviorPrefs.enableSubtitleGestures && subtitlesEnabled && !isStreamMedia) {
            val gestureZoneHeight = 110.dp
            val gestureZoneBottomOffset = bottomDockPadding + playButton + 26.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = gestureZoneBottomOffset, start = 48.dp, end = 48.dp)
                    .fillMaxWidth()
                    .height(gestureZoneHeight)
                    .subtitleGestureZone(
                        enabledKey = subtitleBehaviorPrefs.enableSubtitleGestures,
                        onPinchTextSize = { zoom ->
                            subtitleTextSizeSp = (subtitleTextSizeSp * zoom).coerceIn(12f, 32f)
                            subtitleGestureFeedback = "${subtitleTextSizeSp.toInt()}sp"
                        },
                        onHorizontalSyncDrag = { deltaX ->
                            // Positive (rightward) delay matches the same
                            // sign convention as the Sync slider elsewhere.
                            val deltaSeconds = deltaX / 60f
                            subtitleSyncOffset = (subtitleSyncOffset + deltaSeconds).coerceIn(-10f, 10f)
                            val formattedOffset = String.format("%.1f", subtitleSyncOffset)
                            subtitleGestureFeedback = if (subtitleSyncOffset >= 0f) "+${formattedOffset}s" else "${formattedOffset}s"
                        },
                        onVerticalPositionDrag = { deltaFraction ->
                            // Dragging UP raises the subtitle, which is a
                            // DECREASE in bottom-padding fraction — sign
                            // flip already applied by the caller.
                            subtitleBottomPadding = (subtitleBottomPadding + deltaFraction).coerceIn(0.02f, 0.90f)
                            subtitleGestureFeedback = "Position"
                        },
                        onDoubleTapResetSync = {
                            subtitleSyncOffset = 0f
                            subtitleGestureFeedback = "Sync reset"
                        },
                        onLongPressTogglePlayback = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        },
                    )
            )
            AnimatedVisibility(
                visible = subtitleGestureFeedback.isNotBlank(),
                enter = fadeIn(animationSpec = tween(100)), exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = gestureZoneBottomOffset + gestureZoneHeight / 2)
            ) {
                Text(
                    text = subtitleGestureFeedback, color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        AnimatedVisibility(visible = showBrightnessCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(if (isLandscape) Alignment.TopEnd else Alignment.CenterEnd).padding(top = if (isLandscape) 86.dp else 0.dp, end = 28.dp)) {
            VerticalBrightnessHud(value = brightnessPercent, size = hudSize)
        }
        AnimatedVisibility(visible = showVolumeCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(if (isLandscape) Alignment.TopStart else Alignment.CenterStart).padding(top = if (isLandscape) 86.dp else 0.dp, start = 28.dp)) {
            val volumeColor = when { volumePercent > 120 -> Color.Red; volumePercent > 90 -> Color(0xFFFF9800); else -> Color.White }
            FilledCircleHud(value = volumePercent, maxValue = 150, color = volumeColor, size = hudSize)
        }

        AnimatedVisibility(visible = edgeSwipeHint.isNotBlank(), enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(200)), modifier = Modifier.align(Alignment.Center)) {
            Text(text = edgeSwipeHint, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 20.dp, vertical = 10.dp))
        }

        // Glasses-connected indicator — brief confirmation toast-style pill,
        // same treatment as edgeSwipeHint above, shown once when an external
        // display connects (fades out on its own after ~2s).
        AnimatedVisibility(visible = showGlassesConnectedHint, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(250)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 90.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 16.dp, vertical = 9.dp)) {
                Icon(imageVector = Icons.Rounded.Tv, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(7.dp))
                Text(text = "External display connected — RayNeo subtitle profile", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(visible = showBufferingSpinner && playerErrorMessage == null, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(150)), modifier = Modifier.align(Alignment.Center)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(56.dp).glassPanel(cornerRadius = 28.dp, fill = GlassSurfaceStrong), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberCore, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
                if (stuckBufferingHint) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Taking longer than usual — slow drive or connection?",
                        color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 240.dp).glassPanel(cornerRadius = 14.dp, fill = GlassSurfaceStrong).padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = playerErrorMessage != null, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(150)), modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 320.dp).glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong).padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                Icon(imageVector = Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(34.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Playback Error", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = playerErrorMessage ?: "", color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Back", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onBack() }.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                    Text(
                        text = "Retry", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable {
                            errorRetryCount = 0
                            playCurrentVideoWithSubtitle(subtitleUri = originalSubtitleUri, resumePosition = position, isOriginalSubtitle = false)
                        }.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }

        if (sleepTimerActive && sleepTimerRemainingMs > 0) {
            val sleepMins = (sleepTimerRemainingMs / 60000).toInt()
            val sleepSecs = ((sleepTimerRemainingMs % 60000) / 1000).toInt()
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Rounded.Timer, contentDescription = null, tint = AmberCore, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = "%d:%02d".format(sleepMins, sleepSecs), color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        val clusterHeightDp = with(density) { clusterHeightPx.toDp() }
        val titleRowOffset = if (isLandscape) 0.dp else 46.dp
        AnimatedVisibility(visible = showSpeedMenu, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)), modifier = Modifier.align(Alignment.TopEnd).padding(top = topClusterPaddingTop + titleRowOffset + clusterHeightDp + 8.dp, end = sidePadding)) {
            SpeedMenuPopup(
                currentSpeed = playbackSpeed,
                popupWidth = smallMenuWidth,
                popupMaxHeight = smallMenuMaxHeight,
                onSpeedSelected = { setPlaybackSpeed(it) },
                onDismiss = { showSpeedMenu = false }
            )
        }

        AnimatedVisibility(visible = showSleepMenu, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)), modifier = Modifier.align(Alignment.TopEnd).padding(top = topClusterPaddingTop + titleRowOffset + clusterHeightDp + 8.dp, end = sidePadding)) {
            SleepMenuPopup(
                currentMinutes = sleepTimerMinutes,
                popupWidth = smallMenuWidth,
                popupMaxHeight = smallMenuMaxHeight,
                onSelected = { setSleepTimer(it) },
                onDismiss = { showSleepMenu = false }
            )
        }

        AnimatedVisibility(visible = showSrtBrowser, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = anchoredY(popupBottomPadding, srtPopupMaxHeight)).offset { IntOffset(anchoredX(subIconX, srtPopupWidth), 0) }) {
            val srtFiles = remember(currentVideo.path, showSrtBrowser) { findNearbySrtFiles(currentVideo.path) }
                .filter { it.absolutePath !in pendingDeletePaths }
            SrtBrowserPopup(
                files = srtFiles,
                modifier = Modifier,
                popupWidth = srtPopupWidth,
                popupMaxHeight = srtPopupMaxHeight,
                onPick = { file ->
                    showSrtBrowser = false
                    pendingSrtUri = Uri.fromFile(file)
                },
                onDelete = { file -> requestDeleteSubtitle(file) },
                onSystemPicker = {
                    showSrtBrowser = false
                    srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*"))
                },
                onClose = { showSrtBrowser = false; showControls = true }
            )
        }

        AnimatedVisibility(visible = showAudioSelector, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = popupBottomPadding).offset { IntOffset(anchoredX(audioIconX, audioPopupWidth), 0) }) {
            val audioTracks = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            FloatingTrackPopup(
                title = "Audio",
                modifier = Modifier.width(audioPopupWidth),
                audioSyncMs = audioSyncMs,
                onAudioSyncChange = { audioSyncMs = it; menuTouchKey++ },
                rows = audioTracks.flatMap { group ->
                    List(group.length) { i ->
                        val fmt = group.getTrackFormat(i); val lang = friendlyLanguageName(fmt.language)
                        TrackPopupRowData(title = if (lang == "Unknown" || lang == "UND") "Default Audio" else lang, subtitle = "Track ${i+1}", onClick = {
                            trackSelector.parameters = trackSelector.buildUponParameters().setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i))).build()
                            showAudioSelector = false; showControls = true
                        })
                    }
                },
                onAnyClick = { menuTouchKey++ },
                onClose = { showAudioSelector = false; showControls = true }
            )
        }

        val hasInternalSubtitles = exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }

        // ── Track Selector data, built fresh from live player + disk state
        // every time it's shown. Embedded tracks read straight off
        // ExoPlayer's current track groups (source of truth for what's
        // actually IN the file); downloaded/local read off disk the same
        // way the existing SRT browser and OpenSubtitlesClient cache
        // already do — no new scanning logic, just reused in one place.
        val embeddedTrackChoices = remember(exoPlayer.currentTracks) {
            exoPlayer.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT }
                .flatMapIndexed { groupIndex, group ->
                    (0 until group.length).map { trackIndexInGroup ->
                        val fmt = group.getTrackFormat(trackIndexInGroup)
                        SubtitleTrackChoice.Embedded(
                            groupIndex = groupIndex,
                            trackIndexInGroup = trackIndexInGroup,
                            language = fmt.language ?: "und",
                            isForced = (fmt.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
                            isSdh = (fmt.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0
                        )
                    }
                }
        }
        val downloadedTrackChoice = remember(currentVideo.path, showTrackSelector) {
            if (!canDownloadExternalSubtitles) null
            else OpenSubtitlesClient.findCachedSubtitle(context, currentVideo.path, subtitleBehaviorPrefs.preferredLanguages)?.let { cached ->
                cached.uri.path?.let { path -> SubtitleTrackChoice.Downloaded(file = java.io.File(path), language = cached.language) }
            }
        }
        val localFileChoices = remember(currentVideo.path, showTrackSelector) { findNearbySrtFiles(currentVideo.path) }
            .filter { it.absolutePath !in pendingDeletePaths }

        AnimatedVisibility(visible = showSubtitleSettings, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = anchoredY(popupBottomPadding, subtitlePopupHeightEstimate)).offset { IntOffset(anchoredX(subIconX, subtitlePopupWidth), 0) }) {
            val statusText = when {
                !subtitlesEnabled -> "Subtitles off"
                selectedSubtitleTrackLabel.isNotBlank() -> "$selectedSubtitleTrackLabel · $selectedSubtitleTrackSource"
                hasInternalSubtitles -> "Embedded track active"
                else -> "No subtitle selected"
            }
            SubtitleSettingsMenu(
                isVisible = true,
                subtitlesEnabled = subtitlesEnabled,
                activeTrackStatusText = statusText,
            onFindClick = {
                subtitleMenuTouchKey++
                showSubtitleSettings = false
                showSubtitleSearch = true
                showControls = true
                if (subtitleSearchResults.isEmpty() && !subtitleSearchLoading) {
                    performSubtitleSearch(OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path), "", "")
                }
            },
            onTracksClick = { showSubtitleSettings = false; showTrackSelector = true; showControls = true; subtitleMenuTouchKey++ },
            onToggleSubtitles = {
                subtitlesEnabled = !subtitlesEnabled
                trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
                if (!subtitlesEnabled) { selectedSubtitleTrackKey = "off"; selectedSubtitleTrackLabel = ""; selectedSubtitleTrackSource = "" }
                showControls = true; subtitleMenuTouchKey++
            },
            onDismiss = { showSubtitleSettings = false; showControls = true },
            currentFontSize = subtitleTextSizeSp, onFontSizeChange = { subtitleTextSizeSp = it; showControls = true; subtitleMenuTouchKey++ },
            currentVerticalPosition = subtitleBottomPadding, onVerticalPositionChange = { subtitleBottomPadding = it; showControls = true; subtitleMenuTouchKey++ },
            // New quick shortcut — jumps straight into the Studio's Timing
            // tool (no standalone equivalent exists for this one, unlike
            // Tracks/Style which reuse their existing standalone popups).
            onSyncClick = { showSubtitleSettings = false; subtitleStudioInitialTab = SubtitleStudioTab.TIMING; showSubtitleStudio = true; showControls = true },
            onStyleClick = { showSubtitleSettings = false; showAppearanceStudio = true; showControls = true },
            onUserInteraction = { subtitleMenuTouchKey++; showControls = true }
        )
        }

        // Track Selector sheet — anchored at the same spot the quick menu
        // uses, since it's opened from that menu's "Tracks" pill and should
        // feel like a continuation of it, not a jump to a new location.
        AnimatedVisibility(visible = showTrackSelector, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = anchoredY(popupBottomPadding, trackSelectorMaxHeight)).offset { IntOffset(anchoredX(subIconX, trackSelectorWidth), 0) }) {
            DraggableFloatingPopup(
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                popupWidth = trackSelectorWidth,
                popupMaxHeight = trackSelectorMaxHeight,
                onUserInteraction = { subtitleMenuTouchKey++ }
            ) {
                SubtitleTrackSelectorSheet(
                    embeddedTracks = embeddedTrackChoices,
                    downloadedTrack = downloadedTrackChoice,
                    localFiles = localFileChoices,
                    selectedKey = selectedSubtitleTrackKey,
                    popupWidth = trackSelectorWidth,
                    popupMaxHeight = trackSelectorMaxHeight,
                    onSelect = { choice ->
                        selectSubtitleTrack(choice)
                        showTrackSelector = false; showControls = true
                    },
                    onDeleteLocal = { file -> requestDeleteSubtitle(file) },
                    onOpenFilePicker = {
                        showTrackSelector = false
                        srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*"))
                    },
                    onDismiss = { showTrackSelector = false; showControls = true }
                )
            }
        }

        // Subtitle Download Search sheet — same anchor as the quick menu/
        // track selector since it's opened from the quick menu's Download
        // pill. Search results/loading state are owned by VideoPlayerScreen
        // (not the sheet itself) so a re-search after editing the query
        // doesn't lose state if the sheet briefly recomposes.
        AnimatedVisibility(visible = showSubtitleSearch, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.Center)) {
            // Previously reused trackSelectorWidth/Height (sized for a
            // simple list), which is why it looked cramped — Search has
            // meaningfully more content (query field, season/episode,
            // full result cards) so it gets its own larger sizing, same
            // adaptive approach as Studio above. Centered rather than
            // icon-anchored since the main controls are hidden behind it
            // now anyway (see hideControlsForLargeSheet), so there's no
            // need to dodge the subtitle icon's position anymore.
            // FIX (D2): caps raised from the old single-column sizing
            // (480dp landscape / 400dp portrait) — now holding two
            // side-by-side columns, each needs enough room for a full
            // result card (badges, Apply/Save buttons) without feeling
            // cramped, especially on a tablet where there's real space to
            // use.
            val searchWidth = if (isLandscape) (maxWidth.value * 0.72f).dp.coerceIn(320.dp, 620.dp)
                else (maxWidth.value * 0.94f).dp.coerceAtMost(480.dp)
            val searchMaxHeight = if (isCompactLandscape) (maxHeight.value * 0.76f).dp.coerceAtMost(280.dp)
                else if (isLandscape) (maxHeight.value * 0.78f).dp.coerceAtMost(360.dp)
                else (maxHeight.value * 0.65f).dp.coerceAtMost(580.dp)
            SubtitleSearchSheet(
                initialQuery = remember(currentVideo.path) { OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path) },
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                onUserInteraction = { subtitleMenuTouchKey++ },
                initialSeason = "",
                initialEpisode = "",
                results = subtitleSearchResults,
                isSearching = subtitleSearchLoading,
                statusText = subtitleSearchStatus,
                popupWidth = searchWidth,
                popupMaxHeight = searchMaxHeight,
                onSearch = { q, s, e -> subtitleMenuTouchKey++; performSubtitleSearch(q, s, e) },
                onDownloadAndApply = { result -> subtitleMenuTouchKey++; applySearchResult(result, alsoPlay = true) },
                onDownloadOnly = { result -> subtitleMenuTouchKey++; applySearchResult(result, alsoPlay = false) },
                onWebsiteFallback = {
                    showSubtitleSearch = false
                    showSubtitleFallback = true
                    showControls = true
                },
                onDismiss = { showSubtitleSearch = false; showControls = true }
            )
        }

        // Website fallback — last resort when automatic search/download
        // fails, hits a daily quota, or just doesn't have the release.
        // Custom Tab is the default/recommended route; embedded browser is
        // clearly marked experimental. See SubtitleWebFallback.kt.
        if (showSubtitleFallback) {
            val webQuery = OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path)
            SubtitleFallbackSheet(
                searchQuery = webQuery,
                statusText = subtitleSearchStatus,
                onSecureBrowser = {
                    exoPlayer.pause()
                    launchSubtitleCustomTab(context, webQuery)
                    showSubtitleFallback = false
                    Toast.makeText(context, "After downloading, return and choose Import downloaded subtitle", Toast.LENGTH_LONG).show()
                },
                onEmbeddedBrowser = {
                    exoPlayer.pause()
                    showSubtitleFallback = false
                    showEmbeddedSubtitleBrowser = true
                },
                onImportFile = {
                    // FIX: this was the only one of the three fallback
                    // options that didn't pause first — onSecureBrowser
                    // and onEmbeddedBrowser both do. Opening the system
                    // file picker backgrounds the whole Activity, and
                    // without an explicit pause here, ExoPlayer just kept
                    // playing invisibly behind it since nothing told it to
                    // stop for this specific transition.
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
                onDismiss = { showSubtitleFallback = false }
            )
        }

        if (showEmbeddedSubtitleBrowser) {
            EmbeddedSubtitleBrowser(
                query = OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path),
                preferredLanguage = subtitleBehaviorPrefs.preferredLanguages.firstOrNull() ?: "en",
                onImported = { result ->
                    if (result.alternatives.isEmpty()) {
                        applyImportedWebsiteSubtitle(result.selected)
                    } else {
                        pendingImportedCandidates = result
                        showEmbeddedSubtitleBrowser = false
                    }
                },
                onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                onDismiss = { showEmbeddedSubtitleBrowser = false; showControls = true }
            )
        }

        pendingImportedCandidates?.let { result ->
            SubtitleCandidateSheet(
                primary = result.selected,
                alternatives = result.alternatives,
                onSelected = { applyImportedWebsiteSubtitle(it) },
                onDismiss = { pendingImportedCandidates = null }
            )
        }

        // Dialogue Tap Sync — floating pill, visible over the video (not
        // anchored to the subtitle icon like the menus, since the whole
        // point is the person is watching the scene play out while this is
        // up) armed via the Sync section's "Tap Sync" button.
        AnimatedVisibility(visible = dialogueSyncArmed, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 90.dp)) {
            DialogueTapSyncBar(isLandscape = isLandscape, onTap = { confirmDialogueSyncTap() }, onCancel = { cancelDialogueSync() })
        }

        AnimatedVisibility(visible = showDriftDialog, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)), modifier = Modifier.align(Alignment.Center)) {
            DriftCorrectionSheet(
                videoDurationMs = duration,
                currentPositionMs = position,
                pointA = driftPointA,
                pointB = driftPointB,
                popupWidth = trackSelectorWidth.coerceAtLeast(220.dp),
                onMarkPointA = { correction -> markDriftPointA(correction) },
                onMarkPointB = { correction -> markDriftPointB(correction) },
                onApply = { applyDriftFix() },
                onDismiss = { showDriftDialog = false; showControls = true }
            )
        }

        AnimatedVisibility(visible = showAppearanceStudio, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = anchoredY(popupBottomPadding, trackSelectorMaxHeight)).offset { IntOffset(anchoredX(subIconX, trackSelectorWidth), 0) }) {
            DraggableFloatingPopup(
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                popupWidth = trackSelectorWidth,
                popupMaxHeight = trackSelectorMaxHeight,
                onUserInteraction = { subtitleMenuTouchKey++ }
            ) {
                SubtitleAppearanceStudioSheet(
                    presetName = subtitleAppearancePreset,
                    appearance = subtitleAppearance,
                    fontSizeSp = subtitleTextSizeSp,
                    popupWidth = trackSelectorWidth,
                    popupMaxHeight = trackSelectorMaxHeight,
                    onApplyPreset = { name, preset -> subtitleAppearancePreset = name; subtitleAppearance = preset },
                    onForegroundChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(foregroundColor = c) },
                    onEdgeTypeChange = { t -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(edgeType = t) },
                    onEdgeColorChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(edgeColor = c) },
                    onBackgroundChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(backgroundColor = c) },
                    isAssOrSsaFormat = isAssOrSsaFormat,
                    preserveOriginalStyling = subtitlePreserveOriginalStyling,
                    onPreserveOriginalStylingChange = { subtitlePreserveOriginalStyling = it },
                    onDismiss = { showAppearanceStudio = false; showControls = true }
                )
            }
        }

        // Subtitle Studio — the "everything in one place" destination,
        // reachable via long-press on the subtitle icon (opens the Tools
        // Grid) or a quick-menu shortcut icon (jumps straight into a
        // specific tool). Centered and much larger than the anchored
        // popups above since it's a genuine destination, not a quick
        // action. Reuses the exact same track/style tab content as the
        // standalone sheets (see SubtitleStudioSheet.kt) so there's no
        // parallel implementation to drift out of sync.
        AnimatedVisibility(visible = showSubtitleStudio, enter = fadeIn(animationSpec = tween(180)), exit = fadeOut(animationSpec = tween(200)), modifier = Modifier.align(Alignment.Center)) {
            // Device-adaptive sizing — previously fillMaxWidth(0.94f)/
            // fillMaxHeight(0.82f), which is near-fullscreen on any phone
            // regardless of actual screen size. Uses the same
            // isLandscape/isCompactLandscape/uiScale signals every other
            // popup on this screen already computes, so Studio scales down
            // properly on a phone instead of covering nearly everything.
            val studioWidth = if (isLandscape) (maxWidth.value * 0.58f).dp.coerceIn(340.dp, 520.dp)
                else (maxWidth.value * 0.90f).dp.coerceAtMost(420.dp)
            val studioMaxHeight = if (isCompactLandscape) (maxHeight.value * 0.76f).dp.coerceAtMost(280.dp)
                else if (isLandscape) (maxHeight.value * 0.78f).dp.coerceAtMost(360.dp)
                else (maxHeight.value * 0.60f).dp.coerceAtMost(560.dp)
            SubtitleStudioSheet(
                panelWidth = studioWidth,
                panelMaxHeight = studioMaxHeight,
                containerWidth = maxWidth,
                containerHeight = maxHeight,
                initialTab = subtitleStudioInitialTab,
                videoPath = currentVideo.path,
                onOpenSearch = {
                    showSubtitleStudio = false
                    showSubtitleSearch = true
                    showControls = true
                    if (subtitleSearchResults.isEmpty() && !subtitleSearchLoading) {
                        performSubtitleSearch(OpenSubtitlesClient.cleanMovieNamePublic(currentVideo.path), "", "")
                    }
                },
                onOpenManualSearch = {
                    showSubtitleStudio = false
                    showSubtitleFallback = true
                    showControls = true
                },
                embeddedTracks = embeddedTrackChoices,
                downloadedTrack = downloadedTrackChoice,
                localFiles = localFileChoices,
                selectedTrackKey = selectedSubtitleTrackKey,
                onSelectTrack = { choice -> selectSubtitleTrack(choice) },
                onDeleteLocalTrack = { file -> requestDeleteSubtitle(file) },
                onOpenFilePicker = { srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
                currentSyncOffset = subtitleSyncOffset,
                onSyncOffsetChange = { subtitleSyncOffset = it; subtitleMenuTouchKey++ },
                onDialogueSyncClick = { armDialogueSync() },
                onDriftFixClick = { showSubtitleStudio = false; showDriftDialog = true },
                autoSyncStatus = autoSyncStatus,
                autoSyncAvailable = autoSyncAvailable,
                onAutoSyncClick = { runAutoSync() },
                onApplyAutoSync = { result -> applyAutoSyncResult(result) },
                onCancelAutoSync = { autoSyncStatus = AutoSyncStatus.Idle },
                presetName = subtitleAppearancePreset,
                appearance = subtitleAppearance,
                fontSizeSp = subtitleTextSizeSp,
                onFontSizeChange = { subtitleTextSizeSp = it },
                onApplyPreset = { name, preset -> subtitleAppearancePreset = name; subtitleAppearance = preset },
                onForegroundChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(foregroundColor = c) },
                onEdgeTypeChange = { t -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(edgeType = t) },
                onEdgeColorChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(edgeColor = c) },
                onBackgroundChange = { c -> subtitleAppearancePreset = "Custom"; subtitleAppearance = subtitleAppearance.copy(backgroundColor = c) },
                isAssOrSsaFormat = isAssOrSsaFormat,
                preserveOriginalStyling = subtitlePreserveOriginalStyling,
                onPreserveOriginalStylingChange = { subtitlePreserveOriginalStyling = it },
                bottomPadding = subtitleBottomPadding,
                onBottomPaddingChange = { subtitleBottomPadding = it },
                behaviorPrefs = subtitleBehaviorPrefs,
                onBehaviorPrefsChange = { subtitleBehaviorPrefs = it; saveSubtitleBehaviorPrefs(context, it) },
                cleaningOptions = subtitleCleaningOptions,
                onCleaningOptionsChange = { subtitleCleaningOptions = it; saveSubtitleCleaningOptions(context, it) },
                dualSubtitlesEnabled = dualSubtitlesEnabled,
                dualCanEnable = primarySubtitleUri != null,
                dualSecondaryLanguage = dualSecondaryLanguage,
                dualGapLines = dualGapLines,
                dualStatusText = dualStatusText,
                onToggleDual = { enabled ->
                    dualSubtitlesEnabled = enabled
                    if (enabled) fetchAndApplyDualSecondary() else disableDualSubtitles()
                },
                onDualSecondaryLanguageChange = { lang ->
                    dualSecondaryLanguage = lang
                    if (dualSubtitlesEnabled) fetchAndApplyDualSecondary()
                },
                onDualGapLinesChange = { gap ->
                    dualGapLines = gap
                    if (dualSubtitlesEnabled) fetchAndApplyDualSecondary()
                },
                onDismiss = { showSubtitleStudio = false; showControls = true },
                onUserInteraction = { subtitleMenuTouchKey++ }
            )
        }

        // Studio and Search are large, self-contained sheets that cover
        // most of the screen — showing the transport dock/seek bar/top
        // cluster underneath them just doubles up the UI for no reason
        // (confirmed on-device: Vivo X300 Pro screenshots showed both
        // layers competing for the same space). Both are explicitly
        // EXCLUDED from the trigger list and explicitly HIDE this whole
        // block via the trailing && clause, unlike the smaller anchored
        // popups (Track Selector, Drift, Appearance, quick menu) which
        // were designed to sit alongside visible controls and still do.
        val hideControlsForLargeSheet = showSubtitleStudio || showSubtitleSearch
        AnimatedVisibility(visible = (showControls || isDraggingSeekbar || showAudioSelector || showSubtitleSettings || showTrackSelector || showDriftDialog || showAppearanceStudio || dialogueSyncArmed || showSpeedMenu || showSleepMenu) && !hideControlsForLargeSheet, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {

                val topRowVisible = !showSeekPreview
                if (isLandscape) {
                    Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = topClusterPaddingTop)) {
                        AnimatedVisibility(
                            visible = topRowVisible,
                            enter = fadeIn(animationSpec = tween(160)), exit = fadeOut(animationSpec = tween(120)),
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = sidePadding)
                        ) {
                            FloatingScoreCapsule(meta = currentMeta, vertical = false)
                        }

                        AnimatedVisibility(
                            visible = topRowVisible,
                            enter = fadeIn(animationSpec = tween(220)), exit = fadeOut(animationSpec = tween(160)),
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
                        ) {
                            NowPlayingTitlePill(text = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path), fontSize = 13.sp)
                        }

                        TopIconCluster(
                            isLandscape = true, iconSize = topIconSize,
                            playbackSpeed = playbackSpeed, sleepTimerActive = sleepTimerActive,
                            showSpeedMenu = showSpeedMenu, showSleepMenu = showSleepMenu,
                            onSpeedClick = { val wasOpen = showSpeedMenu; closeAllMenus(); showSpeedMenu = !wasOpen; showControls = true },
                            onSleepClick = { val wasOpen = showSleepMenu; closeAllMenus(); showSleepMenu = !wasOpen; showControls = true },
                            onPipClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val actions = buildPipActions(context, exoPlayer.isPlaying)
                                    activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions).build())
                                }
                            },
                            // FIX: extra end padding reserves space for the
                            // always-on-top lock button (TopEnd, last child
                            // in the outer Box so it draws above everything
                            // including this cluster) — without this, the
                            // Speed icon's position could sit directly under
                            // the lock button's touch/paint area in
                            // landscape, since Compose doesn't auto-avoid
                            // overlap between independently-aligned
                            // siblings. Portrait doesn't need this: the
                            // title pill + spacer above already push this
                            // Column well clear of the lock button's corner.
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = sidePadding + 62.dp)
                                .onGloballyPositioned { clusterHeightPx = it.size.height.toFloat() }
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = topClusterPaddingTop)) {
                        AnimatedVisibility(
                            visible = topRowVisible,
                            enter = fadeIn(animationSpec = tween(220)), exit = fadeOut(animationSpec = tween(160)),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 72.dp)
                        ) {
                            NowPlayingTitlePill(text = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path), fontSize = 15.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = topRowVisible,
                                enter = fadeIn(animationSpec = tween(160)), exit = fadeOut(animationSpec = tween(120)),
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = sidePadding)
                            ) {
                                FloatingScoreCapsule(meta = currentMeta, vertical = true)
                            }

                            TopIconCluster(
                                isLandscape = false, iconSize = topIconSize,
                                playbackSpeed = playbackSpeed, sleepTimerActive = sleepTimerActive,
                                showSpeedMenu = showSpeedMenu, showSleepMenu = showSleepMenu,
                                onSpeedClick = { val wasOpen = showSpeedMenu; closeAllMenus(); showSpeedMenu = !wasOpen; showControls = true },
                                onSleepClick = { val wasOpen = showSleepMenu; closeAllMenus(); showSleepMenu = !wasOpen; showControls = true },
                                onPipClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val actions = buildPipActions(context, exoPlayer.isPlaying)
                                        activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions).build())
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = sidePadding)
                                    .onGloballyPositioned { clusterHeightPx = it.size.height.toFloat() }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = autoSubtitleStatus.isNotBlank() && !showSeekPreview, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 86.dp).padding(horizontal = 24.dp)) {
                    Text(
                        text = autoSubtitleStatus, color = AmberCore, fontSize = if (isLandscape) 11.sp else 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceStrong).widthIn(max = if (isLandscape) 320.dp else 300.dp).padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                AnimatedVisibility(visible = showNextEpisodeOverlay && pendingNextEpisode != null && !showSeekPreview, enter = fadeIn(animationSpec = tween(140)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.Center)) {
                    NextEpisodeCountdownOverlay(nextEpisode = pendingNextEpisode, countdown = nextEpisodeCountdown, isLandscape = isLandscape,
                        onPlayNow = { val n = pendingNextEpisode; if (n != null) { showNextEpisodeOverlay = false; pendingNextEpisode = null; currentMediaType = n.type; currentVideo = n.video; onPlayNext(n) } },
                        onCancel = { showNextEpisodeOverlay = false; pendingNextEpisode = null; nextEpisodeCountdown = 0; showControls = true }
                    )
                }

                val anyMenuOpenForIntroSkip = showAudioSelector || showSubtitleSettings || showTrackSelector || showSubtitleSearch || showDriftDialog || showAppearanceStudio || showSubtitleStudio || dialogueSyncArmed || showSpeedMenu || showSleepMenu || showSrtBrowser
                AnimatedVisibility(visible = showIntroSkip && !showSeekPreview && !isDraggingSeekbar && !anyMenuOpenForIntroSkip, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.CenterEnd).padding(end = sidePadding)) {
                    SkipIntroButton(isLandscape = isLandscape) { val t = 95_000L.coerceAtMost(duration.coerceAtLeast(1L)); exoPlayer.seekTo(t); position = t; showControls = true }
                }

                AnimatedVisibility(visible = isZoomMode && !showSeekPreview, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 90.dp)) {
                    Text(text = "⛶  Fill", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 12.dp, vertical = 6.dp))
                }

                AnimatedVisibility(
                    visible = !showSeekPreview && !isDraggingSeekbar,
                    enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(180)) + fadeOut(animationSpec = tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = bottomDockPadding, start = sidePadding, end = sidePadding)
                            .glassPanel(cornerRadius = 42.dp, fill = GlassSurfaceStrong)
                            .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy((7 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackIconButton(size = smallButton, onClick = onBack)

                        GlassTransportButton(icon = Icons.Rounded.Replay10, size = smallButton) { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)); position = exoPlayer.currentPosition; showControls = true }

                        FrostedPlayButton(isPlaying = isPlaying, isEnded = isVideoEnded, size = playButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isVideoEnded) { exoPlayer.seekTo(0); exoPlayer.play(); isVideoEnded = false; showControls = true }
                            else { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); showControls = true }
                        }

                        GlassTransportButton(icon = Icons.Rounded.Forward10, size = smallButton) { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))); position = exoPlayer.currentPosition; showControls = true }

                        // Next — same visibility/dim logic as Previous above.
                        if (showPrevNextButtons) {
                            IconCircle(
                                icon = Icons.Rounded.SkipNext, size = smallButton,
                                tint = if (hasNextVideo) TextBright else TextMuted.copy(alpha = 0.35f)
                            ) {
                                if (hasNextVideo) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    playNext()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width((4 * scale).dp))

                        IconCircle(icon = Icons.Rounded.AllInclusive, size = smallButton, tint = if (autoPlayEnabled) AmberCore else TextMuted.copy(alpha = 0.6f)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            autoPlayEnabled = !autoPlayEnabled; showControls = true
                            Toast.makeText(context, if (autoPlayEnabled) "Autoplay on" else "Autoplay off", Toast.LENGTH_SHORT).show()
                        }

                        IconCircle(icon = Icons.Rounded.Audiotrack, size = smallButton, tint = if (showAudioSelector) AmberCore else TextBright, modifier = Modifier.onGloballyPositioned { audioIconX = it.positionInRoot().x + it.size.width / 2f }) {
                            val wasOpen = showAudioSelector; closeAllMenus(); showAudioSelector = !wasOpen; showControls = true; menuTouchKey++
                        }

                        if (!isStreamMedia) {
                            Box(
                                modifier = Modifier
                                    .size(smallButton)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GlassSurface)
                                    .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
                                    .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
                                    .onGloballyPositioned { subIconX = it.positionInRoot().x + it.size.width / 2f }
                                    .combinedClickable(
                                        onClick = {
                                            val wasOpen = showSubtitleSettings || showTrackSelector || showSubtitleSearch || showDriftDialog || showAppearanceStudio || showSubtitleStudio
                                            closeAllMenus(); showSubtitleSettings = !wasOpen; showControls = true; menuTouchKey++
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            closeAllMenus(); subtitleStudioInitialTab = null; showSubtitleStudio = true; showControls = true
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ClosedCaption, contentDescription = null,
                                    tint = if (showSubtitleSettings || showTrackSelector || showSubtitleSearch || showDriftDialog || showAppearanceStudio || showSubtitleStudio) AmberCore else TextBright,
                                    modifier = Modifier.size(smallButton * 0.44f)
                                )
                            }
                        }
                    }
                }

                SeekPreviewBubble(isVisible = showSeekPreview, bitmap = previewBitmap, timeText = formatTime(previewPosition), isLandscape = isLandscape, isLarge = isSeekPreviewLarge, progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f))

                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = sidePadding, end = sidePadding, bottom = seekBottomPadding)
                        .glassPanel(cornerRadius = 30.dp, fill = GlassSurface)
                        .padding(horizontal = (14 * scale).dp, vertical = (7 * scale).dp)
                ) {
                    CinematicSeekBar(
                        position = position, duration = duration, isDragging = isDraggingSeekbar,
                        seed = currentVideo.path.hashCode(),
                        onPreviewPositionChanged = { pos ->
                            isDraggingSeekbar = true; showSeekPreview = true; showControls = true; showTopBar = true
                            position = pos.coerceIn(0L, duration); previewPosition = position
                            VideoThumbnailHelper.nearestPreviewFrame(previewFrames, previewPosition)?.let { previewBitmap = it }
                        },
                        onSeekFinished = { finalPos ->
                            val safe = finalPos.coerceIn(0L, duration)
                            position = safe; previewPosition = safe; exoPlayer.seekTo(safe); isDraggingSeekbar = false
                            previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(previewFrames, safe) ?: previewBitmap
                            showSeekPreview = true
                            if (isStreamMedia) { scope.launch { delay(360); if (!isDraggingSeekbar) showSeekPreview = false } }
                            else { scope.launch { val bmp = VideoThumbnailHelper.generateFrameAtTime(context, currentVideo.path, safe); if (bmp != null && previewPosition == safe) previewBitmap = bmp; delay(620); if (previewPosition == safe && !isDraggingSeekbar) showSeekPreview = false } }
                            showControls = true; showTopBar = true
                        }
                    )
                }
            }
        }

        // FIX (E3): controls lock. Placed as the LAST child of the outer
        // Box specifically — Compose renders later children on top, so
        // this genuinely sits above every popup, menu, and the transport
        // controls, not just visually but for touch priority too. When
        // locked, a full-screen absorber (same containment pattern as the
        // A1 fix) swallows every touch before it reaches anything else;
        // the unlock button itself is a separate, later sibling so it
        // stays reachable through the absorber rather than being blocked
        // by its own lock.
        if (controlsLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { } }
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (isLandscape) 10.dp else 14.dp, end = 14.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(AmberCore)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controlsLocked = !controlsLocked
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (controlsLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (controlsLocked) "Unlock controls" else "Lock controls",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }

        // ── Delete confirmation dialog (styled to match the app, not the
        // plain white Android AlertDialog) ─────────────────────────────
        AnimatedVisibility(
            visible = pendingDeleteConfirmFile != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
        pendingDeleteConfirmFile?.let { file ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { pendingDeleteConfirmFile = null } },
                contentAlignment = Alignment.Center
            ) {
                // Sheet slides up + fades in on top of the scrim's plain
                // fade — same "arriving from below" language as the
                // context sheets on the Library/TV Show screens.
                AnimatedVisibility(
                    visible = pendingDeleteConfirmFile != null,
                    enter = slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(180)) + fadeOut(tween(140))
                ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Delete subtitle file?", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = file.name, color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Cancel", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))
                                .clickable { pendingDeleteConfirmFile = null }.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        Text(
                            text = "Delete", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore)
                                .clickable {
                                    pendingDeleteConfirmFile = null
                                    deleteWithUndo(file)
                                }.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
                }
            }
        }
        }

        // ── Undo snackbar — styled the same as the app's other floating
        // pills (glassPanel + amber action text) rather than Material's
        // default snackbar look. Positioned above the transport dock so
        // it never fights the dock for touch space.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomDockPadding + playButton + 26.dp)
        ) { data ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(text = data.visuals.message, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                data.visuals.actionLabel?.let { label ->
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label, color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { data.performAction() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopIconCluster(
    isLandscape: Boolean,
    iconSize: Dp,
    playbackSpeed: Float,
    sleepTimerActive: Boolean,
    showSpeedMenu: Boolean,
    showSleepMenu: Boolean,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit,
    onPipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content: @Composable () -> Unit = {
        LabeledGlowIcon(icon = Icons.Rounded.Tv, label = "PiP", size = iconSize, tint = TextBright, onClick = onPipClick)
        LabeledGlowIcon(icon = Icons.Rounded.Timer, label = "Sleep", size = iconSize, tint = if (sleepTimerActive || showSleepMenu) AmberCore else TextBright, active = sleepTimerActive || showSleepMenu, onClick = onSleepClick)
        LabeledGlowIcon(icon = Icons.Rounded.Speed, label = "Speed", size = iconSize, tint = if (playbackSpeed != 1f || showSpeedMenu) AmberCore else TextBright, active = playbackSpeed != 1f || showSpeedMenu, onClick = onSpeedClick)
    }
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) { content() }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

// Icon + text label, with a persistent amber-glass glow border (same
// visual language as the always-visible lock button, just a border/glow
// treatment rather than a solid fill so active/inactive state — e.g.
// playback speed != 1x, sleep timer running — still reads clearly through
// tint and glow intensity). Labels sit below the icon so PiP/Sleep/Speed
// don't need to be guessed from silhouette alone.
@Composable
private fun LabeledGlowIcon(icon: ImageVector, label: String, size: Dp, tint: Color = TextBright, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(20.dp))
                .background(GlassSurface)
                .background(Brush.radialGradient(listOf(AmberGlow.copy(alpha = if (active) 0.38f else 0.20f), Color.Transparent)))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = if (active) 0.90f else 0.55f), AmberDeep.copy(alpha = if (active) 0.55f else 0.25f))),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.44f))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = if (active) AmberCore else TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassMenuRow(icon: ImageVector?, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))),
                    shape = shape
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = label,
            color = if (selected) AmberCore else TextBright,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// Extensions the local-subtitle browser/matcher recognizes — matches
// SubtitleFormatSupport.kt's SRT/VTT/ASS/SSA/TTML set (VobSub .idx and
// MicroDVD .sub deliberately excluded since CineVault can't decode either;
// see that file for why).
private val SUPPORTED_LOCAL_SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml")

private fun findNearbySrtFiles(videoPath: String): List<java.io.File> {
    val results = LinkedHashSet<java.io.File>()
    try {
        val videoFile = java.io.File(videoPath)
        val folder = videoFile.parentFile
        val base = videoFile.nameWithoutExtension.lowercase()
        val dirs = listOfNotNull(
            folder,
            folder?.let { java.io.File(it, "Subs") },
            folder?.let { java.io.File(it, "Subtitles") },
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        )
        for (dir in dirs) {
            if (!dir.isDirectory) continue
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_LOCAL_SUBTITLE_EXTENSIONS }
                ?.sortedByDescending { it.nameWithoutExtension.lowercase().contains(base) }
                ?.take(25)
                ?.forEach { results.add(it) }
            if (results.size >= 40) break
        }
    } catch (_: Exception) {}
    return results.toList()
}

// FIX (tolerant parser round): accepts both comma (spec-correct SRT) and
// dot (common in files converted from VTT, which uses dots) as the
// milliseconds separator. Previously comma-only, so a dot-decimal file's
// timestamps simply never matched this regex at all — sync/drift
// adjustment silently did nothing rather than failing loudly. Output is
// unaffected either way: shiftSrtTimestampMatch() below regenerates the
// whole matched substring from scratch using comma, so a dot-decimal
// input file gets normalized to spec-correct comma on its first shift,
// which is a strict improvement, not a behavior change to guard against.
private val SRT_TIME_REGEX = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) { null }
}

private fun shiftSrtTimestampMatch(match: MatchResult, offsetMs: Long, scale: Float = 1f): String {
    val h = match.groupValues[1].toLong()
    val m = match.groupValues[2].toLong()
    val s = match.groupValues[3].toLong()
    val ms = match.groupValues[4].toLong()
    val originalMs = (h * 3_600_000L) + (m * 60_000L) + (s * 1_000L) + ms
    var totalMs = (originalMs * scale).toLong() + offsetMs
    if (totalMs < 0L) totalMs = 0L
    val newH = totalMs / 3_600_000L
    val newM = (totalMs % 3_600_000L) / 60_000L
    val newS = (totalMs % 60_000L) / 1_000L
    val newMs = totalMs % 1_000L
    return "%02d:%02d:%02d,%03d".format(newH, newM, newS, newMs)
}

private fun buildShiftedSubtitleFile(context: Context, sourceUri: Uri, offsetMs: Long, scale: Float = 1f): Uri? {
    if (offsetMs == 0L && scale == 1f) return sourceUri
    // Only SRT's specific timing syntax is understood by shiftSrtTimestampMatch
    // below — for any other format, sync/drift adjustment is a no-op rather
    // than a silent corruption risk. See SubtitleFormatSupport.kt.
    if (!supportsCustomTextPipeline(detectSubtitleFormat(sourceUri))) return sourceUri
    val original = readTextFromUri(context, sourceUri) ?: return null
    val shifted = SRT_TIME_REGEX.replace(original) { shiftSrtTimestampMatch(it, offsetMs, scale) }
    return try {
        // FIX: was a single fixed filename ("cinevault_synced_subtitle.srt")
        // shared by EVERY video and EVERY offset/drift value — rapid sync
        // adjustments or overlapping coroutines could have one request's
        // write clobber a file ExoPlayer was still actively reading from a
        // different request. Now unique per source file + exact transform
        // parameters, so two different requests can never collide.
        val uniqueName = "cinevault_synced_${sourceUri.hashCode()}_${offsetMs}_${scale.hashCode()}.srt"
        val outFile = java.io.File(context.cacheDir, uniqueName)
        outFile.writeText(shifted)
        Uri.fromFile(outFile)
    } catch (e: Exception) { null }
}

// Cleaning is applied BEFORE sync/drift shifting whenever a subtitle first
// becomes the active one — since cleaning only ever touches text lines and
// shifting only ever touches timestamp lines, running clean-then-shift (in
// that order, on the cleaned file's own timestamps) composes correctly
// regardless of what order the person actually adjusts settings in.
private fun buildCleanedSubtitleFile(context: Context, sourceUri: Uri, options: SubtitleCleaningOptions): Uri? {
    if (!options.isAnyEnabled) return sourceUri
    if (!supportsCustomTextPipeline(detectSubtitleFormat(sourceUri))) return sourceUri
    val original = readTextFromUri(context, sourceUri) ?: return null
    val cleaned = cleanSrtText(original, options)
    return try {
        // FIX: same shared-fixed-filename race as the sync file above —
        // now unique per source file + the exact cleaning options applied.
        val uniqueName = "cinevault_cleaned_${sourceUri.hashCode()}_${options.hashCode()}.srt"
        val outFile = java.io.File(context.cacheDir, uniqueName)
        outFile.writeText(cleaned)
        Uri.fromFile(outFile)
    } catch (e: Exception) { null }
}

// Given two (position, correction) reference points, derives the linear
// scale + shift that makes both points land exactly on their intended
// corrected time — the math behind "Fix Gradual Drift". Point A is assumed
// to be earlier in the video than Point B; if they're passed in reverse
// order this still works since it solves the two-point line algebraically
// rather than assuming an order.
private fun computeDriftTransform(pointA: DriftPoint, pointB: DriftPoint): Pair<Float, Long> {
    val t1 = pointA.positionMs.toDouble()
    val t2 = pointB.positionMs.toDouble()
    val c1 = t1 + pointA.correctionSeconds * 1000.0
    val c2 = t2 + pointB.correctionSeconds * 1000.0
    if (t2 == t1) return 1f to 0L
    val scale = (c2 - c1) / (t2 - t1)
    val shift = c1 - scale * t1
    return scale.toFloat() to shift.toLong()
}

@Composable
private fun SrtBrowserPopup(
    files: List<java.io.File>,
    modifier: Modifier,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onPick: (java.io.File) -> Unit,
    onDelete: (java.io.File) -> Unit,
    onSystemPicker: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 18.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(8.dp)) {
        Text(text = "Subtitle Files", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        if (files.isEmpty()) {
            Text(
                text = "No .srt files found near this video",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        } else {
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            GlassMenuRow(icon = Icons.Rounded.ClosedCaption, label = file.name, selected = false, onClick = { onPick(file) })
                        }
                        IconButton(onClick = { onDelete(file) }, modifier = Modifier.size(30.dp)) {
                            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete subtitle file", tint = TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        GlassMenuRow(icon = null, label = "System file picker…", selected = false, onClick = onSystemPicker)
        Spacer(modifier = Modifier.height(4.dp))
        GlassMenuRow(icon = null, label = "Close", selected = false, onClick = onClose)
    }
}

@Composable
private fun SpeedMenuPopup(currentSpeed: Float, popupWidth: Dp, popupMaxHeight: Dp, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Column(modifier = Modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(5.dp).verticalScroll(rememberScrollState())) {
        Text(text = "Speed", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        speeds.forEach { speed ->
            CompactSelectableRow(
                label = if (speed == 1.0f) "1x Normal" else "${speed}x",
                selected = speed == currentSpeed,
                onClick = { onSpeedSelected(speed) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun SleepMenuPopup(currentMinutes: Int, popupWidth: Dp, popupMaxHeight: Dp, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min")
    Column(modifier = Modifier.width(popupWidth).heightIn(max = popupMaxHeight).glassPanel(cornerRadius = 13.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(5.dp).verticalScroll(rememberScrollState())) {
        Text(text = "Sleep Timer", color = AmberCore, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        options.forEach { (mins, label) ->
            CompactSelectableRow(
                label = label,
                selected = mins == currentMinutes,
                onClick = { onSelected(mins) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CompactSelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(width = 1.dp, brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.85f), AmberDeep.copy(alpha = 0.35f))), shape = shape) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = if (selected) AmberCore else TextBright, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun BackIconButton(size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to AmberGlow.copy(alpha = 0.10f), 1f to Color.Transparent))
        .border(1.2.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.55f), AmberDeep.copy(alpha = 0.25f))), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = AmberCore, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
private fun GlassTransportButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = TextBright, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun FrostedPlayButton(isPlaying: Boolean, isEnded: Boolean, size: Dp, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "playGlow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "playGlowAlpha"
    )
    val density = LocalDensity.current
    val glowRadiusPx = with(density) { (size / 2f * 1.05f).toPx() }.coerceAtLeast(1f)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassSurfaceStrong)
            .background(Brush.verticalGradient(0f to GlassHighlight, 0.45f to Color.Transparent, 1f to Color.Transparent))
            .background(Brush.radialGradient(colors = listOf(AmberGlow.copy(alpha = glowAlpha * 0.55f), Color.Transparent), radius = glowRadiusPx))
            .border(
                width = 1.4.dp,
                brush = Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.75f + 0.2f * glowAlpha), AmberDeep.copy(alpha = 0.30f))),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                isEnded -> Icons.Rounded.Replay
                isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = AmberCore,
            modifier = Modifier.size(size * 0.50f)
        )
    }
}

@Composable
private fun IconCircle(icon: ImageVector, size: Dp, tint: Color = TextBright, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(GlassSurface)
        .background(Brush.verticalGradient(0f to GlassHighlight, 0.4f to Color.Transparent, 1f to Color.Transparent))
        .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(20.dp))
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
private fun CinematicSeekBar(position: Long, duration: Long, isDragging: Boolean, seed: Int, onPreviewPositionChanged: (Long) -> Unit, onSeekFinished: (Long) -> Unit) {
    var localPosition by remember { mutableLongStateOf(position) }
    LaunchedEffect(position, isDragging) { if (!isDragging) localPosition = position }
    val haptic = LocalHapticFeedback.current
    var waveformVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (isDragging) { waveformVisible = true }
        else if (waveformVisible) { delay(2000); waveformVisible = false }
    }
    // Same bar width/gap as the waveform drawn below — used here purely to
    // detect when a drag crosses into a new bar, so a haptic tick can fire
    // per bar instead of only 4 times across the whole seek bar. Gives a
    // scroll-wheel/ratchet feel instead of a few coarse clicks.
    val density = LocalDensity.current
    val barStepPx = with(density) { (3.dp + 2.2.dp).toPx() }
    var lastBarIndex by remember { mutableIntStateOf(-1) }
    fun barIndexOf(x: Float): Int = (x / barStepPx).toInt()
    val bloom by animateFloatAsState(targetValue = if (isDragging || waveformVisible) 1f else 0f, animationSpec = tween(if (isDragging || waveformVisible) 300 else 600, easing = FastOutSlowInEasing), label = "liquidBloom")
    val glow by animateFloatAsState(targetValue = if (isDragging) 1f else 0.45f, animationSpec = tween(220), label = "seekGlow")
    fun positionFromX(x: Float, width: Float): Long { if (duration <= 0L || width <= 0f) return 0L; return (duration * (x / width).coerceIn(0f, 1f)).toLong().coerceIn(0L, duration) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)
            .pointerInput(duration) { detectTapGestures { o -> val p = positionFromX(o.x, size.width.toFloat()); localPosition = p; onPreviewPositionChanged(p); onSeekFinished(p) } }
            .pointerInput(duration) { detectDragGestures(
                onDragStart = { o -> localPosition = positionFromX(o.x, size.width.toFloat()); lastBarIndex = barIndexOf(o.x); onPreviewPositionChanged(localPosition) },
                onDrag = { c, _ ->
                    localPosition = positionFromX(c.position.x, size.width.toFloat())
                    val bar = barIndexOf(c.position.x)
                    if (bar != lastBarIndex) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastBarIndex = bar }
                    onPreviewPositionChanged(localPosition)
                },
                onDragEnd = { onSeekFinished(localPosition) },
                onDragCancel = { onSeekFinished(localPosition) }) }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                val tx = (size.width * prog).coerceIn(0f, size.width)

                val threadAlpha = 1f - bloom
                if (threadAlpha > 0.01f) {
                    val th = 2.2.dp.toPx(); val r = th / 2f
                    drawRoundRect(color = Color.White.copy(alpha = 0.15f * threadAlpha), topLeft = Offset(0f, cy - th / 2f), size = Size(size.width, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                    drawRoundRect(color = AmberGlow.copy(alpha = 0.95f * threadAlpha), topLeft = Offset(0f, cy - th / 2f), size = Size(tx, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
                }

                if (bloom > 0.01f) {
                    val barW = 3.dp.toPx(); val gap = 2.2.dp.toPx(); val step = barW + gap
                    val n = (size.width / step).toInt().coerceAtLeast(1)
                    val maxH = size.height * 0.92f
                    val cr = androidx.compose.ui.geometry.CornerRadius(barW / 2f, barW / 2f)
                    for (i in 0 until n) {
                        val bx = i * step + barW / 2f
                        val h1 = i * 374761393 + seed * 668265263
                        val h2 = (h1 xor (h1 shr 13)) * 1274126177
                        val noise = ((h2 ushr 16) and 0xFFFF) / 65535f
                        val wave = 0.5f + 0.5f * kotlin.math.sin(i * 0.31f + (seed % 360) / 57.3f)
                        val wave2 = 0.5f + 0.5f * kotlin.math.sin(i * 0.071f + (seed % 13).toFloat())
                        val amp = (0.18f + 0.82f * (0.40f * wave + 0.25f * wave2 + 0.35f * noise)).coerceIn(0.12f, 1f)
                        val hgt = amp * maxH * bloom
                        val played = bx <= tx
                        val prox = 1f - (kotlin.math.abs(bx - tx) / (size.width * 0.30f)).coerceIn(0f, 1f)
                        val alpha = if (played) bloom * (0.55f + 0.45f * prox) else bloom * (0.20f + 0.45f * prox)
                        val barColor = if (played) AmberGlow else Color.White
                        drawRoundRect(color = barColor.copy(alpha = alpha), topLeft = Offset(bx - barW / 2f, cy - hgt / 2f), size = Size(barW, hgt), cornerRadius = cr)
                    }
                }

                val tickY = cy - bloom * (size.height * 0.40f)
                listOf(0.25f, 0.50f, 0.75f).forEach {
                    drawCircle(color = Color.White.copy(alpha = 0.45f + 0.20f * bloom), radius = 2.2.dp.toPx(), center = Offset(size.width * it, tickY))
                }

                drawCircle(color = AmberGlow.copy(alpha = 0.22f * glow), radius = 16.dp.toPx(), center = Offset(tx, cy))
                drawCircle(color = AmberCore.copy(alpha = 0.40f * glow), radius = 10.dp.toPx(), center = Offset(tx, cy))
                if (bloom > 0.01f) {
                    drawLine(color = Color(0xFFFFF3D6).copy(alpha = 0.90f * bloom), start = Offset(tx, cy - size.height * 0.46f), end = Offset(tx, cy + size.height * 0.46f), strokeWidth = 2.dp.toPx())
                }
                drawCircle(color = Color(0xFFFFF3D6), radius = if (isDragging) 5.4.dp.toPx() else 4.6.dp.toPx(), center = Offset(tx, cy))
            }
        }

        val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val pillWidthEstimate = 52.dp
        AnimatedVisibility(
            visible = isDragging || waveformVisible,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (trackWidth * prog - pillWidthEstimate / 2f).coerceIn(0.dp, (trackWidth - pillWidthEstimate).coerceAtLeast(0.dp)),
                    y = (-30).dp
                )
        ) {
            Text(
                text = formatTime(localPosition),
                color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SeekPreviewBubble(isVisible: Boolean, bitmap: Bitmap?, timeText: String, isLandscape: Boolean, isLarge: Boolean, progress: Float) {
    val pw by animateDpAsState(if (isLarge) (if (isLandscape) 220.dp else 210.dp) else (if (isLandscape) 150.dp else 160.dp), tween(160), "pw")
    val ph by animateDpAsState(if (isLarge) (if (isLandscape) 124.dp else 118.dp) else (if (isLandscape) 84.dp else 90.dp), tween(160), "ph")
    AnimatedVisibility(visible = isVisible, enter = fadeIn(animationSpec = tween(80)), exit = fadeOut(animationSpec = tween(80)), modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val hp = 18.dp; val aw = maxWidth - (hp * 2)
            val raw = aw * progress.coerceIn(0f, 1f) - (pw / 2); val max = aw - pw
            val safe = when { max < 0.dp -> 0.dp; raw < 0.dp -> 0.dp; raw > max -> max; else -> raw }
            Column(modifier = Modifier.align(Alignment.BottomStart).offset(x = hp + safe).padding(bottom = if (isLandscape) 116.dp else 134.dp)
                .graphicsLayer { scaleX = if (isLarge) 1.02f else 0.98f; scaleY = if (isLarge) 1.02f else 0.98f; shadowElevation = if (isLarge) 18f else 10f }
                .glassPanel(cornerRadius = 18.dp, fill = GlassSurfaceStrong)
                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)).background(GlassSurfaceFaint), contentAlignment = Alignment.Center) { Text(text = timeText, color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = timeText, color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun buildPipActions(context: Context, isPlaying: Boolean): List<RemoteAction> {
    fun action(code: Int, iconRes: Int, title: String): RemoteAction {
        val intent = Intent("com.sole.cinevault.PIP_ACTION").putExtra("pip_action", code)
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(AndroidIcon.createWithResource(context, iconRes), title, title, pi)
    }
    return listOf(
        action(1, android.R.drawable.ic_media_rew, "Rewind"),
        action(0, if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play"),
        action(2, android.R.drawable.ic_media_ff, "Forward")
    )
}

@Composable
private fun NowPlayingTitlePill(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val infinite = rememberInfiniteTransition(label = "titlePulse")
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "titleDotAlpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
            .border(1.dp, Brush.horizontalGradient(listOf(AmberGlow.copy(alpha = 0.15f), AmberGlow.copy(alpha = 0.55f), AmberGlow.copy(alpha = 0.15f))), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AmberCore.copy(alpha = dotAlpha)))
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = text, color = TextBright, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FloatingScoreCapsule(meta: VideoWithMetadata?, vertical: Boolean = false) {
    if (meta == null) return
    val imdb = meta.imdbRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val rt = meta.rottenTomatoesRating?.takeIf { it.isNotBlank() && it != "N/A" }
    val tmdb = meta.rating?.takeIf { it > 0.0 }
    if (imdb == null && rt == null && tmdb == null) return

    val entries: List<@Composable () -> Unit> = buildList {
        if (imdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = imdb, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (rt != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TomatoLogoMark(value = rt)
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = rt, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tmdb != null) add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TmdbLogoMark()
                Spacer(modifier = Modifier.width(5.dp))
                Text(text = String.format("%.1f", tmdb), color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (vertical) {
        Column(
            modifier = Modifier.glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong).padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { entries.forEach { it() } }
    } else {
        Row(
            modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) { entries.forEach { it() } }
    }
}

@Composable
private fun RatingLogoGlow(size: Dp, content: @Composable BoxScope.() -> Unit) {
    val breathe = rememberInfiniteTransition(label = "ratingGlow")
    val glowAlpha by breathe.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ratingGlowAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .amberGlow(radius = size * 1.4f, alpha = glowAlpha),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun ImdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_imdb),
            contentDescription = "IMDb",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TmdbLogoMark() {
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_tmdb),
            contentDescription = "TMDB",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TomatoLogoMark(value: String) {
    val percent = value.replace("%", "").trim().toIntOrNull() ?: 0
    val isFresh = percent >= 60
    RatingLogoGlow(size = 22.dp) {
        Image(
            painter = painterResource(R.drawable.ic_rotten_tomatoes),
            contentDescription = "Rotten Tomatoes",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isFresh) ColorFilter.tint(Color(0xFF8BC34A)) else null
        )
    }
}

@Composable
private fun VerticalBrightnessHud(value: Int, size: Dp) {
    val fill = (value.toFloat() / 100f).coerceIn(0f, 1f)
    Row(modifier = Modifier.glassPanel(cornerRadius = 26.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.BrightnessHigh, contentDescription = null, tint = AmberCore, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(Color(0xFFFFF3D6), Color(0xFFFFC857)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilledCircleHud(value: Int, maxValue: Int, color: Color, size: Dp) {
    val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(modifier = Modifier.glassPanel(cornerRadius = 26.dp, fill = GlassSurfaceStrong).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.VolumeUp, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private data class TrackPopupRowData(val title: String, val subtitle: String, val onClick: () -> Unit)

private fun friendlyLanguageName(code: String?): String = SubtitleLanguageRegistry.displayName(code)

private fun cleanVideoTitle(path: String): String {
    var t = path.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ").replace(".", " ").replace("_", " ").replace("-", " ")
    t = t.replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby\\s*vision|dolby|vision|imax|remux|bluray|blu\\s*ray|brrip|hdrip|webrip|web\\s*dl|webdl|web|nf|amzn|dsnp|hulu|itunes|x264|x265|h264|h265|hevc|10bit|8bit|aac5?|aac|ddp5?\\.?1?|dd\\+|dts|truehd|atmos|5\\s*1|7\\s*1|yts|rarbg|tgx|eztv|pir8|ag|proper|repack|extended|theatrical|directors?\\s*cut|multi|dual|audio|english|hindi|ita|eng|mkv|mp4|avi|subs?|esub)\\b", RegexOption.IGNORE_CASE), " ")
    t = t.replace(Regex("\\bS\\d{1,2}E\\d{1,2}\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\b(19|20)\\d{2}\\b.*$", RegexOption.IGNORE_CASE), " ")
    return t.replace(Regex("\\s+"), " ").trim().ifBlank { "Now Playing" }
}

private fun formatTime(ms: Long): String { val s = ms/1000; val h = s/3600; val m = (s%3600)/60; val sec = s%60; return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec) }

private fun cleanEpisodeDisplayName(fileName: String): String {
    val m = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)
    val prefix = if (m != null) "S${m.groupValues[1].padStart(2,'0')}E${m.groupValues[2].padStart(2,'0')}" else "Episode"
    var n = fileName.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".").replace(Regex("\\[.*?]"), " ").replace(Regex("\\(.*?\\)"), " ").replace(".", " ").replace("_", " ").replace("-", " ")
    n = n.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ").replace(Regex("\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|bluray|brrip|webrip|webdl|web|x264|x265|h264|h265|hevc|10bit|aac|ddp|dts|atmos|mkv|mp4|avi|rarbg|yts|eztv|tgx|nf|amzn)\\b", RegexOption.IGNORE_CASE), " ").replace(Regex("\\s+"), " ").trim()
    return if (n.isBlank()) prefix else "$prefix • $n"
}

@Composable
private fun SkipIntroButton(isLandscape: Boolean, onClick: () -> Unit) {
    Text(text = "SKIP INTRO", color = Color.Black, fontSize = if (isLandscape) 11.sp else 12.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors = listOf(AmberCore, AmberGlow))).clickable { onClick() }.padding(horizontal = if (isLandscape) 13.dp else 15.dp, vertical = if (isLandscape) 7.dp else 8.dp))
}

@Composable
private fun NextEpisodeCountdownOverlay(nextEpisode: VideoWithMetadata?, countdown: Int, isLandscape: Boolean, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    if (nextEpisode == null) return
    Column(modifier = Modifier.width(if (isLandscape) 310.dp else 300.dp)
        .glassPanel(cornerRadius = 26.dp, fill = GlassSurfaceStrong)
        .padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Next episode starts in", color = TextBright.copy(alpha = 0.82f), fontSize = if (isLandscape) 13.sp else 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = countdown.coerceAtLeast(1).toString(), color = AmberCore, fontSize = if (isLandscape) 38.sp else 42.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = nextEpisode.subtitle.ifBlank { cleanEpisodeDisplayName(nextEpisode.video.name) }, color = TextBright, fontSize = if (isLandscape) 13.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Cancel", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onCancel() }.padding(horizontal = 15.dp, vertical = 8.dp))
            Text(text = "Play Now", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onPlayNow() }.padding(horizontal = 15.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun FloatingTrackPopup(title: String, modifier: Modifier, rows: List<TrackPopupRowData>, audioSyncMs: Int = 0, onAudioSyncChange: (Int) -> Unit = {}, onAnyClick: () -> Unit = {}, onClose: () -> Unit) {
    Column(modifier = modifier.glassPanel(cornerRadius = 16.dp, fill = SpaceMid.copy(alpha = 0.97f)).padding(6.dp)) {
        Text(text = title, color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        rows.forEach { row ->
            CompactGlassMenuRow(icon = Icons.Rounded.Audiotrack, label = row.title, onClick = { onAnyClick(); row.onClick() })
            Spacer(modifier = Modifier.height(3.dp))
        }
        Text(text = "Audio Delay", color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SyncStepChip(text = "−50") { onAnyClick(); onAudioSyncChange((audioSyncMs - 50).coerceAtLeast(-2000)) }
            Text(
                text = if (audioSyncMs == 0) "0ms" else "${if (audioSyncMs > 0) "+" else ""}$audioSyncMs",
                color = if (audioSyncMs == 0) TextBright else AmberCore,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            SyncStepChip(text = "+50") { onAnyClick(); onAudioSyncChange((audioSyncMs + 50).coerceAtMost(2000)) }
        }
        Spacer(modifier = Modifier.height(5.dp))
        CompactGlassMenuRow(icon = null, label = "Close", onClick = { onAnyClick(); onClose() })
    }
}

@Composable
private fun CompactGlassMenuRow(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(text = label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun SyncStepChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(GlassSurface)
            .border(1.dp, Brush.verticalGradient(listOf(GlassBorderTop, GlassBorderBottom)), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
