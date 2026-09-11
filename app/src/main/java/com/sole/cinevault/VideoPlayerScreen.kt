package com.sole.cinevault

import com.sole.cinevault.library.*
import com.sole.cinevault.smb.*

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
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

    var audioSyncMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(audioSyncMs) { AudioSyncHolder.offsetUs = playerAudioSyncOffsetUs(audioSyncMs) }

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
        playerInitialMusicVolumePercent(am)
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
    var showSubtitleDock by remember { mutableStateOf(false) }
    var showSubtitleBloom by remember { mutableStateOf(false) }
    var studioCategory by remember { mutableStateOf<com.sole.cinevault.subtitles.StudioCategory?>(null) }
    var showDualSubsWindow by remember { mutableStateOf(false) }
    var showSubtitleBehaviourWindow by remember { mutableStateOf(false) }
    var pendingDualAiLanguage by remember(currentVideo.path) { mutableStateOf<String?>(null) }
    var movieSubtitleMemory by remember(currentVideo.path) {
        mutableStateOf<MovieSubtitleMemory?>(null)
    }
    var movieSubtitleMemoryReady by remember(currentVideo.path) { mutableStateOf(false) }
    var movieAppearanceMemoryReady by remember(currentVideo.path) { mutableStateOf(false) }
    var restoredDualNeedsApply by remember(currentVideo.path) { mutableStateOf(false) }
    var trackSelectorManageMode by remember { mutableStateOf(false) }
    var activeDockItem by remember { mutableStateOf<com.sole.cinevault.subtitles.SubtitleDockItem?>(null) }

    // Slice 22: Sub Studio navigation is now routed through one small
    // coordinator. The composable still owns the actual visibility state;
    // the coordinator only decides which standalone destination each dock
    // or category tap should open.
    val subtitleStudioNavigation = SubtitleStudioNavigationCoordinator(
        setActiveDockItem = { activeDockItem = it },
        setShowSubtitleDock = { showSubtitleDock = it },
        setTrackSelectorManageMode = { trackSelectorManageMode = it },
        setShowTrackSelector = { trackUi.showSelector = it },
        setShowSubtitleBloom = { showSubtitleBloom = it },
        setStudioCategory = { studioCategory = it },
        setShowAppearanceStudio = { coreUi.showAppearanceStudio = it },
        setShowSubtitleSearch = { searchUi.showSearch = it },
        setShowSubtitleBehaviourWindow = { showSubtitleBehaviourWindow = it },
    )

    var autoSyncStatus by remember { mutableStateOf<AutoSyncStatus>(AutoSyncStatus.Idle) }
    var autoSyncSpeechTimeline by remember { mutableStateOf<FloatArray?>(null) }
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
    var dualSecondaryColorHex by remember(currentVideo.path) {
        mutableStateOf("#00E5FF")
    }

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

    var localPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    val glasses = rememberPlayerGlassesMode(
        player = exoPlayer,
        title = if (currentMediaType.equals("stream", ignoreCase = true)) currentVideo.name else cleanVideoTitle(currentVideo.path),
        ratingText = remember(currentVideo.path, episodeList) {
            buildExternalRatingText(currentVideo.path, episodeList)
        },
        onBack = onBack,
        localPlayerView = localPlayerView,
        onBoundPlayerViewChanged = { studioUi.playerView = it }
    )
    val externalDisplay = glasses.display
    val showGlassesConnectedHint = glasses.showConnectedHint
    val externalPresentation = glasses.presentation
    val externalPlayerView = glasses.externalPlayerView

    val canDownloadExternalSubtitles = currentMediaType.equals("movie", ignoreCase = true) || currentMediaType.equals("tv", ignoreCase = true) || currentMediaType.equals("restricted", ignoreCase = true)
    val isCurrentTvShow = currentMediaType.equals("tv", ignoreCase = true)
    val isStreamMedia = currentMediaType.equals("stream", ignoreCase = true)
    val isRestrictedFolderMedia = folderIdFromRestrictedMarker(currentVideo.folderPath) != null

    // Slice 24: the player exit/PiP decision now lives outside the giant
    // composable. The same rule is preserved: if an explicit exit request
    // happens while playing and PiP can be entered, keep playback visible
    // there; otherwise fall back to the normal onBack navigation.
    val playerExitCoordinator = remember(exoPlayer, activity) {
        PlayerExitCoordinator(
            context = context,
            activity = activity,
            exoPlayer = exoPlayer,
            isPlaying = { isPlaying },
            onBack = onBack,
        )
    }

    // Slice 43: closing all transient player menus is now coordinated outside
    // VideoPlayerScreen. The player owns state; the coordinator owns the close sequence.
    val playerMenuCloseCoordinator = remember {
        PlayerMenuCloseCoordinator(
            closeAudioSelector = { showAudioSelector = false },
            closeSettings = { coreUi.showSettings = false },
            closeDriftDialog = { driftUi.showDialog = false },
            closeSpeedMenu = { showSpeedMenu = false },
            closeSleepMenu = { showSleepMenu = false },
            closeSrtBrowser = { showSrtBrowser = false },
            closeSubtitleSurfaces = {
                subtitleStudioNavigation.closeSubtitleSurfaces(
                    clearPendingImportCandidates = { searchUi.pendingImportCandidates = null },
                    setShowFallback = { searchUi.showFallback = it },
                    setShowEmbeddedBrowser = { searchUi.showEmbeddedBrowser = it },
                    setShowDualSubsWindow = { showDualSubsWindow = it },
                )
            },
        )
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

    // Slice 21: playback-speed and sleep-timer session actions now live
    // outside VideoPlayerScreen. This keeps the composable responsible for
    // displaying state while the coordinator owns the mutations, haptics,
    // player calls and user feedback for these two small session features.
    val playerSessionActionsCoordinator = remember(exoPlayer) {
        PlayerSessionActionsCoordinator(
            context = context,
            exoPlayer = exoPlayer,
            performSelectionHaptic = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            setPlaybackSpeedState = { playbackSpeed = it },
            setSleepTimerMinutes = { sleepTimerMinutes = it },
            getSleepTimerActive = { sleepTimerActive },
            setSleepTimerActive = { sleepTimerActive = it },
            getSleepTimerRemainingMs = { sleepTimerRemainingMs },
            setSleepTimerRemainingMs = { sleepTimerRemainingMs = it },
            closeSpeedMenu = { showSpeedMenu = false },
            closeSleepMenu = { showSleepMenu = false },
            showControls = { showControls = true },
        )
    }

    LaunchedEffect(sleepTimerActive, sleepTimerRemainingMs) {
        if (playerSessionActionsCoordinator.shouldTickSleepTimer()) {
            delay(playerSleepTimerTickIntervalMs())
            playerSessionActionsCoordinator.tickSleepTimer()
        }
    }

    // Playback navigation behavior is owned by PlaybackNavigationCoordinator.
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
                    val resumeAt = playerSafeResumePosition(exoPlayer.currentPosition)
                    playCurrentVideoWithSubtitle(null, resumeAt, false)
                    trackUi.primaryUri = null; trackUi.originalUri = null
                    trackUi.selectedKey = "off"; trackUi.selectedLabel = ""; trackUi.selectedSource = ""
                    coreUi.subtitlesEnabled = false
                }
            },
            onDeleteUndone = { file ->
                if (detachedSubtitleForUndo?.absolutePath == file.absolutePath && file.exists()) {
                    val resumeAt = playerSafeResumePosition(exoPlayer.currentPosition)
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
    // Subtitle acquisition/search behavior is owned by SubtitleSearchCoordinator.
    // UI call sites now invoke it directly; no player-local pass-through layer remains.
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
    // Slice 26: the system picker stays composable-owned, but everything
    // after the user chooses a file now lives in SubtitleLocalImportCoordinator:
    // persistable permission, content validation/import, ZIP candidate handling
    // and failure feedback.
    val subtitleLocalImportCoordinator = remember {
        SubtitleLocalImportCoordinator(
            context = context,
            scope = scope,
            getCurrentVideoPath = { currentVideo.path },
            getPreferredLanguage = {
                coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en"
            },
            applyImportedSubtitle = { imported ->
                subtitleSearchCoordinator.applyImportedWebsiteSubtitle(imported)
            },
            setPendingImportCandidates = { result ->
                searchUi.pendingImportCandidates = result
            },
        )
    }

    val srtPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            subtitleLocalImportCoordinator.importPickedUri(uri)
        }

    // FIX: findHttpStatusDetail/friendlyPlaybackError/isTransientPlaybackError
    // used to be defined right here — now plain top-level functions in
    // PlaybackErrorFormatting.kt (see that file for the full reasoning).
    // Same package (com.sole.cinevault), so every call site below still
    // resolves with no changes at all — not even a wrapper function was
    // needed here, unlike the earlier slices, since these never touched
    // any state to begin with.

    // Slice 47: the complete per-video startup/reset + subtitle restore/fallback
    // pipeline now lives outside VideoPlayerScreen.
    PlayerVideoSessionInitializationEffect(
        context = context,
        scope = scope,
        video = currentVideo,
        isStreamMedia = isStreamMedia,
        isRestrictedFolderMedia = isRestrictedFolderMedia,
        canDownloadExternalSubtitles = canDownloadExternalSubtitles,
        coreUi = coreUi,
        trackUi = trackUi,
        searchUi = searchUi,
        driftUi = driftUi,
        dualUi = dualUi,
        appearanceUi = appearanceUi,
        studioUi = studioUi,
        autoSubtitleFetch = autoSubtitleFetch,
        setTextTracksDisabled = { disabled ->
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled)
                .build()
        },
        playVideoWithSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
            playCurrentVideoWithSubtitle(
                subtitleUri = subtitleUri,
                resumePosition = resumePosition,
                isOriginalSubtitle = isOriginalSubtitle,
            )
        },
        getCurrentSafeResumePosition = {
            playerSafeResumePosition(exoPlayer.currentPosition)
        },
        setters = PlayerVideoSessionSetters(
            setMovieSubtitleMemoryReady = { movieSubtitleMemoryReady = it },
            setMovieAppearanceMemoryReady = { movieAppearanceMemoryReady = it },
            setRestoredDualNeedsApply = { restoredDualNeedsApply = it },
            setMovieSubtitleMemory = { movieSubtitleMemory = it },
            setPosition = { position = it },
            setDuration = { duration = it },
            setShowControls = { showControls = it },
            setShowTopBar = { showTopBar = it },
            setShowAudioSelector = { showAudioSelector = it },
            setShowSpeedMenu = { showSpeedMenu = it },
            setShowSleepMenu = { showSleepMenu = it },
            setShowSrtBrowser = { showSrtBrowser = it },
            setPendingNextEpisode = { pendingNextEpisode = it },
            setNextEpisodeCountdown = { nextEpisodeCountdown = it },
            setShowNextEpisodeOverlay = { showNextEpisodeOverlay = it },
            setNextEpisodeDismissed = { nextEpisodeDismissed = it },
            setSmartSegmentResult = { smartSegmentResult = it },
            setPreviewBitmap = { previewBitmap = it },
            clearPreviewFrames = { previewFrames = emptyList() },
            setIsVideoEnded = { isVideoEnded = it },
            setPlayerErrorMessage = { playerErrorMessage = it },
            setErrorRetryCount = { errorRetryCount = it },
            setStuckBufferingHint = { stuckBufferingHint = it },
            setAudioLanguageCheckedForPath = { audioLanguageCheckedForPath = it },
            setDualSecondaryColorHex = { dualSecondaryColorHex = it },
            setAutoSyncStatus = { autoSyncStatus = it },
            setAutoSyncSpeechTimeline = { autoSyncSpeechTimeline = it },
            setDroppedFrameNudgeCount = { droppedFrameNudgeCount = it },
            setLastNudgeAtMs = { lastNudgeAtMs = it },
        ),
    )

    PlayerSessionLifecycle(
        context = context,
        activity = activity,
        player = exoPlayer,
        videoPath = currentVideo.path,
        isStreamMedia = isStreamMedia,
        onNextRequested = { playbackNavigationCoordinator.playNext() },
        onPreviousRequested = { playbackNavigationCoordinator.playPrevious() },
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

    // Slice 46: PiP lifecycle effects are grouped outside VideoPlayerScreen.
    // Entering PiP still closes CineVault chrome and the action receiver
    // continues to control the same ExoPlayer instance.
    PlayerPipEffects(
        context = context,
        player = exoPlayer,
        isInPipMode = CineVaultPlayerHolder.isInPipMode,
        onEnteredPip = {
            playerMenuCloseCoordinator.closeAll()
        },
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
        showSubtitleDock = showSubtitleDock,
        showSubtitleBloom = showSubtitleBloom,
        showDualSubsWindow = showDualSubsWindow,
        onHideSubtitleDock = { showSubtitleDock = false },
        onHideSubtitleBloom = { showSubtitleBloom = false; studioCategory = null },
        onHideDualSubsWindow = { showDualSubsWindow = false },
    )

    // Slice 51: the subtitle runtime effect cluster now owns pending local
    // subtitle application, live appearance updates, sync/drift re-rendering,
    // restored Dual Subs re-apply, and the shared subtitle sync-tools
    // coordinator. The player keeps only the returned UI-facing handles.
    val subtitleRuntimeEffects = rememberPlayerSubtitleRuntimeEffects(
        context = context,
        scope = scope,
        exoPlayer = exoPlayer,
        trackSelector = trackSelector,
        currentVideoPath = currentVideo.path,
        pendingSrtUri = pendingSrtUri,
        dualSecondaryColorHex = dualSecondaryColorHex,
        movieSubtitleMemoryReady = movieSubtitleMemoryReady,
        restoredDualNeedsApply = restoredDualNeedsApply,
        coreUi = coreUi,
        trackUi = trackUi,
        autoSubtitleFetch = autoSubtitleFetch,
        appearanceUi = appearanceUi,
        studioUi = studioUi,
        dualUi = dualUi,
        driftUi = driftUi,
        onShowControls = { showControls = true },
        onClearPendingSrtUri = { pendingSrtUri = null },
        onRestoredDualApplied = { restoredDualNeedsApply = false },
        onPendingDualAiLanguageChanged = { pendingDualAiLanguage = it },
        playCurrentVideoWithSubtitle = { subtitleUri, resumePosition, isOriginalSubtitle ->
            playCurrentVideoWithSubtitle(
                subtitleUri = subtitleUri,
                resumePosition = resumePosition,
                isOriginalSubtitle = isOriginalSubtitle,
            )
        },
    )
    val subtitleSyncTools = subtitleRuntimeEffects.syncTools
    val isAssOrSsaFormat = subtitleRuntimeEffects.isAssOrSsaFormat

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
    // Slice 33: Auto-Sync eligibility is now a pure, unit-tested decision.
    // The player derives the current subtitle name from its Uri and passes
    // only plain values into the eligibility function.
    val primarySubtitleForAutoSync = trackUi.primaryUri
    val autoSyncAvailable = isPlayerAutoSyncAvailable(
        primarySubtitleName = primarySubtitleForAutoSync
            ?.lastPathSegment
            ?: primarySubtitleForAutoSync?.toString(),
        isStreamMedia = isStreamMedia,
        videoPath = currentVideo.path,
    )

    // Auto-Sync behavior lives in AutoSyncCoordinator; UI call sites now
    // invoke the coordinator directly with no local pass-through wrappers.
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
            resetPreviewFrames = { previewFrames = emptyList(); previewBitmap = null },
            incrementPreviewReloadKey = { previewReloadKey++ },
            setSyncOffsetSeconds = { coreUi.syncOffset = it },
            setDriftScale = { driftUi.scale = it },
            incrementStudioMenuTouchKey = { studioUi.menuTouchKey++ },
            setSpeechTimeline = { autoSyncSpeechTimeline = it }
        )
    }
    // Stage 2C: speech recognition and subtitle translation are independent.
    // Translation never requires Whisper and resolves normal Subtitle Studio /
    // local subtitle sources through SubtitleSourceResolver.
    var speechSubtitleStatus by remember {
        mutableStateOf<SpeechSubtitleStatus>(SpeechSubtitleStatus.Idle)
    }
    var subtitleTranslationStatus by remember {
        mutableStateOf<SubtitleTranslationStatus>(SubtitleTranslationStatus.Idle)
    }
    var translationSuccessLanguage by remember { mutableStateOf<String?>(null) }
    var showSpeechSubtitlePanel by remember { mutableStateOf(false) }
    var showSubtitleTranslationPanel by remember { mutableStateOf(false) }

    var generatedSubtitleRefreshKey by remember(currentVideo.path) { mutableIntStateOf(0) }
    var generatedSubtitleFiles by remember(currentVideo.path) {
        mutableStateOf<List<GeneratedSubtitleFile>>(emptyList())
    }

    // Slice 40: generated-subtitle library loading is now outside the player.
    // Compose still owns the refresh effect key; the store/IO orchestration
    // lives in GeneratedSubtitleLibraryCoordinator.
    val generatedSubtitleLibraryCoordinator = remember {
        GeneratedSubtitleLibraryCoordinator(
            context = context,
            getCurrentVideoPath = { currentVideo.path },
        )
    }

    LaunchedEffect(currentVideo.path, generatedSubtitleRefreshKey) {
        generatedSubtitleFiles =
            generatedSubtitleLibraryCoordinator.loadForCurrentVideo()
    }

    // Slice 20: keep generated/translated subtitle source resolution and
    // application out of VideoPlayerScreen. Speech-to-subs, AI Translation,
    // Dual Subs and the generated-subtitle library now share one small
    // orchestrator instead of duplicating track-selection mutations here.
    val generatedSubtitleOrchestrator = remember(exoPlayer) {
        GeneratedSubtitleOrchestrator(
            getResumePosition = { playerSafeResumePosition(exoPlayer.currentPosition) },
            getPrimaryUri = { trackUi.primaryUri },
            getOriginalUri = { trackUi.originalUri },
            getSelectedKey = { trackUi.selectedKey },
            getSelectedLabel = { trackUi.selectedLabel },
            getSelectedSource = { trackUi.selectedSource },
            getPrimaryLanguage = { trackUi.primaryLanguage },
            setSubtitlesEnabled = { coreUi.subtitlesEnabled = it },
            setPrimaryUri = { trackUi.primaryUri = it },
            setOriginalUri = { trackUi.originalUri = it },
            setPrimaryLanguage = { trackUi.primaryLanguage = it },
            setSelectedKey = { trackUi.selectedKey = it },
            setSelectedLabel = { trackUi.selectedLabel = it },
            setSelectedSource = { trackUi.selectedSource = it },
            playWithSubtitle = { uri, resumeAt ->
                playCurrentVideoWithSubtitle(
                    uri,
                    resumePosition = resumeAt,
                    isOriginalSubtitle = true,
                )
            },
        )
    }

    val speechSubtitleCoordinator = remember(exoPlayer) {
        SpeechSubtitleCoordinator(
            context = context,
            scope = scope,
            exoPlayer = exoPlayer,
            getCurrentVideoPath = { currentVideo.path },
            getStatus = { speechSubtitleStatus },
            setStatus = { speechSubtitleStatus = it },
            onSubtitleReady = { file, language ->
                generatedSubtitleOrchestrator.apply(file, language, "Speech recognition")
            },
            onGeneratedLibraryChanged = { generatedSubtitleRefreshKey++ },
        )
    }

    // Slice 39: completed AI translations are now routed outside the player.
    // A finished subtitle either satisfies a pending Dual Subs request or
    // becomes the active primary AI translation.
    val subtitleTranslationResultCoordinator = remember {
        SubtitleTranslationResultCoordinator(
            scope = scope,
            isDualEnabled = { dualUi.enabled },
            getPendingDualLanguage = { pendingDualAiLanguage },
            clearPendingDualLanguage = { pendingDualAiLanguage = null },
            applyDualSecondary = { uri ->
                subtitleSyncTools.applyDualSecondaryUri(uri, "AI")
            },
            applyPrimaryTranslation = { file, language ->
                generatedSubtitleOrchestrator.apply(
                    file,
                    language,
                    "AI Translation",
                )
            },
            showTranslationSuccess = { language ->
                translationSuccessLanguage =
                    SubtitleLanguageRegistry.displayName(language)
            },
            clearTranslationSuccess = {
                translationSuccessLanguage = null
            },
        )
    }

    val subtitleTranslationCoordinator = remember(exoPlayer) {
        SubtitleTranslationCoordinator(
            context = context,
            scope = scope,
            getCurrentVideoPath = { currentVideo.path },
            resolveActiveSubtitle = { generatedSubtitleOrchestrator.resolveActiveSubtitle() },
            getStatus = { subtitleTranslationStatus },
            setStatus = { subtitleTranslationStatus = it },
            onSubtitleReady = { file, language ->
                subtitleTranslationResultCoordinator.onTranslationReady(
                    file = file,
                    language = language,
                )
            },
            onGeneratedLibraryChanged = { generatedSubtitleRefreshKey++ },
        )
    }

    // Slice 32: pending Dual Subs AI-translation request decisions now live
    // in a plain Kotlin coordinator, which is unit-tested in app/src/test.
    val dualAiTranslationCoordinator = remember {
        DualAiTranslationCoordinator(
            getPendingLanguage = { pendingDualAiLanguage },
            clearPendingLanguage = { pendingDualAiLanguage = null },
            isDualEnabled = { dualUi.enabled },
            disableDual = { dualUi.enabled = false },
            setStatusText = { dualUi.statusText = it },
            translateActive = { target ->
                subtitleTranslationCoordinator.translateActive(target)
            },
        )
    }

    LaunchedEffect(pendingDualAiLanguage) {
        dualAiTranslationCoordinator.processPendingRequest()
    }

    // Slice 35: AI job label/progress presentation is now pure and tested.
    val speechJobPresentation = speechSubtitleJobPresentation(speechSubtitleStatus)
    val translationJobPresentation = subtitleTranslationJobPresentation(subtitleTranslationStatus)

    val speechJobLabel = speechJobPresentation.label
    val speechJobProgress = speechJobPresentation.progress
    val translationJobLabel = translationJobPresentation.label
    val translationJobProgress = translationJobPresentation.progress

    // Slice 36: subtitle AI panel back handling is now driven by a pure,
    // unit-tested decision so panel-close priority cannot silently regress.
    BackHandler(enabled = showSpeechSubtitlePanel || showSubtitleTranslationPanel) {
        when (
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = showSpeechSubtitlePanel,
                showSubtitleTranslationPanel = showSubtitleTranslationPanel,
            )
        ) {
            SubtitlePanelBackAction.CLOSE_TRANSLATION ->
                showSubtitleTranslationPanel = false

            SubtitlePanelBackAction.CLOSE_SPEECH ->
                showSpeechSubtitlePanel = false

            SubtitlePanelBackAction.NONE -> Unit
        }
    }

    // Slice 27: next-episode countdown completion/navigation is now owned
    // by a small coordinator. Compose still owns the cancellable timer effect.
    val nextEpisodeCoordinator = remember {
        NextEpisodeCoordinator(
            getPendingNextEpisode = { pendingNextEpisode },
            getShowNextEpisodeOverlay = { showNextEpisodeOverlay },
            setShowNextEpisodeOverlay = { showNextEpisodeOverlay = it },
            setPendingNextEpisode = { pendingNextEpisode = it },
            setCurrentMediaType = { currentMediaType = it },
            setCurrentVideo = { currentVideo = it },
            onPlayNext = onPlayNext,
        )
    }

    LaunchedEffect(showNextEpisodeOverlay, pendingNextEpisode) {
        if (nextEpisodeCoordinator.shouldRunCountdown()) {
            var count = 15
            while (count > 0) {
                nextEpisodeCountdown = count
                delay(playerNextEpisodeCountdownIntervalMs())
                if (!nextEpisodeCoordinator.shouldRunCountdown()) return@LaunchedEffect
                if (isPlaying || isVideoEnded) count--
            }
            nextEpisodeCoordinator.playPendingNextEpisode()
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
        // Which profile applies right now — external (any AR glasses via DP Alt Mode)
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

        // Slice 49: per-movie subtitle restore/save lifecycle wiring now lives
        // outside VideoPlayerScreen. The existing coordinator still owns the
        // actual memory mutation and persistence behavior.
        PlayerMovieSubtitleMemoryEffects(
            context = context,
            videoPath = currentVideo.path,
            displayProfileName = displayProfileType.name,
            isLandscape = isLandscape,
            movieSubtitleMemory = movieSubtitleMemory,
            movieSubtitleMemoryReady = movieSubtitleMemoryReady,
            movieAppearanceMemoryReady = movieAppearanceMemoryReady,
            coreUi = coreUi,
            trackUi = trackUi,
            dualUi = dualUi,
            appearanceUi = appearanceUi,
            dualSecondaryColorHex = dualSecondaryColorHex,
            onMovieAppearanceMemoryReadyChanged = {
                movieAppearanceMemoryReady = it
            },
        )

        // Slice 23: the complete subtitle reset operation now lives outside
        // VideoPlayerScreen. This keeps the player composable from directly
        // coordinating appearance, subtitle sync, drift and audio-sync reset
        // state in one local function.
        val subtitleResetCoordinator = SubtitleResetCoordinator(
            context = context,
            displayProfileType = displayProfileType,
            isLandscape = isLandscape,
            appearanceUi = appearanceUi,
            coreUi = coreUi,
            trackUi = trackUi,
            driftUi = driftUi,
            setAudioSyncMs = { audioSyncMs = it },
            setShowControls = { showControls = it },
            incrementMenuTouchKey = { studioUi.menuTouchKey++ },
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

        // Slice 15: position Sub Studio surfaces against the ACTUAL visible
        // movie picture, not the physical screen edge. Letter/pillar-box
        // bars are symmetric, so only the horizontal content inset is
        // needed for our right-centred landscape resting zone.
        val rawVideoWidth = exoPlayer.videoSize.width
        val rawVideoHeight = exoPlayer.videoSize.height
        val rawPixelRatio = exoPlayer.videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
        val videoAspect = if (rawVideoWidth > 0 && rawVideoHeight > 0)
            (rawVideoWidth.toFloat() * rawPixelRatio) / rawVideoHeight.toFloat()
        else 0f
        val containerAspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0f
        val visibleMovieWidth = when {
            videoAspect <= 0f || containerAspect <= 0f -> maxWidth
            videoAspect >= containerAspect -> maxWidth
            else -> (maxHeight.value * videoAspect).dp.coerceAtMost(maxWidth)
        }
        val movieFrameHorizontalInset = ((maxWidth - visibleMovieWidth) / 2f).coerceAtLeast(0.dp)
        val studioFrameInset = movieFrameHorizontalInset + if (maxWidth < 700.dp) 12.dp else 22.dp

        // Pad reference: Tracks ~20% broader/taller, Style ~28% broader and
        // ~22% taller. coerceAtMost keeps the same design usable on phones.
        val trackStudioWidth = (trackSelectorWidth * if (maxWidth >= 900.dp) 1.38f else 1.24f)
            .coerceAtLeast(if (maxWidth >= 900.dp) 330.dp else 270.dp)
            .coerceAtMost((visibleMovieWidth - studioFrameInset * 2).coerceAtLeast(250.dp))
        val trackStudioMaxHeight = (trackSelectorMaxHeight * if (maxHeight >= 600.dp) 1.30f else 1.20f)
            .coerceAtMost((maxHeight - 24.dp).coerceAtLeast(250.dp))
        val styleStudioWidth = (trackSelectorWidth * 1.28f)
            .coerceAtMost((visibleMovieWidth - studioFrameInset * 2).coerceAtLeast(270.dp))
        val styleStudioMaxHeight = (trackSelectorMaxHeight * 1.22f)
            .coerceAtMost((maxHeight - 24.dp).coerceAtLeast(270.dp))
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

        // Slice 28: Smart Segment loading plus credits-driven next-episode
        // decisions now live in PlayerSmartSegmentCoordinator. Compose still
        // owns these effects so cancellation remains tied to their keys.
        val playerSmartSegmentCoordinator = remember(smartSegmentRepository) {
            PlayerSmartSegmentCoordinator(smartSegmentRepository)
        }

        val smartPlaybackSegments = deriveSmartPlaybackSegments(
            result = smartSegmentResult,
            position = position
        )
        val activeSmartSegment = smartPlaybackSegments.activeSegment
        val exactSceneSegment = smartPlaybackSegments.exactSceneSegment
        val creditsSegment = smartPlaybackSegments.creditsSegment

        // Slice 45: Smart Segment / credits side effects now live outside the
        // giant player composable. The player only supplies state and callbacks.
        PlayerSmartSegmentEffects(
            coordinator = playerSmartSegmentCoordinator,
            currentMeta = currentMeta,
            duration = duration,
            currentVideoPath = currentVideo.path,
            episodeList = episodeList,
            isCurrentTvShow = isCurrentTvShow,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            nextEpisodeDismissed = nextEpisodeDismissed,
            creditsStartMs = creditsSegment?.startMs,
            position = position,
            onSmartSegmentLoaded = { smartSegmentResult = it },
            onNextEpisodeTriggered = { next ->
                pendingNextEpisode = next
                nextEpisodeCountdown = 15
                showNextEpisodeOverlay = true
            },
            onResetNextEpisodeOverlay = {
                showNextEpisodeOverlay = false
                pendingNextEpisode = null
                nextEpisodeCountdown = 0
            },
        )

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
        // Slice 41: keep player-specific system gesture exclusion cleanup
        // outside the giant player composable.
        PlayerGestureExclusionCleanupEffect(view)

        val playbackGestureModifier = if (externalPlayerView != null) {
            Modifier.glassesTouchpadGestures(
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
                        val safeDuration = playerSafeSeekDuration(exoPlayer.duration)
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
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = playerWindowBrightness(brightnessPercent) }
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
                    onPrevious = { externalPresentation?.showGestureHud("Episode", "PREVIOUS"); playbackNavigationCoordinator.playPrevious() },
                    onNext = { externalPresentation?.showGestureHud("Episode", "NEXT"); playbackNavigationCoordinator.playNext() },
                    onPointerMove = { externalPresentation?.movePointer(it.x, it.y) },
                    onPointerClick = {
                        externalPresentation?.showTouchPulse()
                        externalPresentation?.clickPointer() ?: false
                    },
                    onPinchZoomPan = { zoom, pan ->
                        externalPresentation?.applyViewportTransform(zoom, pan.x, pan.y)
                        val zoomHud = calculatePlayerExternalZoomHud(videoScale, zoom)
                        videoScale = zoomHud.scale
                        externalPresentation?.showGestureHud("Screen size", "${zoomHud.percent}%", zoomHud.progressPercent)
                    },
                    onEmergencyReturnToTablet = {
                        externalPresentation?.showGestureHud("Emergency return", "TABLET")
                        externalPresentation?.enterTabletStandby()
                        glasses.disableSession()
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
                            coreUi.dialogueSyncArmed -> subtitleSyncTools.cancelDialogueSync()
                            showSubtitleDock -> showSubtitleDock = false
                            showSubtitleBloom -> { showSubtitleBloom = false; studioCategory = null }
                            showDualSubsWindow -> showDualSubsWindow = false
                            showSubtitleBehaviourWindow -> showSubtitleBehaviourWindow = false
                            showSpeechSubtitlePanel -> showSpeechSubtitlePanel = false
                            showSubtitleTranslationPanel -> showSubtitleTranslationPanel = false
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
                    onEdgeSwipeNext = { playbackNavigationCoordinator.playNext() },
                    onBrightnessDrag = { deltaY ->
                        brightnessPercent = adjustPlayerBrightnessPercent(brightnessPercent, deltaY)
                        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = playerWindowBrightness(brightnessPercent) }
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
            translationSuccessLanguage = translationSuccessLanguage,
            translationSuccessBottomPadding = bottomDockPadding + playButton + 26.dp,
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
            onSpeedSelected = { playerSessionActionsCoordinator.setPlaybackSpeed(it) },
            onDismissSpeedMenu = { showSpeedMenu = false },
            onSleepSelected = { playerSessionActionsCoordinator.setSleepTimer(it) },
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
            onDeleteSrt = { file -> subtitleDeletionCoordinator.requestDeleteSubtitle(file) },
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
                    subtitleSearchCoordinator.performSubtitleSearch(playerSubtitleSearchQuery(currentVideo.path), "", "", coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en")
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
            onSyncClick = {
                coreUi.showSettings = false
                showSubtitleBloom = true
                studioCategory = com.sole.cinevault.subtitles.StudioCategory.POWER_TOOLS
                showControls = false
            },
            onStyleClick = { coreUi.showSettings = false; coreUi.showAppearanceStudio = true; showControls = false },
            onResetSubtitleSettings = { subtitleResetCoordinator.reset() },
            onSettingsUserInteraction = { studioUi.menuTouchKey++; showControls = true },
            trackSelectorBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            trackSelectorOffsetX = calculatePlayerPopupOffsetX(subIconX, trackStudioWidth, screenWidthPx, density),
            trackSelectorWidth = trackStudioWidth,
            trackSelectorMaxHeight = trackStudioMaxHeight,
            studioRightInset = studioFrameInset,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            embeddedTrackChoices = embeddedTrackChoices,
            downloadedTrackChoice = downloadedTrackChoice,
            localFileChoices = localFileChoices,
            generatedSubtitleFiles = generatedSubtitleFiles.filter { g ->
                java.io.File(g.uri.path ?: "").absolutePath !in pendingDeletePaths
            },
            selectedTrackKey = trackUi.selectedKey,
            onSelectTrack = { choice -> subtitleSearchCoordinator.selectSubtitleTrack(choice); trackUi.showSelector = false; showControls = true },
            onDeleteLocalTrack = { file -> subtitleDeletionCoordinator.requestDeleteSubtitle(file) },
            onDeleteGeneratedTrack = { generated ->
                val path = generated.uri.path
                if (path != null) subtitleDeletionCoordinator.requestDeleteSubtitle(java.io.File(path))
            },
            onOpenFilePickerFromTrackSelector = { trackUi.showSelector = false; srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
            onBackFromTrackSelector = {
                trackUi.showSelector = false
                showSubtitleBloom = true
                studioCategory = null
                showControls = false
                showTopBar = false
            },
            onDismissTrackSelector = { trackUi.showSelector = false; showControls = true },
            onTrackSelectorUserInteraction = { studioUi.menuTouchKey++ },
            initialManageMode = trackSelectorManageMode,
        )

        val subtitleSearchLayout = calculateSubtitleSearchLayout(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isLandscape = isLandscape,
            isCompactLandscape = isCompactLandscape
        )
        val searchWidth = (subtitleSearchLayout.width * 1.12f)
            .coerceAtMost((visibleMovieWidth - studioFrameInset * 2).coerceAtLeast(280.dp))
        val searchMaxHeight = (subtitleSearchLayout.maxHeight * 1.10f)
            .coerceAtMost((maxHeight - 24.dp).coerceAtLeast(280.dp))
        val subtitleWebQuery = playerSubtitleSearchQuery(currentVideo.path)
        SubtitleAcquisitionFlow(
            showSubtitleSearch = searchUi.showSearch,
            searchWidth = searchWidth,
            searchMaxHeight = searchMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            studioRightInset = studioFrameInset,
            initialSearchQuery = remember(currentVideo.path) { playerSubtitleSearchQuery(currentVideo.path) },
            searchResults = searchUi.searchResults,
            isSearching = searchUi.searchLoading,
            searchStatusText = searchUi.searchStatus,
            onSearchUserInteraction = { studioUi.menuTouchKey++ },
            onSearch = { q, s, e -> studioUi.menuTouchKey++; subtitleSearchCoordinator.performSubtitleSearch(q, s, e, coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en") },
            onDownloadAndApply = { result -> studioUi.menuTouchKey++; subtitleSearchCoordinator.applySearchResult(result, alsoPlay = true) },
            onDownloadOnly = { result -> studioUi.menuTouchKey++; subtitleSearchCoordinator.applySearchResult(result, alsoPlay = false) },
            onWebsiteFallbackFromSearch = { searchUi.showSearch = false; searchUi.showFallback = true; showControls = false },
            onBackFromSearch = {
                searchUi.showSearch = false
                showSubtitleBloom = true
                studioCategory = null
                showControls = false
                showTopBar = false
            },
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
            onBackFromFallback = {
                searchUi.showFallback = false
                showSubtitleBloom = true
                studioCategory = null
                showControls = false
                showTopBar = false
            },
            onDismissFallback = { searchUi.showFallback = false },
            showEmbeddedSubtitleBrowser = searchUi.showEmbeddedBrowser,
            embeddedBrowserQuery = playerSubtitleSearchQuery(currentVideo.path),
            embeddedBrowserPreferredLanguage = coreUi.behaviorPrefs.preferredLanguages.firstOrNull() ?: "en",
            onImported = { result ->
                if (result.alternatives.isEmpty()) {
                    subtitleSearchCoordinator.applyImportedWebsiteSubtitle(result.selected)
                } else {
                    searchUi.pendingImportCandidates = result
                    searchUi.showEmbeddedBrowser = false
                }
            },
            onMessage = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
            onDismissEmbeddedBrowser = { searchUi.showEmbeddedBrowser = false; showControls = true },
            pendingImportedCandidates = searchUi.pendingImportCandidates,
            onCandidateSelected = { subtitleSearchCoordinator.applyImportedWebsiteSubtitle(it) },
            onDismissCandidateSheet = { searchUi.pendingImportCandidates = null },
        )

        SubtitleSyncAndAppearancePopups(
            dialogueSyncArmed = coreUi.dialogueSyncArmed,
            isLandscape = isLandscape,
            onDialogueSyncTap = { subtitleSyncTools.confirmDialogueSyncTap() },
            onDialogueSyncCancel = { subtitleSyncTools.cancelDialogueSync() },
            showDriftDialog = driftUi.showDialog,
            driftPopupWidth = playerDriftPopupWidth(trackSelectorWidth),
            videoDurationMs = duration,
            currentPositionMs = position,
            driftPointA = driftUi.pointA,
            driftPointB = driftUi.pointB,
            onMarkPointA = { correction -> subtitleSyncTools.markDriftPointA(correction) },
            onMarkPointB = { correction -> subtitleSyncTools.markDriftPointB(correction) },
            onApplyDrift = { subtitleSyncTools.applyDriftFix() },
            onDismissDrift = { driftUi.showDialog = false; showControls = false; showTopBar = false },
            showAppearanceStudio = coreUi.showAppearanceStudio,
            appearanceBottomPadding = playerPopupBottomPadding(popupBottomPadding),
            appearanceOffsetX = calculatePlayerPopupOffsetX(subIconX, styleStudioWidth, screenWidthPx, density),
            appearancePopupWidth = styleStudioWidth,
            appearancePopupMaxHeight = styleStudioMaxHeight,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            studioRightInset = studioFrameInset,
            appearancePresetName = appearanceUi.preset,
            appearance = appearanceUi.appearance,
            appearanceFontSizeSp = appearanceUi.textSizeSp,
            onAppearanceFontSizeChange = { appearanceUi.textSizeSp = it },
            appearanceBottomPaddingFraction = appearanceUi.bottomPadding,
            onAppearanceBottomPaddingChange = { appearanceUi.bottomPadding = it },
            onApplyPreset = { name, preset -> appearanceUi.preset = name; appearanceUi.appearance = preset },
            onForegroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(foregroundColor = c) },
            onEdgeTypeChange = { t -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeType = t) },
            onEdgeColorChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(edgeColor = c) },
            onBackgroundChange = { c -> appearanceUi.preset = "Custom"; appearanceUi.appearance = appearanceUi.appearance.copy(backgroundColor = c) },
            isAssOrSsaFormat = isAssOrSsaFormat,
            preserveOriginalStyling = appearanceUi.preserveOriginalStyling,
            onPreserveOriginalStylingChange = { appearanceUi.preserveOriginalStyling = it },
            onBackFromAppearanceStudio = {
                coreUi.showAppearanceStudio = false
                showSubtitleBloom = true
                studioCategory = null
                showControls = false
                showTopBar = false
            },
            onDismissAppearanceStudio = { coreUi.showAppearanceStudio = false; showControls = true },
            onAppearanceUserInteraction = { studioUi.menuTouchKey++ },
        )

        // Subtitle Studio now uses only the dedicated overlay windows.
        val subtitleOverlayActive =
            coreUi.showSettings ||
            trackUi.showSelector ||
            searchUi.showSearch ||
            driftUi.showDialog ||
            coreUi.showAppearanceStudio ||
            coreUi.dialogueSyncArmed ||
            showSubtitleDock ||
            showSubtitleBloom ||
            showDualSubsWindow ||
            showSubtitleBehaviourWindow ||
            showSpeechSubtitlePanel ||
            showSubtitleTranslationPanel

        val mainControlsVisible = !subtitleOverlayActive && shouldShowMainPlayerControls(
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
                        playerMenuCloseCoordinator.closeAll()
                        showSpeedMenu = !wasOpen
                        showControls = true
                    },
                    onSleepClick = {
                        val wasOpen = showSleepMenu
                        playerMenuCloseCoordinator.closeAll()
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

                val anyMenuOpenForSmartSkip = showAudioSelector || subtitleOverlayActive || showSpeedMenu || showSleepMenu || showSrtBrowser
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
                    showSubtitleActive = coreUi.showSettings || trackUi.showSelector || searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio,
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
                            playbackNavigationCoordinator.playNext()
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
                        playerMenuCloseCoordinator.closeAll()
                        showAudioSelector = !wasOpen
                        showControls = true
                        menuTouchKey++
                    },
                    onAudioCenterMeasured = { audioIconX = it },
                    onSubtitleClick = {
                        val wasOpen = showSubtitleDock || showSubtitleBloom || trackUi.showSelector || searchUi.showSearch || driftUi.showDialog || coreUi.showAppearanceStudio
                        playerMenuCloseCoordinator.closeAll()
                        showSubtitleDock = !wasOpen
                        if (showSubtitleDock) {
                            // "Only the HUD remains visible" — hide the
                            // transport dock/top bar immediately rather
                            // than leaving them up alongside it.
                            showControls = false
                            showTopBar = false
                        } else {
                            showControls = true
                        }
                        menuTouchKey++
                    },
                    onSubtitleLongClick = {
                        // 450ms hold threshold is enforced by combinedClickable's own
                        // long-press timing; this fires once that's satisfied.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        playerMenuCloseCoordinator.closeAll()
                        showSubtitleBloom = true
                        showControls = false
                        showTopBar = false
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
                        position = playerBoundedSeekPosition(pos, duration)
                        previewPosition = position
                        VideoThumbnailHelper.nearestPreviewFrame(previewFrames, previewPosition)?.let {
                            previewBitmap = it
                        }
                    },
                    onSeekFinished = { finalPos ->
                        val safe = playerBoundedSeekPosition(finalPos, duration)
                        position = safe
                        previewPosition = safe
                        exoPlayer.seekTo(safe)
                        isDraggingSeekbar = false
                        previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(previewFrames, safe) ?: previewBitmap
                        showSeekPreview = true
                        if (isStreamMedia) {
                            scope.launch {
                                delay(playerStreamSeekPreviewHideDelayMs())
                                if (!isDraggingSeekbar) showSeekPreview = false
                            }
                        } else {
                            scope.launch {
                                val bmp = VideoThumbnailHelper.generateFrameAtTime(context, currentVideo.path, safe)
                                if (bmp != null && previewPosition == safe) previewBitmap = bmp
                                delay(playerLocalSeekPreviewHideDelayMs())
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
            visible = !CineVaultPlayerHolder.isInPipMode,
            containerWidth = playerMaxWidth,
            containerHeight = playerMaxHeight,
            status = autoSyncStatus,
            onApply = { result -> autoSyncCoordinator.applyAutoSyncResult(result) },
            onCancel = { autoSyncStatus = AutoSyncStatus.Idle },
            onRetry = { autoSyncCoordinator.runAutoSync() }
        )

        PlayerSubtitleDeleteFeedback(
            pendingFile = pendingDeleteConfirmFile,
            snackbarHostState = snackbarHostState,
            snackbarBottomPadding = bottomDockPadding + playButton + 26.dp,
            onDismissDelete = { pendingDeleteConfirmFile = null },
            onConfirmDelete = { file ->
                pendingDeleteConfirmFile = null
                subtitleDeletionCoordinator.deleteWithUndo(file)
            }
        )

        // Tap CC -> Quick HUD (filename, Delay/Size/Position), draggable
        // anywhere in the player frame. Filename resolution mirrors the
        // same key-matching Track's selector already does — not invented
        // fresh here, so it can't quietly drift out of sync with what
        // Track actually shows as selected.
        val quickHudFileName = remember(
            trackUi.selectedKey, embeddedTrackChoices, downloadedTrackChoice,
            localFileChoices, generatedSubtitleFiles
        ) {
            val key = trackUi.selectedKey
            when {
                key == null || key == SubtitleTrackChoice.Off.key -> null
                downloadedTrackChoice?.key == key -> SubtitleLanguageRegistry.displayName(downloadedTrackChoice.language)
                else -> localFileChoices.firstOrNull { SubtitleTrackChoice.Local(it).key == key }?.name
                    ?: generatedSubtitleFiles.firstOrNull {
                        SubtitleTrackChoice.Generated(it, it.fileName.contains("-translated-")).key == key
                    }?.label
                    ?: embeddedTrackChoices.firstOrNull { it.key == key }
                        ?.let { SubtitleLanguageRegistry.displayName(it.language) }
            }
        }
        if (showSubtitleDock && !CineVaultPlayerHolder.isInPipMode && externalPlayerView == null) {
            val density = LocalDensity.current
            val containerPx = with(density) {
                androidx.compose.ui.unit.IntSize(playerMaxWidth.roundToPx(), playerMaxHeight.roundToPx())
            }
            val quickHudWidth = (playerMaxWidth * 0.33f).coerceIn(211.dp, 264.dp)
            val quickHudOffsetX = calculatePlayerPopupOffsetX(
                iconCenterX = subIconX,
                popupWidth = quickHudWidth,
                screenWidthPx = with(density) { playerMaxWidth.toPx() },
                density = density
            ).toFloat()
            // Rest directly above the transport area where CC lives. The
            // window is also clamped by DraggableStudioWindow after its
            // actual size is measured, so unusual aspect ratios stay safe.
            val quickHudOffsetY = with(density) {
                (playerMaxHeight - bottomDockPadding - playButton - 158.dp)
                    .coerceAtLeast(8.dp)
                    .toPx()
            }

            com.sole.cinevault.subtitles.QuickHud(
                subtitleFileName = quickHudFileName,
                delaySeconds = coreUi.syncOffset,
                onDelayChange = { coreUi.syncOffset = it; studioUi.menuTouchKey++ },
                speechTimeline = autoSyncSpeechTimeline,
                fontSizeSp = appearanceUi.textSizeSp,
                onFontSizeChange = { appearanceUi.textSizeSp = it; studioUi.menuTouchKey++ },
                bottomPadding = appearanceUi.bottomPadding,
                onBottomPaddingChange = { appearanceUi.bottomPadding = it; studioUi.menuTouchKey++ },
                onReset = { subtitleResetCoordinator.reset() },
                containerSize = containerPx,
                initialOffset = Offset(quickHudOffsetX, quickHudOffsetY),
                windowWidth = quickHudWidth
            )
        }

        // Long-press CC -> Studio pill (Download / Style / Power tools / Settings)
        if (showSubtitleBloom && !CineVaultPlayerHolder.isInPipMode && externalPlayerView == null) {
            val studioDensity = LocalDensity.current
            val studioContainerPx = with(studioDensity) {
                androidx.compose.ui.unit.IntSize(playerMaxWidth.roundToPx(), playerMaxHeight.roundToPx())
            }
            // Right side, not left. The pill is short (one row) so bottom-
            // anchoring it is safe height-wise, but list windows are much
            // taller — anchoring THOSE from the bottom with a large assumed
            // height risked going negative in landscape's shorter frame,
            // which is exactly why they showed up half off-screen at the
            // top with an unreachable drag handle. Anchoring from the top
            // with a small fixed margin instead avoids that class of bug
            // entirely, regardless of landscape vs portrait height.
            val studioSideMargin = if (playerMaxWidth < 700.dp) 10.dp else 18.dp
            val studioTopMargin = if (playerMaxHeight < 420.dp) 10.dp else 16.dp
            // Slice 14: Studio surfaces are ~10% larger and share one
            // comfortable right-centre resting zone inside the measured
            // player frame.
            val pillWidth = 242.dp
            val listWindowWidth = if (playerMaxWidth < 700.dp) 242.dp else 275.dp

            // Compact Studio surfaces always rest on the right side.  The
            // values are derived from the actual player frame instead of a
            // hard-coded phone assumption, and DraggableStudioWindow still
            // performs the final measured-size clamp after composition.
            val pillOffset = with(studioDensity) {
                val xDp = (playerMaxWidth - pillWidth - studioSideMargin)
                    .coerceAtLeast(studioSideMargin)
                val yDp = (playerMaxHeight * 0.5f - 28.dp)
                    .coerceIn(studioTopMargin, (playerMaxHeight - 62.dp).coerceAtLeast(studioTopMargin))
                Offset(xDp.toPx(), yDp.toPx())
            }
            val windowOffset = with(studioDensity) {
                val xDp = (playerMaxWidth - listWindowWidth - studioSideMargin)
                    .coerceAtLeast(studioSideMargin)
                val estimatedWindowHeight = if (playerMaxHeight < 420.dp) 250.dp else 290.dp
                val yDp = (playerMaxHeight * 0.5f - estimatedWindowHeight * 0.5f)
                    .coerceAtLeast(studioTopMargin)
                Offset(xDp.toPx(), yDp.toPx())
            }

            when (studioCategory) {
                null -> com.sole.cinevault.subtitles.SubtitleStudioPill(
                    activeCategory = null,
                    onCategorySelected = { subtitleStudioNavigation.onStudioCategoryTapped(it) },
                    containerSize = studioContainerPx,
                    initialOffset = pillOffset
                )
                com.sole.cinevault.subtitles.StudioCategory.DOWNLOAD -> com.sole.cinevault.subtitles.StudioListWindow(
                    title = "Download",
                    onBack = { studioCategory = null },
                    containerSize = studioContainerPx,
                    initialOffset = windowOffset,
                    items = listOf(
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.Manage,
                            label = "Manage",
                            onClick = { trackSelectorManageMode = true; trackUi.showSelector = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.Tracks,
                            label = "Tracks",
                            onClick = { trackSelectorManageMode = false; trackUi.showSelector = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.Web,
                            label = "Web",
                            onClick = { searchUi.showFallback = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.SmartSearch,
                            label = "Smart search",
                            onClick = { searchUi.showSearch = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.AutoDownload,
                            label = "Auto download",
                            toggledOn = coreUi.behaviorPrefs.autoDownloadWhenMissing,
                            onClick = {
                                coreUi.behaviorPrefs = coreUi.behaviorPrefs.copy(autoDownloadWhenMissing = !coreUi.behaviorPrefs.autoDownloadWhenMissing)
                                saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                            }
                        )
                    )
                )
                com.sole.cinevault.subtitles.StudioCategory.POWER_TOOLS -> com.sole.cinevault.subtitles.StudioListWindow(
                    title = "Power tools",
                    onBack = { studioCategory = null },
                    containerSize = studioContainerPx,
                    initialOffset = windowOffset,
                    items = listOf(
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.SpeechToSubs,
                            label = "Speech to subs",
                            onClick = { showSpeechSubtitlePanel = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.AiTranslate,
                            label = "AI translate",
                            onClick = { showSubtitleTranslationPanel = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.AutoSync,
                            label = "Auto sync",
                            onClick = { autoSyncCoordinator.runAutoSync(); showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.DialogueSync,
                            label = "Dialogue sync",
                            onClick = { subtitleSyncTools.armDialogueSync(); showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.DriftSync,
                            label = "Drift sync",
                            onClick = { driftUi.showDialog = true; showSubtitleBloom = false; studioCategory = null }
                        ),
                        com.sole.cinevault.subtitles.StudioListItem(
                            icon = com.sole.cinevault.subtitles.StudioRowIcons.DualSubs,
                            label = "Dual subs",
                            onClick = { showDualSubsWindow = true; showSubtitleBloom = false; studioCategory = null }
                        )
                    )
                )
                else -> Unit
            }
        }

        if (showSubtitleBehaviourWindow && !CineVaultPlayerHolder.isInPipMode && externalPlayerView == null) {
            val settingsDensity = LocalDensity.current
            val settingsContainerPx = with(settingsDensity) {
                androidx.compose.ui.unit.IntSize(playerMaxWidth.roundToPx(), playerMaxHeight.roundToPx())
            }
            com.sole.cinevault.subtitles.SubtitleBehaviourWindow(
                prefs = coreUi.behaviorPrefs,
                onChange = {
                    coreUi.behaviorPrefs = it
                    saveSubtitleBehaviorPrefs(context, it)
                    studioUi.menuTouchKey++
                },
                cleaningOptions = coreUi.cleaningOptions,
                onCleaningOptionsChange = {
                    coreUi.cleaningOptions = it
                    saveSubtitleCleaningOptions(context, it)
                    studioUi.menuTouchKey++
                },
                onBack = {
                    showSubtitleBehaviourWindow = false
                    showSubtitleBloom = true
                    studioCategory = null
                },
                containerSize = settingsContainerPx,
                initialOffset = with(settingsDensity) {
                    val margin = if (playerMaxWidth < 700.dp) 10.dp else 18.dp
                    val width = if (playerMaxWidth < 700.dp) 290.dp else 330.dp
                    val top = if (playerMaxHeight < 420.dp) 10.dp else 16.dp
                    Offset(
                        (playerMaxWidth - width - margin).coerceAtLeast(margin).toPx(),
                        top.toPx()
                    )
                },
                onUserInteraction = { studioUi.menuTouchKey++ }
            )
        }

        if (showDualSubsWindow && !CineVaultPlayerHolder.isInPipMode && externalPlayerView == null) {
            val density3 = LocalDensity.current
            val containerPx3 = with(density3) {
                androidx.compose.ui.unit.IntSize(playerMaxWidth.roundToPx(), playerMaxHeight.roundToPx())
            }
            val languages = SubtitleLanguageRegistry.allLanguages()
            com.sole.cinevault.subtitles.DualSubsWindow(
                enabled = dualUi.enabled,
                onEnabledChange = { enabled ->
                    dualUi.enabled = enabled
                    if (enabled) subtitleSyncTools.fetchAndApplyDualSecondary() else subtitleSyncTools.disableDualSubtitles()
                    studioUi.menuTouchKey++
                },
                canEnable = trackUi.primaryUri != null,
                primaryLabel = quickHudFileName ?: "None",
                secondaryLanguage = dualUi.secondaryLanguage,
                secondaryLanguageLabel = languages.firstOrNull { it.first == dualUi.secondaryLanguage }?.second ?: dualUi.secondaryLanguage.uppercase(),
                onSecondaryLanguageChange = { lang ->
                    pendingDualAiLanguage = null
                    dualUi.secondaryLanguage = lang
                    coreUi.behaviorPrefs = coreUi.behaviorPrefs.copy(dualSecondaryLanguage = lang)
                    saveSubtitleBehaviorPrefs(context, coreUi.behaviorPrefs)
                    if (dualUi.enabled) subtitleSyncTools.fetchAndApplyDualSecondary()
                    studioUi.menuTouchKey++
                },
                availableLanguages = languages,
                gapLines = dualUi.gapLines,
                onGapLinesChange = { gap ->
                    dualUi.gapLines = gap
                    if (dualUi.enabled) subtitleSyncTools.fetchAndApplyDualSecondary()
                    studioUi.menuTouchKey++
                },
                secondaryColorHex = dualSecondaryColorHex,
                onSecondaryColorChange = { color ->
                    dualSecondaryColorHex = color
                    if (dualUi.enabled) subtitleSyncTools.fetchAndApplyDualSecondary()
                    studioUi.menuTouchKey++
                },
                // Covers interactions that don't change a value (e.g. just
                // opening the language picker) — those still count as
                // "actively using this window" and should reset the idle
                // timer the same as a value change would.
                onUserInteraction = { studioUi.menuTouchKey++ },
                statusText = dualUi.statusText,
                secondarySourceLabel = dualUi.secondarySourceLabel,
                onBack = { showDualSubsWindow = false; showSubtitleBloom = true },
                containerSize = containerPx3,
                initialOffset = with(density3) {
                    val margin = if (playerMaxWidth < 700.dp) 10.dp else 18.dp
                    val width = if (playerMaxWidth < 700.dp) 240.dp else 270.dp
                    val top = if (playerMaxHeight < 420.dp) 10.dp else 16.dp
                    Offset(
                        (playerMaxWidth - width - margin).coerceAtLeast(margin).toPx(),
                        top.toPx()
                    )
                }
            )
        }

        // AI sheet removed — Speech to subs / AI translate are now direct
        // rows inside the Power Tools list window, so this intermediate
        // sheet was a third path to the same two actions.

        // Speech to subs / AI Translate are reachable through the Bloom's
        // AI sheet now (same showSpeechSubtitlePanel/showSubtitleTranslationPanel
        // flags below) — this standalone button row was the original, unstyled
        // entry point and is removed rather than left as a second door to the
        // same two actions.

        // Slice 50: floating subtitle AI job pills and both draggable AI
        // panels are now one cohesive presentation component.
        PlayerSubtitleAiPanels(
            context = context,
            containerWidth = playerMaxWidth,
            containerHeight = playerMaxHeight,
            isInPipMode = CineVaultPlayerHolder.isInPipMode,
            externalDisplayActive = externalPlayerView != null,
            speechJobLabel = speechJobLabel,
            speechJobProgress = speechJobProgress,
            translationJobLabel = translationJobLabel,
            translationJobProgress = translationJobProgress,
            showSpeechPanel = showSpeechSubtitlePanel,
            showTranslationPanel = showSubtitleTranslationPanel,
            speechStatus = speechSubtitleStatus,
            translationStatus = subtitleTranslationStatus,
            generatedFiles = generatedSubtitleFiles,
            activeSubtitleUri = trackUi.primaryUri ?: trackUi.originalUri,
            speechCoordinator = speechSubtitleCoordinator,
            translationCoordinator = subtitleTranslationCoordinator,
            generatedSubtitleOrchestrator = generatedSubtitleOrchestrator,
            onShowSpeechPanel = {
                showSubtitleTranslationPanel = false
                showSpeechSubtitlePanel = true
            },
            onHideSpeechPanel = {
                showSpeechSubtitlePanel = false
            },
            onShowTranslationPanel = {
                showSpeechSubtitlePanel = false
                showSubtitleTranslationPanel = true
            },
            onHideTranslationPanel = {
                showSubtitleTranslationPanel = false
            },
        )



    }
}
