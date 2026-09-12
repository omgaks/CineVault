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
        onShowSpeedMenuChanged = { showSpeedMenu = it },
        onShowSleepMenuChanged = { showSleepMenu = it },
        onShowControlsChanged = { showControls = it },
        onCurrentVideoChanged = { currentVideo = it },
        onCurrentMediaTypeChanged = { currentMediaType = it },
        onEdgeSwipeHintChanged = { edgeSwipeHint = it },
        onPlayerErrorMessageChanged = { playerErrorMessage = it },
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
        errorRetryCount = errorRetryCount,
        coreUi = coreUi,
        trackUi = trackUi,
        searchUi = searchUi,
        driftUi = driftUi,
        studioUi = studioUi,
        audioLanguageCheckedForPath = audioLanguageCheckedForPath,
        isDraggingSeekbar = isDraggingSeekbar,
        isBuffering = isBuffering,
        showSeekPreview = showSeekPreview,
        previewPosition = previewPosition,
        duration = duration,
        previewReloadKey = previewReloadKey,
        droppedFrameNudgeCount = droppedFrameNudgeCount,
        lastNudgeAtMs = lastNudgeAtMs,
        isPlaying = isPlaying,
        showControls = showControls,
        showTopBar = showTopBar,
        controlsLocked = controlsLocked,
        lockButtonVisibleWhileLocked = lockButtonVisibleWhileLocked,
        showAudioSelector = showAudioSelector,
        showSpeedMenu = showSpeedMenu,
        showSleepMenu = showSleepMenu,
        showSrtBrowser = showSrtBrowser,
        menuTouchKey = menuTouchKey,
        brightnessGestureKey = brightnessGestureKey,
        volumeGestureKey = volumeGestureKey,
        showSubtitleDock = showSubtitleDock,
        showSubtitleBloom = showSubtitleBloom,
        showDualSubsWindow = showDualSubsWindow,
        onNextRequested = { playbackNavigationCoordinator.playNext() },
        onPreviousRequested = { playbackNavigationCoordinator.playPrevious() },
        onInitialBrightnessChanged = { brightnessPercent = it },
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
        onShowControlsAndTopBar = {
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
        onPositionChanged = { position = it },
        onDurationChanged = { duration = it },
        onBufferingSpinnerChanged = { showBufferingSpinner = it },
        onStuckBufferingChanged = { stuckBufferingHint = it },
        onDroppedFrameNudgeCountChanged = { droppedFrameNudgeCount = it },
        onLastNudgeAtMsChanged = { lastNudgeAtMs = it },
        onPreviewFramesChanged = { previewFrames = it },
        onPreviewBitmapChanged = { previewBitmap = it },
        onSeekPreviewLargeChanged = { isSeekPreviewLarge = it },
        onEnteredPip = { playerMenuCloseCoordinator.closeAll() },
        onHideControls = { showControls = false },
        onHideTopBar = { showTopBar = false },
        onHideLockedButton = { lockButtonVisibleWhileLocked = false },
        onHideAudioSelector = { showAudioSelector = false },
        onHideSpeedMenu = { showSpeedMenu = false },
        onHideSleepMenu = { showSleepMenu = false },
        onHideSrtBrowser = { showSrtBrowser = false },
        onHideBrightnessHud = { showBrightnessCircle = false },
        onHideVolumeHud = { showVolumeCircle = false },
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
        speechSubtitleStatus = speechSubtitleStatus,
        onSpeechSubtitleStatusChanged = { speechSubtitleStatus = it },
        subtitleTranslationStatus = subtitleTranslationStatus,
        onSubtitleTranslationStatusChanged = { subtitleTranslationStatus = it },
        pendingDualAiLanguage = pendingDualAiLanguage,
        onPendingDualAiLanguageChanged = { pendingDualAiLanguage = it },
        generatedSubtitleRefreshKey = generatedSubtitleRefreshKey,
        onGeneratedSubtitleRefreshRequested = { generatedSubtitleRefreshKey++ },
        onGeneratedSubtitleFilesLoaded = { generatedSubtitleFiles = it },
        showSpeechSubtitlePanel = showSpeechSubtitlePanel,
        showSubtitleTranslationPanel = showSubtitleTranslationPanel,
        onShowSpeechSubtitlePanelChanged = { showSpeechSubtitlePanel = it },
        onShowSubtitleTranslationPanelChanged = { showSubtitleTranslationPanel = it },
        onTranslationSuccessLanguageChanged = { translationSuccessLanguage = it },
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
        val isLandscape = playerLayout.isLandscape
        val isSmallPhone = playerLayout.isSmallPhone
        val isCompactLandscape = playerLayout.isCompactLandscape
        val scale = playerLayout.scale
        val playButton = playerLayout.playButton
        val smallButton = playerLayout.smallButton
        val hudSize = playerLayout.hudSize
        val sidePadding = playerLayout.sidePadding
        val bottomDockPadding = playerLayout.bottomDockPadding
        val seekBottomPadding = playerLayout.seekBottomPadding
        val topClusterPaddingTop = playerLayout.topClusterPaddingTop

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

        // Slice 53: all player/popup/Sub Studio geometry is derived in one
        // pure layout helper. VideoPlayerScreen only keeps local aliases used by
        // the existing presentation calls below.
        val density = LocalDensity.current
        val uiScale = playerLayout.uiScale
        val screenWidthPx = playerLayout.screenWidthPx
        val screenHeightPx = playerLayout.screenHeightPx
        val popupBottomPadding = playerLayout.popupBottomPadding
        val subtitlePopupWidth = playerLayout.subtitlePopupWidth
        val subtitlePopupHeightEstimate = playerLayout.subtitlePopupHeightEstimate
        val trackSelectorWidth = playerLayout.trackSelectorWidth
        val trackSelectorMaxHeight = playerLayout.trackSelectorMaxHeight
        val visibleMovieWidth = playerLayout.visibleMovieWidth
        val studioFrameInset = playerLayout.studioFrameInset
        val trackStudioWidth = playerLayout.trackStudioWidth
        val trackStudioMaxHeight = playerLayout.trackStudioMaxHeight
        val styleStudioWidth = playerLayout.styleStudioWidth
        val styleStudioMaxHeight = playerLayout.styleStudioMaxHeight
        val srtPopupWidth = playerLayout.srtPopupWidth
        val srtPopupMaxHeight = playerLayout.srtPopupMaxHeight
        val audioPopupWidth = playerLayout.audioPopupWidth
        val smallMenuWidth = playerLayout.smallMenuWidth
        val smallMenuMaxHeight = playerLayout.smallMenuMaxHeight
        val topIconSize = playerLayout.topIconSize

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
            isLandscape = isLandscape,
            canChangeEpisode = showPrevNextButtons,
            previewFrames = previewFrames,
            previewPosition = previewPosition,
            previewBitmap = previewBitmap,
            brightnessPercent = brightnessPercent,
            volumePercent = volumePercent,
            videoScale = videoScale,
            videoOffsetX = videoOffsetX,
            videoOffsetY = videoOffsetY,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            showControls = showControls,
            showAudioSelector = showAudioSelector,
            showSubtitleDock = showSubtitleDock,
            showSubtitleBloom = showSubtitleBloom,
            showDualSubsWindow = showDualSubsWindow,
            showSubtitleBehaviourWindow = showSubtitleBehaviourWindow,
            showSpeechSubtitlePanel = showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = showSubtitleTranslationPanel,
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            showSrtBrowser = showSrtBrowser,
            coreUi = coreUi,
            trackUi = trackUi,
            searchUi = searchUi,
            driftUi = driftUi,
            subtitleSyncTools = subtitleSyncTools,
            playbackNavigationCoordinator = playbackNavigationCoordinator,
            onDraggingSeekbarChanged = { isDraggingSeekbar = it },
            onPreviewPositionChanged = { previewPosition = it },
            onPreviewBitmapChanged = { previewBitmap = it },
            onPositionChanged = { position = it },
            onBrightnessPercentChanged = { brightnessPercent = it },
            onVolumePercentChanged = { volumePercent = it },
            onShowBrightnessCircleChanged = { showBrightnessCircle = it },
            onShowVolumeCircleChanged = { showVolumeCircle = it },
            onVideoTransformChanged = { scaleValue, offsetX, offsetY ->
                videoScale = scaleValue
                videoOffsetX = offsetX
                videoOffsetY = offsetY
            },
            onZoomModeToggle = { isZoomMode = !isZoomMode },
            onShowControlsChanged = { showControls = it },
            onShowTopBarChanged = { showTopBar = it },
            onShowAudioSelectorChanged = { showAudioSelector = it },
            onShowSubtitleDockChanged = { showSubtitleDock = it },
            onShowSubtitleBloomChanged = {
                showSubtitleBloom = it
                if (!it) studioCategory = null
            },
            onShowDualSubsWindowChanged = { showDualSubsWindow = it },
            onShowSubtitleBehaviourWindowChanged = { showSubtitleBehaviourWindow = it },
            onShowSpeechSubtitlePanelChanged = { showSpeechSubtitlePanel = it },
            onShowSubtitleTranslationPanelChanged = { showSubtitleTranslationPanel = it },
            onShowSpeedMenuChanged = { showSpeedMenu = it },
            onShowSleepMenuChanged = { showSleepMenu = it },
            onShowSrtBrowserChanged = { showSrtBrowser = it },
            onGestureEnd = {
                brightnessGestureKey++
                volumeGestureKey++
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
            generatedSubtitleFiles = generatedSubtitleFiles,
            showSrtBrowser = showSrtBrowser,
            showAudioSelector = showAudioSelector,
            audioSyncMs = audioSyncMs,
            popupBottomPadding = popupBottomPadding,
            srtPopupWidth = srtPopupWidth,
            srtPopupMaxHeight = srtPopupMaxHeight,
            audioPopupWidth = audioPopupWidth,
            subtitlePopupWidth = subtitlePopupWidth,
            trackStudioWidth = trackStudioWidth,
            trackStudioMaxHeight = trackStudioMaxHeight,
            styleStudioWidth = styleStudioWidth,
            styleStudioMaxHeight = styleStudioMaxHeight,
            studioFrameInset = studioFrameInset,
            visibleMovieWidth = visibleMovieWidth,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            isLandscape = isLandscape,
            isCompactLandscape = isCompactLandscape,
            screenWidthPx = screenWidthPx,
            density = density,
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
            onShowSrtBrowserChanged = { showSrtBrowser = it },
            onShowAudioSelectorChanged = { showAudioSelector = it },
            onAudioSyncMsChanged = { audioSyncMs = it },
            onShowControlsChanged = { showControls = it },
            onShowTopBarChanged = { showTopBar = it },
            onShowSubtitleBloomChanged = { showSubtitleBloom = it },
            onStudioCategoryChanged = { studioCategory = it },
            onLaunchSrtPicker = { mimeTypes -> srtPickerLauncher.launch(mimeTypes) },
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
            isLandscape = isLandscape,
            isZoomMode = isZoomMode,
            showControls = showControls,
            isDraggingSeekbar = isDraggingSeekbar,
            showAudioSelector = showAudioSelector,
            showSpeedMenu = showSpeedMenu,
            showSleepMenu = showSleepMenu,
            showSrtBrowser = showSrtBrowser,
            showSeekPreview = showSeekPreview,
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
            showSpeechSubtitlePanel = showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = showSubtitleTranslationPanel,
            externalDisplayActive = externalPlayerView != null,
            autoSubtitleStatus = autoSubtitleFetch.status,
            playbackSpeed = playbackSpeed,
            sleepTimerActive = sleepTimerActive,
            topClusterPaddingTop = topClusterPaddingTop,
            sidePadding = sidePadding,
            topIconSize = topIconSize,
            showNextEpisodeOverlay = showNextEpisodeOverlay,
            pendingNextEpisode = pendingNextEpisode,
            nextEpisodeCountdown = nextEpisodeCountdown,
            activeSmartSegment = activeSmartSegment,
            exactSceneSegment = exactSceneSegment,
            creditsSegment = creditsSegment,
            smartSegmentResult = smartSegmentResult,
            position = position,
            duration = duration,
            previewBitmap = previewBitmap,
            previewPosition = previewPosition,
            isSeekPreviewLarge = isSeekPreviewLarge,
            previewFrames = previewFrames,
            bottomDockPadding = bottomDockPadding,
            seekBottomPadding = seekBottomPadding,
            scale = scale,
            smallButton = smallButton,
            playButton = playButton,
            isPlaying = isPlaying,
            isVideoEnded = isVideoEnded,
            showPrevNextButtons = showPrevNextButtons,
            hasNextVideo = hasNextVideo,
            autoPlayEnabled = autoPlayEnabled,
            onBack = onBack,
            playbackNavigationCoordinator = playbackNavigationCoordinator,
            playerMenuCloseCoordinator = playerMenuCloseCoordinator,
            onShowSpeedMenuChanged = { showSpeedMenu = it },
            onShowSleepMenuChanged = { showSleepMenu = it },
            onShowControlsChanged = { showControls = it },
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
                showControls = true
            },
            onPositionChanged = { position = it },
            onShowTopBarChanged = { showTopBar = it },
            onVideoEndedChanged = { isVideoEnded = it },
            onAutoPlayEnabledChanged = { autoPlayEnabled = it },
            onShowAudioSelectorChanged = { showAudioSelector = it },
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
                    showControls = false
                    showTopBar = false
                } else {
                    showControls = true
                }
                menuTouchKey++
            },
            onSubtitleLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playerMenuCloseCoordinator.closeAll()
                showSubtitleBloom = true
                showControls = false
                showTopBar = false
            },
            onSubtitleCenterMeasured = { subIconX = it },
            onDraggingSeekbarChanged = { isDraggingSeekbar = it },
            onShowSeekPreviewChanged = { showSeekPreview = it },
            onPreviewPositionChanged = { previewPosition = it },
            onPreviewBitmapChanged = { previewBitmap = it },
        )

        // Slice 60: all non-main-control overlay surfaces are now hosted
        // together: lock/Auto-Sync/delete feedback, Subtitle Studio surfaces,
        // and the AI subtitle panels. State remains owned by this screen.
        PlayerOverlaySurfacesHost(
            context = context,
            player = exoPlayer,
            haptics = haptics,
            controlsLocked = controlsLocked,
            lockButtonVisibleWhileLocked = lockButtonVisibleWhileLocked,
            showControls = showControls,
            externalDisplayActive = externalPlayerView != null,
            isLandscape = isLandscape,
            containerWidth = playerMaxWidth,
            containerHeight = playerMaxHeight,
            bottomDockPadding = bottomDockPadding,
            playButton = playButton,
            subtitleIconCenterX = subIconX,
            autoSyncStatus = autoSyncStatus,
            autoSyncCoordinator = autoSyncCoordinator,
            pendingDeleteFile = pendingDeleteConfirmFile,
            snackbarHostState = snackbarHostState,
            pendingDeletePaths = pendingDeletePaths,
            currentVideoPath = currentVideo.path,
            canDownloadExternalSubtitles = canDownloadExternalSubtitles,
            generatedSubtitleFiles = generatedSubtitleFiles,
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
            showSpeechSubtitlePanel = showSpeechSubtitlePanel,
            showSubtitleTranslationPanel = showSubtitleTranslationPanel,
            speechSubtitleStatus = speechSubtitleStatus,
            subtitleTranslationStatus = subtitleTranslationStatus,
            speechSubtitleCoordinator = speechSubtitleCoordinator,
            subtitleTranslationCoordinator = subtitleTranslationCoordinator,
            generatedSubtitleOrchestrator = generatedSubtitleOrchestrator,
            onControlsLockedChanged = { controlsLocked = it },
            onLockButtonVisibleWhileLockedChanged = { lockButtonVisibleWhileLocked = it },
            onAutoSyncStatusChanged = { autoSyncStatus = it },
            onDismissDelete = { pendingDeleteConfirmFile = null },
            onConfirmDelete = { file ->
                pendingDeleteConfirmFile = null
                subtitleDeletionCoordinator.deleteWithUndo(file)
            },
            onStudioCategoryChanged = { studioCategory = it },
            onTrackSelectorManageModeChanged = { trackSelectorManageMode = it },
            onShowSubtitleBloomChanged = { showSubtitleBloom = it },
            onShowSubtitleBehaviourWindowChanged = { showSubtitleBehaviourWindow = it },
            onShowDualSubsWindowChanged = { showDualSubsWindow = it },
            onShowSpeechSubtitlePanelChanged = { showSpeechSubtitlePanel = it },
            onShowSubtitleTranslationPanelChanged = { showSubtitleTranslationPanel = it },
            onPendingDualAiLanguageChanged = { pendingDualAiLanguage = it },
            onDualSecondaryColorHexChanged = { dualSecondaryColorHex = it },
        )





    }
}
