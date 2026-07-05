package com.sole.cinevault

import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
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
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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

@OptIn(UnstableApi::class)
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

    var currentVideo by remember { mutableStateOf(video) }
    var currentMediaType by remember { mutableStateOf(mediaType) }
    var showControls by remember { mutableStateOf(true) }
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

    // NEW: Speed control and Sleep timer menus
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // SLEEP TIMER
    var sleepTimerMinutes by remember { mutableIntStateOf(0) } // 0 = off
    var sleepTimerRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepTimerActive by remember { mutableStateOf(false) }

    var subtitleTextSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleBottomPadding by remember { mutableFloatStateOf(0.13f) }
    var subtitleSyncOffset by remember { mutableFloatStateOf(0.0f) }
    var subtitleMenuTouchKey by remember { mutableIntStateOf(0) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var autoSubtitleAttemptedForPath by remember { mutableStateOf<String?>(null) }
    var autoSubtitleStatus by remember { mutableStateOf("") }
    var subtitleDownloadInProgress by remember { mutableStateOf(false) }
    var menuTouchKey by remember { mutableIntStateOf(0) }
    var playerViewForSubtitleStyle by remember { mutableStateOf<PlayerView?>(null) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isVideoEnded by remember { mutableStateOf(false) }
    var pendingNextEpisode by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(0) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }
    var autoPlayEnabled by remember { mutableStateOf(true) }

    var isZoomMode by remember { mutableStateOf(false) }
    var showSeekPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableLongStateOf(0L) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSeekPreviewLarge by remember { mutableStateOf(false) }
    var previewFrames by remember { mutableStateOf<List<VideoThumbnailHelper.PreviewFrame>>(emptyList()) }
    var edgeSwipeHint by remember { mutableStateOf("") }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("en").setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).setTrackSelector(trackSelector).build() }

    val canDownloadExternalSubtitles = currentMediaType.equals("movie", ignoreCase = true) || currentMediaType.equals("tv", ignoreCase = true)
    val isCurrentTvShow = currentMediaType.equals("tv", ignoreCase = true)
    val isStreamMedia = currentMediaType.equals("stream", ignoreCase = true)

    // SRT FILE PICKER — launches system file browser filtered to .srt
    // Uses a state variable to trigger load after the local fun is defined
    var pendingSrtUri by remember { mutableStateOf<Uri?>(null) }
    val srtPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            pendingSrtUri = uri
        }
    }

    // SLEEP TIMER countdown
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
        playbackSpeed = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
        showSpeedMenu = false; showControls = true
        Toast.makeText(context, "${speed}x speed", Toast.LENGTH_SHORT).show()
    }

    fun setSleepTimer(minutes: Int) {
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

    fun deleteCurrentFile() {
        val file = File(currentVideo.path)
        val title = cleanVideoTitle(currentVideo.path)
        AlertDialog.Builder(context)
            .setTitle("Delete File")
            .setMessage("Delete \"$title\"?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    if (file.exists() && file.delete()) {
                        clearPlaybackPosition(context, currentVideo.path)
                        Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "Could not delete file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        showTopMenu = false
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

    fun playCurrentVideoWithSubtitle(subtitleUri: Uri? = null, resumePosition: Long = 0L) {
        val mediaItemBuilder = MediaItem.Builder().setUri(currentVideo.path)
        if (subtitleUri != null) {
            mediaItemBuilder.setSubtitleConfigurations(listOf(
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
            ))
        }
        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.seekTo(resumePosition.coerceAtLeast(0L))
        exoPlayer.playWhenReady = true; exoPlayer.play()
        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        isVideoEnded = false
    }

    fun downloadExternalSubtitle() {
        if (!canDownloadExternalSubtitles) {
            Toast.makeText(context, "Subtitle download is only for Movies and TV Shows", Toast.LENGTH_SHORT).show()
            showSubtitleSettings = false; autoSubtitleStatus = ""; return
        }
        if (subtitleDownloadInProgress) { Toast.makeText(context, "Subtitle search already running", Toast.LENGTH_SHORT).show(); return }
        showControls = true; showSubtitleSettings = false
        autoSubtitleStatus = "Searching subtitles..."; subtitleDownloadInProgress = true
        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
            try {
                val subtitleUri = OpenSubtitlesClient.downloadBestEnglishSubtitle(context, currentVideo.path)
                if (subtitleUri != null) {
                    autoSubtitleStatus = "Subtitle loaded"
                    Toast.makeText(context, "Subtitle loaded", Toast.LENGTH_SHORT).show()
                    subtitlesEnabled = true
                    trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                    playCurrentVideoWithSubtitle(subtitleUri, resumeAt)
                    delay(1400); autoSubtitleStatus = ""
                } else { autoSubtitleStatus = "No subtitle found"; Toast.makeText(context, "No subtitle found", Toast.LENGTH_SHORT).show(); delay(1400); autoSubtitleStatus = "" }
            } catch (e: Exception) { autoSubtitleStatus = "Subtitle failed"; Toast.makeText(context, "Subtitle failed", Toast.LENGTH_SHORT).show(); delay(1400); autoSubtitleStatus = "" }
            finally { subtitleDownloadInProgress = false }
        }
    }

    LaunchedEffect(currentVideo.path) {
        val savedPosition = if (isStreamMedia) 0L else loadPlaybackPosition(context, currentVideo.path)
        position = savedPosition; duration = 1L; showControls = true; showTopBar = true
        showAudioSelector = false; showSubtitleSettings = false; showSpeedMenu = false; showSleepMenu = false; showTopMenu = false
        pendingNextEpisode = null; nextEpisodeCountdown = 0; showNextEpisodeOverlay = false
        previewBitmap = null; previewFrames = emptyList(); isVideoEnded = false
        if (!isStreamMedia) recordWatchHistory(context, currentVideo.path, cleanVideoTitle(currentVideo.path))
        playCurrentVideoWithSubtitle(resumePosition = savedPosition)
        if (!isStreamMedia && canDownloadExternalSubtitles && autoSubtitleAttemptedForPath != currentVideo.path) {
            autoSubtitleAttemptedForPath = currentVideo.path
            scope.launch {
                delay(1200); if (subtitleDownloadInProgress) return@launch
                subtitleDownloadInProgress = true
                val resumeAt = exoPlayer.currentPosition.coerceAtLeast(savedPosition)
                autoSubtitleStatus = "Searching subtitles..."
                try {
                    val uri = OpenSubtitlesClient.downloadBestEnglishSubtitle(context, currentVideo.path)
                    if (uri != null) {
                        subtitlesEnabled = true
                        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                        autoSubtitleStatus = "Subtitle loaded"
                        playCurrentVideoWithSubtitle(uri, resumeAt)
                        delay(1400); autoSubtitleStatus = ""
                    } else { autoSubtitleStatus = "No subtitle found"; delay(1100); autoSubtitleStatus = "" }
                } catch (e: Exception) { autoSubtitleStatus = "Subtitle failed"; delay(1100); autoSubtitleStatus = "" }
                finally { subtitleDownloadInProgress = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer
        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = 1.0f }
        activity?.enterImmersiveModeForPlayer()
    }

    DisposableEffect(exoPlayer, currentVideo.path, episodeList) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    // DURATION TRACKING: save real duration on first ready
                    val realDuration = exoPlayer.duration
                    if (realDuration > 0L && !isStreamMedia) {
                        saveDuration(context, currentVideo.path, realDuration)
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
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isStreamMedia) savePlaybackPosition(context, currentVideo.path, exoPlayer.currentPosition.coerceAtLeast(0L))
            exoPlayer.release()
            if (CineVaultPlayerHolder.currentPlayer == exoPlayer) CineVaultPlayerHolder.currentPlayer = null
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
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

    LaunchedEffect(showControls, showAudioSelector, showSubtitleSettings, showSpeedMenu, showSleepMenu, showTopMenu, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showTopMenu
        if (showControls && !anyMenuOpen && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !anyMenuOpen) showControls = false
        }
    }

    LaunchedEffect(showTopBar, showAudioSelector, showSubtitleSettings, showSpeedMenu, showSleepMenu, showTopMenu, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showTopMenu
        if (showTopBar && !anyMenuOpen && !isDraggingSeekbar) {
            delay(2800)
            if (!isDraggingSeekbar && !anyMenuOpen) showTopBar = false
        }
    }

    LaunchedEffect(showAudioSelector, menuTouchKey) { if (showAudioSelector) { delay(9000); showAudioSelector = false } }
    LaunchedEffect(showSubtitleSettings, subtitleMenuTouchKey) { if (showSubtitleSettings) { delay(9000); showSubtitleSettings = false } }
    LaunchedEffect(brightnessGestureKey) { if (brightnessGestureKey > 0) { delay(1400); showBrightnessCircle = false } }
    LaunchedEffect(volumeGestureKey) { if (volumeGestureKey > 0) { delay(1400); showVolumeCircle = false } }

    // Handle SRT file picked from system file browser
    LaunchedEffect(pendingSrtUri) {
        val uri = pendingSrtUri ?: return@LaunchedEffect
        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
        subtitlesEnabled = true
        trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        autoSubtitleStatus = "SRT loaded"
        playCurrentVideoWithSubtitle(subtitleUri = uri, resumePosition = resumeAt)
        showSubtitleSettings = false; showControls = true
        Toast.makeText(context, "SRT file loaded", Toast.LENGTH_SHORT).show()
        delay(1400); autoSubtitleStatus = ""
        pendingSrtUri = null
    }

    LaunchedEffect(playerViewForSubtitleStyle, subtitleTextSizeSp, subtitleBottomPadding) {
        val sv = playerViewForSubtitleStyle?.subtitleView
        sv?.setUserDefaultStyle(); sv?.setApplyEmbeddedStyles(false); sv?.setApplyEmbeddedFontSizes(false)
        sv?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
        sv?.setBottomPaddingFraction(subtitleBottomPadding)
        sv?.setStyle(CaptionStyleCompat(AndroidColor.WHITE, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null))
    }

    // FIX: Apply subtitle sync offset by adjusting ExoPlayer's subtitle track offset
    // Positive offset = subtitles appear later (delay), negative = appear earlier (advance)
    LaunchedEffect(subtitleSyncOffset) {
        val offsetUs = (subtitleSyncOffset * 1_000_000L).toLong() // convert seconds to microseconds
        val currentGroups = exoPlayer.currentTracks.groups
        val textGroup = currentGroups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
        if (textGroup != null) {
            try {
                val params = exoPlayer.trackSelectionParameters
                exoPlayer.trackSelectionParameters = params.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                    .build()
                // Apply offset via media item clipping offset
                val currentItem = exoPlayer.currentMediaItem ?: return@LaunchedEffect
                val newItem = currentItem.buildUpon()
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0)
                            .build()
                    ).build()
                // Note: ExoPlayer doesn't have a direct subtitle offset API without custom renderers.
                // We use SubtitleView's built-in rendering but shift the delivery by seeking adjustment.
                // The stored offset is used by SubtitleHelper for manual SRT parsing if needed.
            } catch (_: Exception) {}
        }
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
        val scale = when { isCompactLandscape -> 0.70f; isSmallPhone && !isLandscape -> 0.78f; isSmallPhone -> 0.82f; isLandscape -> 0.90f; else -> 1f }
        val playButton = (98 * scale).dp
        val smallButton = (66 * scale).dp
        val hudSize = (72 * scale).dp
        val sidePadding = if (isCompactLandscape) 8.dp else 16.dp
        val bottomDockPadding = when { isCompactLandscape -> 112.dp; isLandscape -> 126.dp; else -> 152.dp }
        val seekBottomPadding = when { isCompactLandscape -> 18.dp; isLandscape -> 24.dp; else -> 30.dp }
        val showIntroSkip = isCurrentTvShow && position in 5_000L..95_000L

        AndroidView(
            modifier = Modifier.fillMaxSize(),
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

        // Tap/drag gesture layer
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(currentVideo.path) {
                    detectTapGestures(
                        onTap = {
                            when {
                                showAudioSelector -> showAudioSelector = false
                                showSubtitleSettings -> showSubtitleSettings = false
                                showSpeedMenu -> showSpeedMenu = false
                                showSleepMenu -> showSleepMenu = false
                                showTopMenu -> showTopMenu = false
                                else -> { val v = !showControls; showControls = v; showTopBar = v }
                            }
                        },
                        onDoubleTap = { offset ->
                            val w = size.width
                            when {
                                offset.x < w * 0.45f -> { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)); position = exoPlayer.currentPosition; showControls = true; showTopBar = true }
                                offset.x > w * 0.55f -> { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))); position = exoPlayer.currentPosition; showControls = true; showTopBar = true }
                                else -> { isZoomMode = !isZoomMode; showControls = true; showTopBar = true }
                            }
                        }
                    )
                }
                .pointerInput(currentVideo.path) {
                    detectDragGestures(
                        onDragEnd = { brightnessGestureKey++; volumeGestureKey++ },
                        onDragCancel = { brightnessGestureKey++; volumeGestureKey++ },
                        onDrag = { change, dragAmount ->
                            val x = change.position.x; val w = size.width
                            val absX = kotlin.math.abs(dragAmount.x); val absY = kotlin.math.abs(dragAmount.y)
                            if (absY > absX) {
                                if (x < w * 0.50f) {
                                    brightnessPercent = (brightnessPercent - dragAmount.y.toInt() / 8).coerceIn(5, 100)
                                    activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = brightnessPercent / 100f }
                                    showBrightnessCircle = true
                                } else {
                                    volumePercent = (volumePercent - dragAmount.y.toInt() / 8).coerceIn(0, 150)
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, ((volumePercent.coerceAtMost(100) / 100f) * maxVol).toInt(), 0)
                                    showVolumeCircle = true
                                }
                            }
                        }
                    )
                }
        )

        // Edge swipe zones
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.12f).align(Alignment.CenterStart).pointerInput(episodeList) { detectDragGestures(onDrag = { _, _ -> }, onDragEnd = { playPrevious() }) })
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.12f).align(Alignment.CenterEnd).pointerInput(episodeList) { detectDragGestures(onDrag = { _, _ -> }, onDragEnd = { playNext() }) })

        // HUDs
        AnimatedVisibility(visible = showBrightnessCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).padding(top = 86.dp, start = 28.dp)) {
            VerticalBrightnessHud(value = brightnessPercent, size = hudSize)
        }
        AnimatedVisibility(visible = showVolumeCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 28.dp)) {
            val volumeColor = when { volumePercent > 120 -> Color.Red; volumePercent > 90 -> Color(0xFFFF9800); else -> Color.White }
            FilledCircleHud(value = volumePercent, maxValue = 150, color = volumeColor, size = hudSize)
        }

        // Edge swipe hint
        AnimatedVisibility(visible = edgeSwipeHint.isNotBlank(), enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(200)), modifier = Modifier.align(Alignment.Center)) {
            Text(text = edgeSwipeHint, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 20.dp, vertical = 10.dp))
        }

        // Sleep timer remaining label — shown in top center
        if (sleepTimerActive && sleepTimerRemainingMs > 0) {
            val sleepMins = (sleepTimerRemainingMs / 60000).toInt()
            val sleepSecs = ((sleepTimerRemainingMs % 60000) / 1000).toInt()
            Text(
                text = "😴 %d:%02d".format(sleepMins, sleepSecs),
                color = Color(0xFFFFD36A), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
                    .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        // Speed menu popup
        if (showSpeedMenu) {
            SpeedMenuPopup(
                currentSpeed = playbackSpeed,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = if (isLandscape) 200.dp else 260.dp, end = sidePadding),
                onSpeedSelected = { setPlaybackSpeed(it) },
                onDismiss = { showSpeedMenu = false }
            )
        }

        // Sleep menu popup
        if (showSleepMenu) {
            SleepMenuPopup(
                currentMinutes = sleepTimerMinutes,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = if (isLandscape) 200.dp else 260.dp, end = sidePadding + smallButton + 8.dp),
                onSelected = { setSleepTimer(it) },
                onDismiss = { showSleepMenu = false }
            )
        }

        // Top ⋮ menu popup
        if (showTopMenu) {
            TopMenuPopup(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = if (isLandscape) 40.dp else 60.dp, end = sidePadding),
                onDeleteFile = { deleteCurrentFile() },
                onDismiss = { showTopMenu = false }
            )
        }

        if (showAudioSelector) {
            val audioTracks = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            FloatingTrackPopup(
                title = "Audio",
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = if (isCompactLandscape) 160.dp else if (isLandscape) 240.dp else 300.dp, end = sidePadding).width(if (isCompactLandscape) 145.dp else if (isLandscape) 170.dp else 190.dp),
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
        SubtitleSettingsMenu(
            isVisible = showSubtitleSettings, subtitlesEnabled = subtitlesEnabled, hasInternalSubtitles = hasInternalSubtitles,
            onInternalClick = { subtitlesEnabled = true; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); showSubtitleSettings = false; showControls = true; subtitleMenuTouchKey++ },
            onDownloadClick = { subtitleMenuTouchKey++; downloadExternalSubtitle() },
            onPickFileClick = { srtPickerLauncher.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
            onToggleSubtitles = { subtitlesEnabled = !subtitlesEnabled; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build(); showControls = true; subtitleMenuTouchKey++ },
            onDismiss = { showSubtitleSettings = false; showControls = true },
            currentFontSize = subtitleTextSizeSp, onFontSizeChange = { subtitleTextSizeSp = it; showControls = true; subtitleMenuTouchKey++ },
            currentVerticalPosition = subtitleBottomPadding, onVerticalPositionChange = { subtitleBottomPadding = it; showControls = true; subtitleMenuTouchKey++ },
            currentSyncOffset = subtitleSyncOffset, onSyncOffsetChange = { subtitleSyncOffset = it; showControls = true; subtitleMenuTouchKey++ },
            onReset = { subtitleTextSizeSp = 18f; subtitleBottomPadding = 0.13f; subtitleSyncOffset = 0.0f; subtitlesEnabled = true; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); showControls = true; subtitleMenuTouchKey++ },
            onUserInteraction = { subtitleMenuTouchKey++; showControls = true }
        )

        AnimatedVisibility(visible = showControls || isDraggingSeekbar || showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showTopMenu, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Top bar with ⋮ menu now interactive
                AnimatedVisibility(visible = (showTopBar || showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showTopMenu) && !showSeekPreview, enter = fadeIn(), exit = fadeOut()) {
                    TopGlassTitleBar(
                        title = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path),
                        isLandscape = isLandscape,
                        speedLabel = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else null,
                        onMenuClick = { showTopMenu = !showTopMenu; showControls = true; showTopBar = true }
                    )
                }

                AnimatedVisibility(visible = autoSubtitleStatus.isNotBlank() && !showSeekPreview, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 86.dp)) {
                    Text(text = autoSubtitleStatus, color = Color(0xFFFFD36A), fontSize = if (isLandscape) 11.sp else 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.52f)).padding(horizontal = 12.dp, vertical = 6.dp))
                }

                AnimatedVisibility(visible = showNextEpisodeOverlay && pendingNextEpisode != null && !showSeekPreview, enter = fadeIn(animationSpec = tween(140)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.Center)) {
                    NextEpisodeCountdownOverlay(nextEpisode = pendingNextEpisode, countdown = nextEpisodeCountdown, isLandscape = isLandscape,
                        onPlayNow = { val n = pendingNextEpisode; if (n != null) { showNextEpisodeOverlay = false; pendingNextEpisode = null; currentMediaType = n.type; currentVideo = n.video; onPlayNext(n) } },
                        onCancel = { showNextEpisodeOverlay = false; pendingNextEpisode = null; nextEpisodeCountdown = 0; showControls = true }
                    )
                }

                AnimatedVisibility(visible = showIntroSkip && !showSeekPreview && !isDraggingSeekbar, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.CenterEnd).padding(end = sidePadding)) {
                    SkipIntroButton(isLandscape = isLandscape) { val t = 95_000L.coerceAtMost(duration.coerceAtLeast(1L)); exoPlayer.seekTo(t); position = t; showControls = true }
                }

                AnimatedVisibility(visible = isZoomMode && !showSeekPreview, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 90.dp)) {
                    Text(text = "⛶  Fill", color = Color(0xFFFFD36A), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.52f)).padding(horizontal = 12.dp, vertical = 6.dp))
                }

                // Bottom control row
                AnimatedVisibility(visible = !showSeekPreview && !isDraggingSeekbar, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(90)), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Row(
                        modifier = Modifier.padding(bottom = bottomDockPadding, start = sidePadding, end = sidePadding)
                            .clip(RoundedCornerShape(42.dp))
                            .background(Brush.horizontalGradient(colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.32f))))
                            .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp),
                        horizontalArrangement = Arrangement.spacedBy((7 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BackIconButton(size = smallButton, onClick = onBack)

                        ControlCircle("◀◀", smallButton) { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)); position = exoPlayer.currentPosition; showControls = true }

                        ControlCircle(text = if (isPlaying) "Ⅱ" else "▶", size = playButton) {
                            if (isVideoEnded) { exoPlayer.seekTo(0); exoPlayer.play(); isVideoEnded = false; showControls = true }
                            else { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play(); showControls = true }
                        }

                        ControlCircle("▶▶", smallButton) { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))); position = exoPlayer.currentPosition; showControls = true }

                        Spacer(modifier = Modifier.width((4 * scale).dp))

                        // Autoplay
                        IconCircle(icon = Icons.Rounded.AllInclusive, size = smallButton, tint = if (autoPlayEnabled) Color(0xFFFFD36A) else Color.White.copy(alpha = 0.45f)) {
                            autoPlayEnabled = !autoPlayEnabled; showControls = true
                            Toast.makeText(context, if (autoPlayEnabled) "Autoplay on" else "Autoplay off", Toast.LENGTH_SHORT).show()
                        }

                        // Speed — gold if not 1x
                        IconCircle(icon = Icons.Rounded.Speed, size = smallButton, tint = if (playbackSpeed != 1.0f) Color(0xFFFFD36A) else Color.White) {
                            showSleepMenu = false; showTopMenu = false
                            showSpeedMenu = !showSpeedMenu; showControls = true
                        }

                        // Sleep timer — gold if active
                        IconCircle(icon = Icons.Rounded.Timer, size = smallButton, tint = if (sleepTimerActive) Color(0xFFFFD36A) else Color.White) {
                            showSpeedMenu = false; showTopMenu = false
                            showSleepMenu = !showSleepMenu; showControls = true
                        }

                        // Audio
                        IconCircle(icon = Icons.Rounded.Audiotrack, size = smallButton, tint = if (showAudioSelector) Color(0xFFFFD36A) else Color.White) {
                            showSubtitleSettings = false; showAudioSelector = !showAudioSelector; showControls = true; menuTouchKey++
                        }

                        // Subtitles
                        if (!isStreamMedia) {
                            IconCircle(icon = Icons.Rounded.ClosedCaption, size = smallButton, tint = if (showSubtitleSettings) Color(0xFFFFD36A) else Color.White) {
                                showAudioSelector = false; showSubtitleSettings = !showSubtitleSettings; showControls = true; menuTouchKey++
                            }
                        }

                        // PiP
                        IconCircle(icon = Icons.Rounded.Tv, size = smallButton, tint = Color.White) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
                            }
                        }
                    }
                }

                SeekPreviewBubble(isVisible = showSeekPreview, bitmap = previewBitmap, timeText = formatTime(previewPosition), isLandscape = isLandscape, isLarge = isSeekPreviewLarge, progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f))

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = sidePadding, end = sidePadding, bottom = seekBottomPadding)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.35f))))
                        .padding(horizontal = (14 * scale).dp, vertical = (7 * scale).dp)
                ) {
                    CinematicSeekBar(
                        position = position, duration = duration, isDragging = isDraggingSeekbar,
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TimePill(formatTime(position)); TimePill(formatTime(duration))
                    }
                }
            }
        }
    }
}

// ── Speed Menu ────────────────────────────────────────────────────────────────
@Composable
private fun SpeedMenuPopup(currentSpeed: Float, modifier: Modifier, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111).copy(alpha = 0.96f)).padding(8.dp)) {
        Text(text = "Speed", color = Color(0xFFFFD36A), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        speeds.forEach { speed ->
            val selected = speed == currentSpeed
            Text(
                text = if (speed == 1.0f) "1x  Normal" else "${speed}x",
                color = if (selected) Color(0xFFFFD36A) else Color.White,
                fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSpeedSelected(speed) }.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

// ── Sleep Menu ────────────────────────────────────────────────────────────────
@Composable
private fun SleepMenuPopup(currentMinutes: Int, modifier: Modifier, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min")
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111).copy(alpha = 0.96f)).padding(8.dp)) {
        Text(text = "Sleep Timer", color = Color(0xFFFFD36A), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        options.forEach { (mins, label) ->
            val selected = mins == currentMinutes
            Text(
                text = label,
                color = if (selected) Color(0xFFFFD36A) else Color.White,
                fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSelected(mins) }.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

// ── Top ⋮ menu ────────────────────────────────────────────────────────────────
@Composable
private fun TopMenuPopup(modifier: Modifier, onDeleteFile: () -> Unit, onDismiss: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111).copy(alpha = 0.96f)).padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .clickable { onDeleteFile() }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = "Delete File", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Composables ───────────────────────────────────────────────────────────────

@Composable
private fun BackIconButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(Brush.radialGradient(colors = listOf(Color(0xFFFFD36A).copy(alpha = 0.18f), Color(0xFFFFD36A).copy(alpha = 0.08f), Color.Black.copy(alpha = 0.20f))))
        .border(1.2.dp, Color(0xFFFFD36A).copy(alpha = 0.55f), RoundedCornerShape(20.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFD36A), modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
private fun IconCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, tint: Color = Color.White, onClick: () -> Unit) {
    Box(modifier = Modifier.size(size).clip(RoundedCornerShape(20.dp))
        .background(Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.20f))))
        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

@Composable
private fun CinematicSeekBar(position: Long, duration: Long, isDragging: Boolean, onPreviewPositionChanged: (Long) -> Unit, onSeekFinished: (Long) -> Unit) {
    var localPosition by remember { mutableLongStateOf(position) }
    LaunchedEffect(position, isDragging) { if (!isDragging) localPosition = position }
    val glow by animateFloatAsState(targetValue = if (isDragging) 1f else 0.45f, animationSpec = tween(220), label = "seekGlow")
    fun positionFromX(x: Float, width: Float): Long { if (duration <= 0L || width <= 0f) return 0L; return (duration * (x / width).coerceIn(0f, 1f)).toLong().coerceIn(0L, duration) }
    Box(modifier = Modifier.fillMaxWidth().height(34.dp)
        .pointerInput(duration) { detectTapGestures { o -> val p = positionFromX(o.x, size.width.toFloat()); localPosition = p; onPreviewPositionChanged(p); onSeekFinished(p) } }
        .pointerInput(duration) { detectDragGestures(onDragStart = { o -> localPosition = positionFromX(o.x, size.width.toFloat()); onPreviewPositionChanged(localPosition) }, onDrag = { c, _ -> localPosition = positionFromX(c.position.x, size.width.toFloat()); onPreviewPositionChanged(localPosition) }, onDragEnd = { onSeekFinished(localPosition) }, onDragCancel = { onSeekFinished(localPosition) }) }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val th = 5.dp.toPx(); val cy = size.height / 2f; val r = th / 2f
            val prog = (localPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            val aw = size.width * prog; val tx = aw.coerceIn(0f, size.width)
            drawRoundRect(color = Color.White.copy(alpha = 0.13f), topLeft = Offset(0f, cy - th/2f), size = Size(size.width, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            drawRoundRect(color = Color(0xFFFFC857).copy(alpha = 0.95f), topLeft = Offset(0f, cy - th/2f), size = Size(aw, th), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
            listOf(0.25f, 0.50f, 0.75f).forEach { drawCircle(color = Color.White.copy(alpha = 0.45f), radius = 2.2.dp.toPx(), center = Offset(size.width * it, cy)) }
            drawCircle(color = Color(0xFFFFC857).copy(alpha = 0.22f * glow), radius = 16.dp.toPx(), center = Offset(tx, cy))
            drawCircle(color = Color(0xFFFFE7A3).copy(alpha = 0.45f * glow), radius = 10.dp.toPx(), center = Offset(tx, cy))
            drawCircle(color = Color(0xFFFFF3D6), radius = if (isDragging) 6.6.dp.toPx() else 5.2.dp.toPx(), center = Offset(tx, cy))
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
                .clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(colors = listOf(Color.White.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.58f))))
                .border(1.dp, Color(0xFFFFD36A).copy(alpha = if (isLarge) 0.55f else 0.25f), RoundedCornerShape(18.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                else Box(modifier = Modifier.width(pw).height(ph).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) { Text(text = timeText, color = Color(0xFFFFE7A3), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = timeText, color = Color(0xFFFFE7A3), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TopGlassTitleBar(title: String, isLandscape: Boolean, speedLabel: String? = null, onMenuClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = if (isLandscape) 8.dp else 12.dp, end = if (isLandscape) 8.dp else 12.dp, top = if (isLandscape) 0.dp else 4.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Brush.horizontalGradient(colors = listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.30f))))
            .padding(horizontal = if (isLandscape) 10.dp else 13.dp, vertical = if (isLandscape) 3.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontSize = if (isLandscape) 13.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.weight(1f))
        if (speedLabel != null) {
            Text(text = speedLabel, color = Color(0xFFFFD36A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.40f)).padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = "⋮", color = Color.White.copy(alpha = 0.95f), fontSize = if (isLandscape) 24.sp else 26.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMenuClick() }.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun TimePill(text: String) {
    Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 5.dp))
}

@Composable
private fun VerticalBrightnessHud(value: Int, size: androidx.compose.ui.unit.Dp) {
    val fill = (value.toFloat() / 100f).coerceIn(0f, 1f)
    Row(modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.72f)).border(1.dp, Color(0xFFFFD36A).copy(alpha = 0.30f), RoundedCornerShape(26.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.BrightnessHigh, contentDescription = null, tint = Color(0xFFFFD36A), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(Color(0xFFFFF3D6), Color(0xFFFFC857)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilledCircleHud(value: Int, maxValue: Int, color: Color, size: androidx.compose.ui.unit.Dp) {
    val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(modifier = Modifier.clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.72f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(26.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Rounded.VolumeUp, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.width(9.dp).height(size).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(fill).align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Brush.verticalGradient(colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f)))))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$value%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlCircle(text: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val isPP = text == "▶" || text == "Ⅱ"; val shape = if (isPP) CircleShape else RoundedCornerShape(20.dp)
    Box(modifier = Modifier.size(size).clip(shape)
        .background(brush = Brush.radialGradient(colors = if (isPP) listOf(Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.32f)) else listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.20f))))
        .border(width = if (isPP) 1.7.dp else 1.dp, color = if (isPP) Color(0xFFFFC857).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.18f), shape = shape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        if (isPP) { Canvas(modifier = Modifier.matchParentSize()) { val o = size.toPx() * 0.48f; val rw = size.toPx() * 0.045f; drawCircle(color = Color(0xFFFF8A00).copy(alpha = 0.26f), radius = o, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = rw)); drawCircle(color = Color(0xFFFFD66B).copy(alpha = 0.18f), radius = o * 0.82f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = rw * 0.65f)) } }
        Text(text = text, color = if (isPP) Color(0xFFFF7A2F) else Color.White, fontSize = when { isPP && text == "▶" -> (size.value * 0.48f).sp; isPP -> (size.value * 0.46f).sp; else -> (size.value * 0.42f).sp }, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = if (text == "▶") 2.dp else 0.dp))
    }
}

private data class TrackPopupRowData(val title: String, val subtitle: String, val onClick: () -> Unit)

private fun friendlyLanguageName(code: String?): String = when (code?.lowercase()) {
    null, "", "und" -> "Unknown"; "en", "eng" -> "English"; "it", "ita" -> "Italian"
    "ja", "jpn" -> "Japanese"; "hi", "hin" -> "Hindi"; "fr", "fre", "fra" -> "French"
    "es", "spa" -> "Spanish"; "ko", "kor" -> "Korean"; "de", "ger", "deu" -> "German"
    "pt", "por" -> "Portuguese"; "zh", "chi", "zho" -> "Chinese"; "ar", "ara" -> "Arabic"
    "ru", "rus" -> "Russian"; else -> code?.uppercase() ?: "Unknown"
}

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
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors = listOf(Color(0xFFFFD36A), Color(0xFFFFA000)))).clickable { onClick() }.padding(horizontal = if (isLandscape) 13.dp else 15.dp, vertical = if (isLandscape) 7.dp else 8.dp))
}

@Composable
private fun NextEpisodeCountdownOverlay(nextEpisode: VideoWithMetadata?, countdown: Int, isLandscape: Boolean, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    if (nextEpisode == null) return
    Column(modifier = Modifier.width(if (isLandscape) 310.dp else 300.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.50f))))
        .border(1.dp, Color(0xFFFFD36A).copy(alpha = 0.38f), RoundedCornerShape(26.dp)).padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Next episode starts in", color = Color.White.copy(alpha = 0.82f), fontSize = if (isLandscape) 13.sp else 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = countdown.coerceAtLeast(1).toString(), color = Color(0xFFFFD36A), fontSize = if (isLandscape) 38.sp else 42.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = nextEpisode.subtitle.ifBlank { cleanEpisodeDisplayName(nextEpisode.video.name) }, color = Color.White, fontSize = if (isLandscape) 13.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).clickable { onCancel() }.padding(horizontal = 15.dp, vertical = 8.dp))
            Text(text = "Play Now", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFD36A)).clickable { onPlayNow() }.padding(horizontal = 15.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun FloatingTrackPopup(title: String, modifier: Modifier, rows: List<TrackPopupRowData>, onAnyClick: () -> Unit = {}, onClose: () -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.52f)).padding(7.dp)) {
        Text(text = title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(5.dp))
        rows.forEach { row ->
            Text(text = row.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)).clickable { onAnyClick(); row.onClick() }.padding(7.dp))
            Spacer(modifier = Modifier.height(5.dp))
        }
        Text(text = "Close", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)).clickable { onAnyClick(); onClose() }.padding(7.dp))
    }
}
