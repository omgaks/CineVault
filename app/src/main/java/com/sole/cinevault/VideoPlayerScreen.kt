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

    // FIX: preserve the device's real starting music volume instead of
    // forcing an arbitrary value when CineVault opens.
    val initialMusicVolumePercent = remember {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playerInitialMusicVolumePercent(am)
    }

    // Slice 70: core player chrome, HUD and transient menu visibility now
    // share one stable UI-state holder.
    val chromeUi = remember { PlayerChromeUiState(initialMusicVolumePercent) }
    val trackUi = remember { SubtitleTrackSelectionState() }
    val searchUi = remember { SubtitleAcquisitionUiState() }

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

    // Slice 66: zoom/pan, seek-preview and edge-swipe gesture state now
    // live in one stable holder shared by the gesture and controls layers.
    val gestureUi = remember { PlayerGestureUiState() }

    // Slice 67: buffering/error recovery and dropped-frame recovery counters
    // now live in one stable playback-health state holder.
    val playbackHealth = remember { PlayerPlaybackHealthState() }

    // Playback Resilience Slice 73: recovery-engine state is per-video.
    // A software fallback decision for one file must never carry into another.
    val playbackRecovery = remember(currentVideo.path) {
        PlayerPlaybackRecoveryState()
    }

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
            closeAudioSelector = { chromeUi.showAudioSelector = false },
            closeSettings = { coreUi.showSettings = false },
            closeDriftDialog = { driftUi.showDialog = false },
            closeSpeedMenu = { chromeUi.showSpeedMenu = false },
            closeSleepMenu = { chromeUi.showSleepMenu = false },
            closeSrtBrowser = { chromeUi.showSrtBrowser = false },
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

    // Slice 62: playback session actions, sleep ticking, navigation, and
    // subtitle search coordination now share one session-coordinator host.
    val sessionCoordinators = rememberPlayerSessionCoordinators(
        context = context,
        scope = scope,
        player = exoPlayer,
        trackSelector = trackSelector,
        haptics = haptics,
        episodeList = episodeList,
        currentVideo = currentVideo,
        isStreamMedia = isStreamMedia,
        playbackSpeed = playbackSpeed,
        sleepTimerActive = sleepTimerActive,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        trackUi = trackUi,
        coreUi = coreUi,
        searchUi = searchUi,
        studioUi = studioUi,
        onPlaybackSpeedChanged = { playbackSpeed = it },
        onSleepTimerMinutesChanged = { sleepTimerMinutes = it },
        onSleepTimerActiveChanged = { sleepTimerActive = it },
        onSleepTimerRemainingMsChanged = { sleepTimerRemainingMs = it },
        onShowSpeedMenuChanged = { chromeUi.showSpeedMenu = it },
        onShowSleepMenuChanged = { chromeUi.showSleepMenu = it },
        onShowControlsChanged = { chromeUi.showControls = it },
        onCurrentVideoChanged = { currentVideo = it },
        onCurrentMediaTypeChanged = { currentMediaType = it },
        onEdgeSwipeHintChanged = { gestureUi.edgeSwipeHint = it },
        onPlayerErrorMessageChanged = { playbackHealth.playerErrorMessage = it },
        onVideoEndedChanged = { isVideoEnded = it },
        onPendingSrtUriChanged = { pendingSrtUri = it },
        onPlayNext = onPlayNext,
    )
    val playerSessionActionsCoordinator = sessionCoordinators.sessionActions
    val playbackNavigationCoordinator = sessionCoordinators.navigation
    val subtitleSearchCoordinator = sessionCoordinators.subtitleSearch

    fun playCurrentVideoWithSubtitle(
        subtitleUri: Uri? = null,
        resumePosition: Long = 0L,
        isOriginalSubtitle: Boolean = true,
    ) = playbackNavigationCoordinator.playCurrentVideoWithSubtitle(
        subtitleUri,
        resumePosition,
        isOriginalSubtitle,
    )

    // Slice 63: subtitle file deletion/undo/OS consent and local subtitle
    // import/picker handling now live in one responsibility-owned runtime.
    val subtitleFileRuntime = rememberPlayerSubtitleFileRuntime(
        context = context,
        scope = scope,
        player = exoPlayer,
        playbackNavigationCoordinator = playbackNavigationCoordinator,
        subtitleSearchCoordinator = subtitleSearchCoordinator,
        trackUi = trackUi,
        coreUi = coreUi,
        searchUi = searchUi,
        currentVideoPath = currentVideo.path,
    )
    val pendingDeletePaths = subtitleFileRuntime.pendingDeletePaths
    val pendingDeleteConfirmFile = subtitleFileRuntime.pendingDeleteFileState.value
    val snackbarHostState = subtitleFileRuntime.snackbarHostState
    val subtitleDeletionCoordinator = subtitleFileRuntime.deletionCoordinator

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
            setShowControls = { chromeUi.showControls = it },
            setShowTopBar = { chromeUi.showTopBar = it },
            setShowAudioSelector = { chromeUi.showAudioSelector = it },
            setShowSpeedMenu = { chromeUi.showSpeedMenu = it },
            setShowSleepMenu = { chromeUi.showSleepMenu = it },
            setShowSrtBrowser = { chromeUi.showSrtBrowser = it },
            setPendingNextEpisode = { pendingNextEpisode = it },
            setNextEpisodeCountdown = { nextEpisodeCountdown = it },
            setShowNextEpisodeOverlay = { showNextEpisodeOverlay = it },
            setNextEpisodeDismissed = { nextEpisodeDismissed = it },
            setSmartSegmentResult = { smartSegmentResult = it },
            setPreviewBitmap = { gestureUi.previewBitmap = it },
            clearPreviewFrames = { gestureUi.previewFrames = emptyList() },
            setIsVideoEnded = { isVideoEnded = it },
            setPlayerErrorMessage = { playbackHealth.playerErrorMessage = it },
            setErrorRetryCount = { playbackHealth.errorRetryCount = it },
            setStuckBufferingHint = { playbackHealth.stuckBufferingHint = it },
            setAudioLanguageCheckedForPath = { audioLanguageCheckedForPath = it },
            setDualSecondaryColorHex = { dualSecondaryColorHex = it },
            setAutoSyncStatus = { autoSyncStatus = it },
            setAutoSyncSpeechTimeline = { autoSyncSpeechTimeline = it },
            setDroppedFrameNudgeCount = { playbackHealth.droppedFrameNudgeCount = it },
            setLastNudgeAtMs = { playbackHealth.lastNudgeAtMs = it },
        ),
    )

    // Slice 54: playback lifecycle/listener/timeline/PiP/auto-hide wiring now
    // lives in one runtime-effects host. VideoPlayerScreen still owns the UI
    // state; this helper owns the Compose side-effect cluster around the player.
    PlayerRuntimeEffects(
        context = context,
        activity = activity,
        scope = scope,
        player = exoPlayer,
        trackSelector = trackSelector,
        currentVideoPath = currentVideo.path,
        currentMediaType = currentMediaType,
        isStreamMedia = isStreamMedia,
        episodeList = episodeList,
        autoPlayEnabled = autoPlayEnabled,
        errorRetryCount = playbackHealth.errorRetryCount,
        playbackEngineMode = playbackRecovery.engineMode,
        softwareFallbackAvailable = playbackRecovery.softwareFallbackAvailable,
        coreUi = coreUi,
        trackUi = trackUi,
        searchUi = searchUi,
        driftUi = driftUi,
        studioUi = studioUi,
        audioLanguageCheckedForPath = audioLanguageCheckedForPath,
        isDraggingSeekbar = chromeUi.isDraggingSeekbar,
        isBuffering = playbackHealth.isBuffering,
        showSeekPreview = gestureUi.showSeekPreview,
        previewPosition = gestureUi.previewPosition,
        duration = duration,
        previewReloadKey = gestureUi.previewReloadKey,
        droppedFrameNudgeCount = playbackHealth.droppedFrameNudgeCount,
        lastNudgeAtMs = playbackHealth.lastNudgeAtMs,
        isPlaying = isPlaying,
        showControls = chromeUi.showControls,
        showTopBar = chromeUi.showTopBar,
        controlsLocked = chromeUi.controlsLocked,
        lockButtonVisibleWhileLocked = chromeUi.lockButtonVisibleWhileLocked,
        showAudioSelector = chromeUi.showAudioSelector,
        showSpeedMenu = chromeUi.showSpeedMenu,
        showSleepMenu = chromeUi.showSleepMenu,
        showSrtBrowser = chromeUi.showSrtBrowser,
        menuTouchKey = menuTouchKey,
        brightnessGestureKey = chromeUi.brightnessGestureKey,
        volumeGestureKey = chromeUi.volumeGestureKey,
        showSubtitleDock = showSubtitleDock,
        showSubtitleBloom = showSubtitleBloom,
        showDualSubsWindow = showDualSubsWindow,
        onNextRequested = { playbackNavigationCoordinator.playNext() },
        onPreviousRequested = { playbackNavigationCoordinator.playPrevious() },
        onInitialBrightnessChanged = { chromeUi.brightnessPercent = it },
        onAudioLanguageCheckedForPathChanged = { audioLanguageCheckedForPath = it },
        onBufferingChanged = { playbackHealth.isBuffering = it },
        onErrorRetryCountChanged = { playbackHealth.errorRetryCount = it },
        onPlayerErrorMessageChanged = { playbackHealth.playerErrorMessage = it },
        onVideoEndedChanged = { isVideoEnded = it },
        onPlayingChanged = { isPlaying = it },
        onQueueNextEpisode = { next ->
            pendingNextEpisode = next
            nextEpisodeCountdown = 15
            showNextEpisodeOverlay = true
            chromeUi.showControls = true
            chromeUi.showTopBar = true
        },
        onAdvanceImmediately = { next ->
            currentMediaType = next.type
            currentVideo = next.video
            onPlayNext(next)
        },
        onShowControlsAndTopBar = {
            chromeUi.showControls = true
            chromeUi.showTopBar = true
        },
        onRetryPlayback = { subtitleUri, resumePosition ->
            playCurrentVideoWithSubtitle(
                subtitleUri = subtitleUri,
                resumePosition = resumePosition,
                isOriginalSubtitle = false,
            )
        },
        onSoftwareFallbackRequested = { errorCode, resumePosition, _ ->
            playbackRecovery.requestSoftwareFallback(
                errorCode = errorCode,
                resumePositionMs = resumePosition,
            )
        },
        onPositionChanged = { position = it },
        onDurationChanged = { duration = it },
        onBufferingSpinnerChanged = { playbackHealth.showBufferingSpinner = it },
        onStuckBufferingChanged = { playbackHealth.stuckBufferingHint = it },
        onDroppedFrameNudgeCountChanged = { playbackHealth.droppedFrameNudgeCount = it },
        onLastNudgeAtMsChanged = { playbackHealth.lastNudgeAtMs = it },
        onPreviewFramesChanged = { gestureUi.previewFrames = it },
        onPreviewBitmapChanged = { gestureUi.previewBitmap = it },
        onSeekPreviewLargeChanged = { gestureUi.isSeekPreviewLarge = it },
        onEnteredPip = { playerMenuCloseCoordinator.closeAll() },
        onHideControls = { chromeUi.showControls = false },
        onHideTopBar = { chromeUi.showTopBar = false },
        onHideLockedButton = { chromeUi.lockButtonVisibleWhileLocked = false },
        onHideAudioSelector = { chromeUi.showAudioSelector = false },
        onHideSpeedMenu = { chromeUi.showSpeedMenu = false },
        onHideSleepMenu = { chromeUi.showSleepMenu = false },
        onHideSrtBrowser = { chromeUi.showSrtBrowser = false },
        onHideBrightnessHud = { chromeUi.showBrightnessCircle = false },
        onHideVolumeHud = { chromeUi.showVolumeCircle = false },
        onHideSubtitleDock = { showSubtitleDock = false },
        onHideSubtitleBloom = {
            showSubtitleBloom = false
            studioCategory = null
        },
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
        onShowControls = { chromeUi.showControls = true },
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
            resetPreviewFrames = { gestureUi.previewFrames = emptyList(); gestureUi.previewBitmap = null },
            incrementPreviewReloadKey = { gestureUi.previewReloadKey++ },
            setSyncOffsetSeconds = { coreUi.syncOffset = it },
            setDriftScale = { driftUi.scale = it },
            incrementStudioMenuTouchKey = { studioUi.menuTouchKey++ },
            setSpeechTimeline = { autoSyncSpeechTimeline = it }
        )
    }
    // Slice 68: Speech-to-Subs / translation presentation state and generated
    // subtitle refresh state now live together in one per-video AI state holder.
    val subtitleAiUi = remember(currentVideo.path) { PlayerSubtitleAiUiState() }

    // Slice 52: generated subtitle / Speech-to-Subs / AI Translation runtime
    // orchestration now lives outside the giant player composable. The player
    // still owns the visible state values; the helper owns loading, coordinators,
    // pending Dual AI processing, job presentation, and AI-panel Back handling.
    val subtitleAiRuntime = rememberPlayerSubtitleAiRuntime(
        context = context,
        scope = scope,
        exoPlayer = exoPlayer,
        currentVideoPath = currentVideo.path,
        trackUi = trackUi,
        coreUi = coreUi,
        dualUi = dualUi,
        subtitleSyncTools = subtitleSyncTools,
        speechSubtitleStatus = subtitleAiUi.speechSubtitleStatus,
        onSpeechSubtitleStatusChanged = { subtitleAiUi.speechSubtitleStatus = it },
        subtitleTranslationStatus = subtitleAiUi.subtitleTranslationStatus,
        onSubtitleTranslationStatusChanged = { subtitleAiUi.subtitleTranslationStatus = it },
        pendingDualAiLanguage = pendingDualAiLanguage,
        onPendingDualAiLanguageChanged = { pendingDualAiLanguage = it },
        generatedSubtitleRefreshKey = subtitleAiUi.generatedSubtitleRefreshKey,
        onGeneratedSubtitleRefreshRequested = { subtitleAiUi.generatedSubtitleRefreshKey++ },
        onGeneratedSubtitleFilesLoaded = { subtitleAiUi.generatedSubtitleFiles = it },
        showSpeechSubtitlePanel = subtitleAiUi.showSpeechSubtitlePanel,
        showSubtitleTranslationPanel = subtitleAiUi.showSubtitleTranslationPanel,
        onShowSpeechSubtitlePanelChanged = { subtitleAiUi.showSpeechSubtitlePanel = it },
        onShowSubtitleTranslationPanelChanged = { subtitleAiUi.showSubtitleTranslationPanel = it },
        onTranslationSuccessLanguageChanged = { subtitleAiUi.translationSuccessLanguage = it },
        playCurrentVideoWithSubtitle = { uri, resumeAt ->
            playCurrentVideoWithSubtitle(
                uri,
                resumePosition = resumeAt,
                isOriginalSubtitle = true,
            )
        },
    )
    val generatedSubtitleOrchestrator = subtitleAiRuntime.generatedSubtitleOrchestrator
    val speechSubtitleCoordinator = subtitleAiRuntime.speechSubtitleCoordinator
    val subtitleTranslationCoordinator = subtitleAiRuntime.subtitleTranslationCoordinator
    val speechJobLabel = subtitleAiRuntime.speechJobLabel
    val speechJobProgress = subtitleAiRuntime.speechJobProgress
    val translationJobLabel = subtitleAiRuntime.translationJobLabel
    val translationJobProgress = subtitleAiRuntime.translationJobProgress

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
        val playerLayout = calculatePlayerSurfaceLayout(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            videoWidth = exoPlayer.videoSize.width,
            videoHeight = exoPlayer.videoSize.height,
            pixelWidthHeightRatio = exoPlayer.videoSize.pixelWidthHeightRatio,
            density = LocalDensity.current,
        )
        // Slice 65: display-profile selection, per-movie subtitle memory
        // effects and subtitle-reset wiring now live in one display runtime.
        val subtitleResetCoordinator = rememberPlayerSubtitleDisplayRuntime(
            context = context,
            externalDisplayConnected = externalDisplay.isConnected,
            isSmallPhone = playerLayout.isSmallPhone,
            isLandscape = playerLayout.isLandscape,
            videoPath = currentVideo.path,
            movieSubtitleMemory = movieSubtitleMemory,
            movieSubtitleMemoryReady = movieSubtitleMemoryReady,
            movieAppearanceMemoryReady = movieAppearanceMemoryReady,
            coreUi = coreUi,
            trackUi = trackUi,
            dualUi = dualUi,
            appearanceUi = appearanceUi,
            driftUi = driftUi,
            dualSecondaryColorHex = dualSecondaryColorHex,
            onMovieAppearanceMemoryReadyChanged = {
                movieAppearanceMemoryReady = it
            },
            setAudioSyncMs = { audioSyncMs = it },
            setShowControls = { chromeUi.showControls = it },
            incrementMenuTouchKey = { studioUi.menuTouchKey++ },
        )

        // Slice 69: downstream surfaces now consume the immutable
        // PlayerSurfaceLayout snapshot directly instead of unpacking 20+ aliases.

        // Slice 61: playlist navigation, Smart Segment runtime, credits-driven
        // next-episode triggering, and countdown completion now live together.
        val episodeRuntime = rememberPlayerEpisodeRuntime(
            smartSegmentRepository = smartSegmentRepository,
            currentVideo = currentVideo,
            episodeList = episodeList,
            isCurrentTvShow = isCurrentTvShow,
            isRestrictedFolderMedia = isRestrictedFolderMedia,
            smartSegmentResult = smartSegmentResult,
            duration = duration,
            position = position,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            nextEpisodeDismissed = nextEpisodeDismissed,
            pendingNextEpisode = pendingNextEpisode,
            isPlaying = isPlaying,
            isVideoEnded = isVideoEnded,
            onSmartSegmentResultChanged = { smartSegmentResult = it },
            onPendingNextEpisodeChanged = { pendingNextEpisode = it },
            onNextEpisodeCountdownChanged = { nextEpisodeCountdown = it },
            onShowNextEpisodeOverlayChanged = { showNextEpisodeOverlay = it },
            onCurrentMediaTypeChanged = { currentMediaType = it },
            onCurrentVideoChanged = { currentVideo = it },
            onPlayNext = onPlayNext,
        )
        val currentMeta = episodeRuntime.currentMeta
        val activeSmartSegment = episodeRuntime.activeSmartSegment
        val exactSceneSegment = episodeRuntime.exactSceneSegment
        val creditsSegment = episodeRuntime.creditsSegment
        val showPrevNextButtons = episodeRuntime.showPrevNextButtons
        val hasNextVideo = episodeRuntime.hasNextVideo

        PlayerVideoSurface(
            player = exoPlayer,
            externalDisplayActive = externalPlayerView != null,
            isZoomMode = gestureUi.isZoomMode,
            videoScale = gestureUi.videoScale,
            videoOffsetX = gestureUi.videoOffsetX,
            videoOffsetY = gestureUi.videoOffsetY,
            onPlayerViewChanged = { pv ->
                localPlayerView = pv
                studioUi.playerView = externalPlayerView ?: pv
            },
            onResizeModeChanged = { resizeMode ->
                externalPresentation?.updateResizeMode(resizeMode)
            }
        )

        // Slice 55: tablet + glasses playback gesture orchestration now lives
        // outside VideoPlayerScreen. This keeps the giant composable focused on
        // presentation while preserving the exact gesture callbacks and state.
        PlayerPlaybackGestureLayer(
            context = context,
            activity = activity,
            player = exoPlayer,
            audioManager = audioManager,
            externalDisplayActive = externalPlayerView != null,
            currentVideoPath = currentVideo.path,
            episodeList = episodeList,
            isLandscape = playerLayout.isLandscape,
            canChangeEpisode = showPrevNextButtons,
            previewFrames = gestureUi.previewFrames,
            previewPosition = gestureUi.previewPosition,
            previewBitmap = gestureUi.previewBitmap,
            brightnessPercent = chromeUi.brightnessPercent,
            volumePercent = chromeUi.volumePercent,
            videoScale = gestureUi.videoScale,
            videoOffsetX = gestureUi.videoOffsetX,
            videoOffsetY = gestureUi.videoOffsetY,
            screenWidthPx = playerLayout.screenWidthPx,
            screenHeightPx = playerLayout.screenHeightPx,
            showControls = chromeUi.showControls,
            showAudioSelector = chromeUi.showAudioSelector,
            showSubtitleDock = showSubtitleDock,
            showSubtitleBloom = showSubtitleBloom,
            showDualSubsWindow = showDualSubsWindow,
            showSubtitleBehaviourWindow = showSubtitleBehaviourWindow,
            showSpeechSubtitlePanel = subtitleAiUi.showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = subtitleAiUi.showSubtitleTranslationPanel,
            showSpeedMenu = chromeUi.showSpeedMenu,
            showSleepMenu = chromeUi.showSleepMenu,
            showSrtBrowser = chromeUi.showSrtBrowser,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            driftUi = driftUi,
            subtitleSyncTools = subtitleSyncTools,
            playbackNavigationCoordinator = playbackNavigationCoordinator,
            onDraggingSeekbarChanged = { chromeUi.isDraggingSeekbar = it },
            onPreviewPositionChanged = { gestureUi.previewPosition = it },
            onPreviewBitmapChanged = { gestureUi.previewBitmap = it },
            onPositionChanged = { position = it },
            onBrightnessPercentChanged = { chromeUi.brightnessPercent = it },
            onVolumePercentChanged = { chromeUi.volumePercent = it },
            onShowBrightnessCircleChanged = { chromeUi.showBrightnessCircle = it },
            onShowVolumeCircleChanged = { chromeUi.showVolumeCircle = it },
            onVideoTransformChanged = { scaleValue, offsetX, offsetY ->
                gestureUi.videoScale = scaleValue
                gestureUi.videoOffsetX = offsetX
                gestureUi.videoOffsetY = offsetY
            },
            onZoomModeToggle = { gestureUi.isZoomMode = !gestureUi.isZoomMode },
            onShowControlsChanged = { chromeUi.showControls = it },
            onShowTopBarChanged = { chromeUi.showTopBar = it },
            onShowAudioSelectorChanged = { chromeUi.showAudioSelector = it },
            onShowSubtitleDockChanged = { showSubtitleDock = it },
            onShowSubtitleBloomChanged = {
                showSubtitleBloom = it
                if (!it) studioCategory = null
            },
            onShowDualSubsWindowChanged = { showDualSubsWindow = it },
            onShowSubtitleBehaviourWindowChanged = { showSubtitleBehaviourWindow = it },
            onShowSpeechSubtitlePanelChanged = { subtitleAiUi.showSpeechSubtitlePanel = it },
            onShowSubtitleTranslationPanelChanged = { subtitleAiUi.showSubtitleTranslationPanel = it },
            onShowSpeedMenuChanged = { chromeUi.showSpeedMenu = it },
            onShowSleepMenuChanged = { chromeUi.showSleepMenu = it },
            onShowSrtBrowserChanged = { chromeUi.showSrtBrowser = it },
            onGestureEnd = {
                chromeUi.brightnessGestureKey++
                chromeUi.volumeGestureKey++
            },
            externalControlsVisible = {
                externalPresentation?.controlsVisible?.value == true
            },
            externalShowTouchPulse = {
                externalPresentation?.showTouchPulse()
            },
            externalClickPointer = {
                externalPresentation?.clickPointer() ?: false
            },
            externalShowControls = {
                externalPresentation?.showControls()
            },
            externalShowGestureHud = { title, value, progress ->
                if (progress == null) {
                    externalPresentation?.showGestureHud(title, value)
                } else {
                    externalPresentation?.showGestureHud(title, value, progress)
                }
            },
            externalOpenQuickSubtitles = {
                externalPresentation?.openQuickSubtitles()
            },
            externalUpdateSeekPreview = { bitmap, positionMs, visible ->
                externalPresentation?.updateSeekPreview(bitmap, positionMs, visible)
            },
            externalMovePointer = { x, y ->
                externalPresentation?.movePointer(x, y)
            },
            externalApplyViewportTransform = { zoom, panX, panY ->
                externalPresentation?.applyViewportTransform(zoom, panX, panY)
            },
            externalEnterTabletStandby = {
                externalPresentation?.enterTabletStandby()
            },
            disableGlassesSession = {
                glasses.disableSession()
            },
        )

        // Slice 64: auxiliary playback surfaces are now hosted together:
        // subtitle gestures, transient playback/status overlays and speed/sleep menus.
        PlayerAuxiliarySurfaces(
            player = exoPlayer,
            haptics = haptics,
            isStreamMedia = isStreamMedia,
            bottomDockPadding = playerLayout.bottomDockPadding,
            playButtonSize = playerLayout.playButton,
            coreUi = coreUi,
            appearanceUi = appearanceUi,
            studioUi = studioUi,
            isLandscape = playerLayout.isLandscape,
            hudSize = playerLayout.hudSize,
            showBrightnessCircle = chromeUi.showBrightnessCircle,
            brightnessPercent = chromeUi.brightnessPercent,
            showVolumeCircle = chromeUi.showVolumeCircle,
            volumePercent = chromeUi.volumePercent,
            edgeSwipeHint = gestureUi.edgeSwipeHint,
            showGlassesConnectedHint = showGlassesConnectedHint,
            showBufferingSpinner = playbackHealth.showBufferingSpinner,
            stuckBufferingHint = playbackHealth.stuckBufferingHint,
            playerErrorMessage = playbackHealth.playerErrorMessage,
            sleepTimerActive = sleepTimerActive,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            translationSuccessLanguage = subtitleAiUi.translationSuccessLanguage,
            translationSuccessBottomPadding = playerLayout.bottomDockPadding + playerLayout.playButton + 26.dp,
            showSpeedMenu = chromeUi.showSpeedMenu,
            showSleepMenu = chromeUi.showSleepMenu,
            playbackSpeed = playbackSpeed,
            sleepTimerMinutes = sleepTimerMinutes,
            topClusterPaddingTop = playerLayout.topClusterPaddingTop,
            clusterHeightPx = clusterHeightPx,
            sidePadding = playerLayout.sidePadding,
            smallMenuWidth = playerLayout.smallMenuWidth,
            smallMenuMaxHeight = playerLayout.smallMenuMaxHeight,
            onShowControls = { chromeUi.showControls = true },
            onBack = onBack,
            onRetry = {
                playbackHealth.errorRetryCount = 0
                playCurrentVideoWithSubtitle(
                    subtitleUri = trackUi.originalUri,
                    resumePosition = position,
                    isOriginalSubtitle = false,
                )
            },
            onSpeedSelected = { playerSessionActionsCoordinator.setPlaybackSpeed(it) },
            onDismissSpeedMenu = { chromeUi.showSpeedMenu = false },
            onSleepSelected = { playerSessionActionsCoordinator.setSleepTimer(it) },
            onDismissSleepMenu = { chromeUi.showSleepMenu = false },
        )

        // Slice 57: local/audio track popups, quick subtitle controls, track
        // selector, acquisition flow, drift sync and appearance studio now live
        // in one subtitle selection/acquisition presentation host.
        PlayerSubtitleSelectionAndAcquisitionSurfaces(
            context = context,
            player = exoPlayer,
            trackSelector = trackSelector,
            videoPath = currentVideo.path,
            canDownloadExternalSubtitles = canDownloadExternalSubtitles,
            pendingDeletePaths = pendingDeletePaths,
            generatedSubtitleFiles = subtitleAiUi.generatedSubtitleFiles,
            showSrtBrowser = chromeUi.showSrtBrowser,
            showAudioSelector = chromeUi.showAudioSelector,
            audioSyncMs = audioSyncMs,
            popupBottomPadding = playerLayout.popupBottomPadding,
            srtPopupWidth = playerLayout.srtPopupWidth,
            srtPopupMaxHeight = playerLayout.srtPopupMaxHeight,
            audioPopupWidth = playerLayout.audioPopupWidth,
            subtitlePopupWidth = playerLayout.subtitlePopupWidth,
            trackStudioWidth = playerLayout.trackStudioWidth,
            trackSelectorWidth = playerLayout.trackSelectorWidth,
            trackStudioMaxHeight = playerLayout.trackStudioMaxHeight,
            styleStudioWidth = playerLayout.styleStudioWidth,
            styleStudioMaxHeight = playerLayout.styleStudioMaxHeight,
            studioFrameInset = playerLayout.studioFrameInset,
            visibleMovieWidth = playerLayout.visibleMovieWidth,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            isLandscape = playerLayout.isLandscape,
            isCompactLandscape = playerLayout.isCompactLandscape,
            screenWidthPx = playerLayout.screenWidthPx,
            density = LocalDensity.current,
            subtitleIconCenterX = subIconX,
            audioIconCenterX = audioIconX,
            duration = duration,
            position = position,
            isAssOrSsaFormat = isAssOrSsaFormat,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            driftUi = driftUi,
            studioUi = studioUi,
            appearanceUi = appearanceUi,
            trackSelectorManageMode = trackSelectorManageMode,
            subtitleSearchCoordinator = subtitleSearchCoordinator,
            subtitleDeletionCoordinator = subtitleDeletionCoordinator,
            subtitleResetCoordinator = subtitleResetCoordinator,
            subtitleSyncTools = subtitleSyncTools,
            onPendingSrtUriChanged = { pendingSrtUri = it },
            onShowSrtBrowserChanged = { chromeUi.showSrtBrowser = it },
            onShowAudioSelectorChanged = { chromeUi.showAudioSelector = it },
            onAudioSyncMsChanged = { audioSyncMs = it },
            onAudioMenuInteraction = { menuTouchKey++ },
            onShowControlsChanged = { chromeUi.showControls = it },
            onShowTopBarChanged = { chromeUi.showTopBar = it },
            onShowSubtitleBloomChanged = { showSubtitleBloom = it },
            onStudioCategoryChanged = { studioCategory = it },
            onLaunchSrtPicker = subtitleFileRuntime.launchSrtPicker,
        )

        // Slice 59: visibility policy, top chrome, transient status pills,
        // and the transport/seek host are now one cohesive player-chrome surface.
        PlayerMainControlsChrome(
            context = context,
            activity = activity,
            scope = scope,
            player = exoPlayer,
            haptics = haptics,
            currentMeta = currentMeta,
            currentTitle = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path),
            currentVideoPath = currentVideo.path,
            isStreamMedia = isStreamMedia,
            isCurrentTvShow = isCurrentTvShow,
            isLandscape = playerLayout.isLandscape,
            isZoomMode = gestureUi.isZoomMode,
            showControls = chromeUi.showControls,
            isDraggingSeekbar = chromeUi.isDraggingSeekbar,
            isDraggingSeekbarNow = { chromeUi.isDraggingSeekbar },
            showAudioSelector = chromeUi.showAudioSelector,
            showSpeedMenu = chromeUi.showSpeedMenu,
            showSleepMenu = chromeUi.showSleepMenu,
            showSrtBrowser = chromeUi.showSrtBrowser,
            showSeekPreview = gestureUi.showSeekPreview,
            subtitleSettingsVisible = coreUi.showSettings,
            trackSelectorVisible = trackUi.showSelector,
            subtitleSearchVisible = searchUi.showSearch,
            driftDialogVisible = driftUi.showDialog,
            appearanceStudioVisible = coreUi.showAppearanceStudio,
            dialogueSyncArmed = coreUi.dialogueSyncArmed,
            showSubtitleDock = showSubtitleDock,
            showSubtitleBloom = showSubtitleBloom,
            showDualSubsWindow = showDualSubsWindow,
            showSubtitleBehaviourWindow = showSubtitleBehaviourWindow,
            showSpeechSubtitlePanel = subtitleAiUi.showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = subtitleAiUi.showSubtitleTranslationPanel,
            externalDisplayActive = externalPlayerView != null,
            autoSubtitleStatus = autoSubtitleFetch.status,
            playbackSpeed = playbackSpeed,
            sleepTimerActive = sleepTimerActive,
            topClusterPaddingTop = playerLayout.topClusterPaddingTop,
            sidePadding = playerLayout.sidePadding,
            topIconSize = playerLayout.topIconSize,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            pendingNextEpisode = pendingNextEpisode,
            nextEpisodeCountdown = nextEpisodeCountdown,
            activeSmartSegment = activeSmartSegment,
            exactSceneSegment = exactSceneSegment,
            creditsSegment = creditsSegment,
            smartSegmentResult = smartSegmentResult,
            position = position,
            duration = duration,
            previewBitmap = gestureUi.previewBitmap,
            previewPosition = gestureUi.previewPosition,
            getPreviewPosition = { gestureUi.previewPosition },
            isSeekPreviewLarge = gestureUi.isSeekPreviewLarge,
            previewFrames = gestureUi.previewFrames,
            bottomDockPadding = playerLayout.bottomDockPadding,
            seekBottomPadding = playerLayout.seekBottomPadding,
            scale = playerLayout.scale,
            smallButton = playerLayout.smallButton,
            playButton = playerLayout.playButton,
            isPlaying = isPlaying,
            isVideoEnded = isVideoEnded,
            showPrevNextButtons = showPrevNextButtons,
            hasNextVideo = hasNextVideo,
            autoPlayEnabled = autoPlayEnabled,
            onBack = onBack,
            playbackNavigationCoordinator = playbackNavigationCoordinator,
            playerMenuCloseCoordinator = playerMenuCloseCoordinator,
            onShowSpeedMenuChanged = { chromeUi.showSpeedMenu = it },
            onShowSleepMenuChanged = { chromeUi.showSleepMenu = it },
            onShowControlsChanged = { chromeUi.showControls = it },
            onClusterHeightMeasured = { clusterHeightPx = it },
            onPlayNextEpisode = { next ->
                showNextEpisodeOverlay = false
                pendingNextEpisode = null
                currentMediaType = next.type
                currentVideo = next.video
                onPlayNext(next)
            },
            onCancelNextEpisode = {
                showNextEpisodeOverlay = false
                pendingNextEpisode = null
                nextEpisodeCountdown = 0
                nextEpisodeDismissed = true
                chromeUi.showControls = true
            },
            onPositionChanged = { position = it },
            onShowTopBarChanged = { chromeUi.showTopBar = it },
            onVideoEndedChanged = { isVideoEnded = it },
            onAutoPlayEnabledChanged = { autoPlayEnabled = it },
            onShowAudioSelectorChanged = { chromeUi.showAudioSelector = it },
            onMenuTouch = { menuTouchKey++ },
            onAudioCenterMeasured = { audioIconX = it },
            onSubtitleClick = {
                val wasOpen = showSubtitleDock ||
                    showSubtitleBloom ||
                    trackUi.showSelector ||
                    searchUi.showSearch ||
                    driftUi.showDialog ||
                    coreUi.showAppearanceStudio
                playerMenuCloseCoordinator.closeAll()
                showSubtitleDock = !wasOpen
                if (showSubtitleDock) {
                    chromeUi.showControls = false
                    chromeUi.showTopBar = false
                } else {
                    chromeUi.showControls = true
                }
                menuTouchKey++
            },
            onSubtitleLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playerMenuCloseCoordinator.closeAll()
                showSubtitleBloom = true
                chromeUi.showControls = false
                chromeUi.showTopBar = false
            },
            onSubtitleCenterMeasured = { subIconX = it },
            onDraggingSeekbarChanged = { chromeUi.isDraggingSeekbar = it },
            onShowSeekPreviewChanged = { gestureUi.showSeekPreview = it },
            onPreviewPositionChanged = { gestureUi.previewPosition = it },
            onPreviewBitmapChanged = { gestureUi.previewBitmap = it },
        )

        // Slice 60: all non-main-control overlay surfaces are now hosted
        // together: lock/Auto-Sync/delete feedback, Subtitle Studio surfaces,
        // and the AI subtitle panels. State remains owned by this screen.
        PlayerOverlaySurfacesHost(
            context = context,
            player = exoPlayer,
            haptics = haptics,
            controlsLocked = chromeUi.controlsLocked,
            lockButtonVisibleWhileLocked = chromeUi.lockButtonVisibleWhileLocked,
            showControls = chromeUi.showControls,
            externalDisplayActive = externalPlayerView != null,
            isLandscape = playerLayout.isLandscape,
            containerWidth = playerMaxWidth,
            containerHeight = playerMaxHeight,
            bottomDockPadding = playerLayout.bottomDockPadding,
            playButton = playerLayout.playButton,
            subtitleIconCenterX = subIconX,
            autoSyncStatus = autoSyncStatus,
            autoSyncCoordinator = autoSyncCoordinator,
            pendingDeleteFile = pendingDeleteConfirmFile,
            snackbarHostState = snackbarHostState,
            pendingDeletePaths = pendingDeletePaths,
            currentVideoPath = currentVideo.path,
            canDownloadExternalSubtitles = canDownloadExternalSubtitles,
            generatedSubtitleFiles = subtitleAiUi.generatedSubtitleFiles,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            studioUi = studioUi,
            dualUi = dualUi,
            appearanceUi = appearanceUi,
            driftUi = driftUi,
            showSubtitleDock = showSubtitleDock,
            showSubtitleBloom = showSubtitleBloom,
            studioCategory = studioCategory,
            showSubtitleBehaviourWindow = showSubtitleBehaviourWindow,
            showDualSubsWindow = showDualSubsWindow,
            autoSyncSpeechTimeline = autoSyncSpeechTimeline,
            dualSecondaryColorHex = dualSecondaryColorHex,
            pendingDualAiLanguage = pendingDualAiLanguage,
            subtitleStudioNavigation = subtitleStudioNavigation,
            subtitleResetCoordinator = subtitleResetCoordinator,
            subtitleSyncTools = subtitleSyncTools,
            speechJobLabel = speechJobLabel,
            speechJobProgress = speechJobProgress,
            translationJobLabel = translationJobLabel,
            translationJobProgress = translationJobProgress,
            showSpeechSubtitlePanel = subtitleAiUi.showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = subtitleAiUi.showSubtitleTranslationPanel,
            speechSubtitleStatus = subtitleAiUi.speechSubtitleStatus,
            subtitleTranslationStatus = subtitleAiUi.subtitleTranslationStatus,
            speechSubtitleCoordinator = speechSubtitleCoordinator,
            subtitleTranslationCoordinator = subtitleTranslationCoordinator,
            generatedSubtitleOrchestrator = generatedSubtitleOrchestrator,
            onControlsLockedChanged = { chromeUi.controlsLocked = it },
            onLockButtonVisibleWhileLockedChanged = { chromeUi.lockButtonVisibleWhileLocked = it },
            onAutoSyncStatusChanged = { autoSyncStatus = it },
            onDismissDelete = { subtitleFileRuntime.pendingDeleteFileState.value = null },
            onConfirmDelete = { file ->
                subtitleFileRuntime.pendingDeleteFileState.value = null
                subtitleDeletionCoordinator.deleteWithUndo(file)
            },
            onStudioCategoryChanged = { studioCategory = it },
            onTrackSelectorManageModeChanged = { trackSelectorManageMode = it },
            onShowSubtitleBloomChanged = { showSubtitleBloom = it },
            onShowSubtitleBehaviourWindowChanged = { showSubtitleBehaviourWindow = it },
            onShowDualSubsWindowChanged = { showDualSubsWindow = it },
            onShowSpeechSubtitlePanelChanged = { subtitleAiUi.showSpeechSubtitlePanel = it },
            onShowSubtitleTranslationPanelChanged = { subtitleAiUi.showSubtitleTranslationPanel = it },
            onPendingDualAiLanguageChanged = { pendingDualAiLanguage = it },
            onDualSecondaryColorHexChanged = { dualSecondaryColorHex = it },
        )





    }
}
