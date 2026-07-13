package com.sole.cinevault

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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.sole.cinevault.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val haptics = LocalHapticFeedback.current

    var audioSyncMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(audioSyncMs) { AudioSyncHolder.offsetUs = audioSyncMs * 1000L }

    var audioIconX by remember { mutableFloatStateOf(0f) }
    var subIconX by remember { mutableFloatStateOf(0f) }
    var clusterHeightPx by remember { mutableFloatStateOf(0f) }

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

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showSrtBrowser by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepTimerActive by remember { mutableStateOf(false) }

    var subtitleTextSizeSp by remember { mutableFloatStateOf(22f) }
    var subtitleSizeDefaultApplied by remember { mutableStateOf(false) }
    var subtitleBottomPadding by remember { mutableFloatStateOf(0.02f) }
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

    val exoPlayer: ExoPlayer = remember {
        ExoPlayer.Builder(context)
            .setRenderersFactory(CineRenderersFactory(context))
            .setTrackSelector(trackSelector)
            .build()
    }

    val canDownloadExternalSubtitles = currentMediaType.equals("movie", ignoreCase = true) || currentMediaType.equals("tv", ignoreCase = true)
    val isCurrentTvShow = currentMediaType.equals("tv", ignoreCase = true)
    val isStreamMedia = currentMediaType.equals("stream", ignoreCase = true)

    fun closeAllMenus() {
        showAudioSelector = false
        showSubtitleSettings = false
        showSpeedMenu = false
        showSleepMenu = false
        showSrtBrowser = false
    }

    var pendingSrtUri by remember { mutableStateOf<Uri?>(null) }
    val srtPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            pendingSrtUri = uri
        }
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeleteSubtitle(file: java.io.File) {
        FileManagementHelper.deleteFile(
            context = context,
            file = file,
            onNeedsConsent = { intentSender -> deleteConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) },
            onDeleted = { Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show() },
            onFailed = { e -> Toast.makeText(context, "Couldn't delete: ${e.message}", Toast.LENGTH_SHORT).show() }
        )
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

    // NOTE: an attempt was made to attach subtitles via MergingMediaSource to
    // avoid the reload blip entirely, but it caused a worse regression —
    // playback would stop dead and the play button stopped responding once
    // the merge completed. Reverted back to the reload approach below, which
    // is reliable; it accepts a brief blip in exchange for actually working.
    // The resume-position bug (playback jumping backwards) IS still fixed —
    // see the resumeAt capture right before each playCurrentVideoWithSubtitle
    // call below, which happens after the download finishes, not before.

    fun downloadExternalSubtitle() {
        if (!canDownloadExternalSubtitles) {
            Toast.makeText(context, "Subtitle download is only for Movies and TV Shows", Toast.LENGTH_SHORT).show()
            showSubtitleSettings = false; autoSubtitleStatus = ""; return
        }
        if (subtitleDownloadInProgress) { Toast.makeText(context, "Subtitle search already running", Toast.LENGTH_SHORT).show(); return }
        showControls = true; showSubtitleSettings = false
        autoSubtitleStatus = "Searching subtitles..."; subtitleDownloadInProgress = true
        scope.launch {
            try {
                val subtitleUri = OpenSubtitlesClient.downloadBestEnglishSubtitle(context, currentVideo.path)
                if (subtitleUri != null) {
                    // Captured HERE, right before the reload — not before the
                    // multi-second search — so playback resumes from where it
                    // actually is instead of jumping backwards.
                    val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
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
        showAudioSelector = false; showSubtitleSettings = false; showSpeedMenu = false; showSleepMenu = false; showSrtBrowser = false
        pendingNextEpisode = null; nextEpisodeCountdown = 0; showNextEpisodeOverlay = false
        previewBitmap = null; previewFrames = emptyList(); isVideoEnded = false
        if (!isStreamMedia) recordWatchHistory(context, currentVideo.path, cleanVideoTitle(currentVideo.path))
        playCurrentVideoWithSubtitle(resumePosition = savedPosition)
        if (!isStreamMedia && canDownloadExternalSubtitles && autoSubtitleAttemptedForPath != currentVideo.path) {
            autoSubtitleAttemptedForPath = currentVideo.path
            scope.launch {
                delay(1200); if (subtitleDownloadInProgress) return@launch
                subtitleDownloadInProgress = true
                autoSubtitleStatus = "Searching subtitles..."
                try {
                    val uri = OpenSubtitlesClient.downloadBestEnglishSubtitle(context, currentVideo.path)
                    if (uri != null) {
                        val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
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
            AudioSyncHolder.offsetUs = 0L
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

    LaunchedEffect(showControls, showAudioSelector, showSubtitleSettings, showSpeedMenu, showSleepMenu, showSrtBrowser, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showSrtBrowser
        if (showControls && !anyMenuOpen && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !anyMenuOpen) showControls = false
        }
    }

    LaunchedEffect(showTopBar, showAudioSelector, showSubtitleSettings, showSpeedMenu, showSleepMenu, showSrtBrowser, isDraggingSeekbar) {
        val anyMenuOpen = showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu || showSrtBrowser
        if (showTopBar && !anyMenuOpen && !isDraggingSeekbar) {
            delay(2800)
            if (!isDraggingSeekbar && !anyMenuOpen) showTopBar = false
        }
    }

    LaunchedEffect(showAudioSelector, menuTouchKey) { if (showAudioSelector) { delay(9000); showAudioSelector = false } }
    LaunchedEffect(showSubtitleSettings, subtitleMenuTouchKey) { if (showSubtitleSettings) { delay(9000); showSubtitleSettings = false } }
    LaunchedEffect(showSrtBrowser) { if (showSrtBrowser) { delay(20000); showSrtBrowser = false } }
    LaunchedEffect(showSpeedMenu) { if (showSpeedMenu) { delay(8000); showSpeedMenu = false } }
    LaunchedEffect(showSleepMenu) { if (showSleepMenu) { delay(8000); showSleepMenu = false } }
    LaunchedEffect(brightnessGestureKey) { if (brightnessGestureKey > 0) { delay(1400); showBrightnessCircle = false } }
    LaunchedEffect(volumeGestureKey) { if (volumeGestureKey > 0) { delay(1400); showVolumeCircle = false } }

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

    LaunchedEffect(subtitleSyncOffset) {
        val offsetUs = (subtitleSyncOffset * 1_000_000L).toLong()
        val currentGroups = exoPlayer.currentTracks.groups
        val textGroup = currentGroups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
        if (textGroup != null) {
            try {
                val params = exoPlayer.trackSelectionParameters
                exoPlayer.trackSelectionParameters = params.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                    .build()
                val currentItem = exoPlayer.currentMediaItem ?: return@LaunchedEffect
                val newItem = currentItem.buildUpon()
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(0)
                            .build()
                    ).build()
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
        val layoutScale = when { isCompactLandscape -> 0.70f; isSmallPhone && !isLandscape -> 0.78f; isSmallPhone -> 0.82f; isLandscape -> 0.90f; else -> 1f }
        val deckNaturalWidth = 66f * 6 + 98f + 7f * 7 + 24f
        val fitScale = ((maxWidth.value - 32f) / deckNaturalWidth).coerceAtMost(1f)
        val scale = minOf(layoutScale, fitScale).coerceAtLeast(0.42f)
        val playButton = (98 * scale).dp
        val smallButton = (66 * scale).dp
        val hudSize = (72 * scale).dp
        val sidePadding = if (isCompactLandscape) 8.dp else 16.dp
        // Landscape: controls brought DOWN closer to the seek bar (smaller
        // dock padding) while the seek bar is brought UP ~30% from where it
        // was, leaving a modest — not huge — gap between the two.
        // Portrait: seek bar brought up a bit from the very edge, but still
        // clearly below (closer to the bottom than) the transport dock.
        // Portrait: seek bar brought way up from the bottom edge to sit
        // directly under the transport dock instead of floating separately
        // near the bottom with a big empty gap.
        // Landscape: another pass on top of the previous adjustment — seek
        // bar nudged up further, dock brought down a bit more, still leaving
        // a modest (not huge) gap between the two.
        // Landscape dock padding was cut too aggressively last round — once
        // the dock's actual rendered height (~80-100dp incl. play button +
        // padding) is accounted for, there was only ~1dp of clearance left
        // above the seek bar, which is why they were overlapping. Restored
        // enough headroom for a real ~15-20dp gap between the two.
        val bottomDockPadding = when { isCompactLandscape -> 76.dp; isLandscape -> 90.dp; else -> 152.dp }
        val seekBottomPadding = when { isCompactLandscape -> 13.dp; isLandscape -> 17.dp; else -> 92.dp }
        val showIntroSkip = isCurrentTvShow && position in 5_000L..95_000L
        val topClusterPaddingTop = if (isLandscape) 10.dp else 18.dp

        // Default subtitle size: 16sp portrait, 18sp landscape — applied once,
        // so it doesn't stomp on a size the person already dialed in manually.
        LaunchedEffect(isLandscape) {
            if (!subtitleSizeDefaultApplied) {
                subtitleTextSizeSp = if (isLandscape) 18f else 16f
                subtitleSizeDefaultApplied = true
            }
        }

        val uiScale = (maxWidth.value / 400f).coerceIn(0.85f, 1.25f)

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        fun anchoredX(iconCenterX: Float, popupWidth: Dp): Int {
            val pw = with(density) { popupWidth.toPx() }
            val pad = with(density) { 8.dp.toPx() }
            return (iconCenterX - pw / 2f).coerceIn(pad, (screenWidthPx - pw - pad).coerceAtLeast(pad)).roundToInt()
        }
        // Always keeps the popup's bottom edge clear of the transport control
        // dock. Previously this would shrink the bottom offset for tall popups
        // to keep their top on-screen, which let the popup's bottom slide down
        // behind the control panel — exactly the overlap that was reported.
        // Now the dock clearance always wins; if a popup is very tall it simply
        // scrolls internally instead of overlapping the controls.
        fun anchoredY(desiredBottomPadding: Dp, popupHeightEstimate: Dp): Dp = desiredBottomPadding
        val popupBottomPadding = bottomDockPadding + playButton + 18.dp

        // Popup sizing — tightened and bounded against BOTH screen width and
        // height so windows never dwarf the actual device screen.
        // Subtitle popup: SubtitleSettingsMenu.kt now sizes itself internally
        // via LocalConfiguration (screenWidthDp * 0.17/0.35, halved from the
        // previous pass) — this mirrors that exact formula so the anchor
        // positioning lines up with what actually renders, rather than
        // guessing a width from outside and fighting the component's own size.
        val subtitlePopupWidthBase = if (isLandscape) (maxWidth.value * 0.30f).dp.coerceIn(210.dp, 270.dp) else (maxWidth.value * 0.62f).dp.coerceIn(220.dp, 300.dp)
        val subtitlePopupWidth = if (isLandscape) (maxWidth.value * 0.17f).dp.coerceIn(115.dp, 150.dp) else (maxWidth.value * 0.35f).dp.coerceIn(120.dp, 170.dp)
        val subtitlePopupHeightEstimate = (((if (isCompactLandscape || isLandscape) 220f else 360f) * uiScale).dp).coerceAtMost(maxHeight * 0.45f)
        // SRT file browser keeps the pre-reduction width — it's a plain file list, not the dense settings menu
        val srtPopupWidth = (subtitlePopupWidthBase.value * uiScale).dp.coerceAtMost(maxWidth * 0.86f)
        val srtPopupMaxHeight = (((if (isCompactLandscape) 160f else if (isLandscape) 200f else 280f) * uiScale).dp).coerceAtMost(maxHeight * 0.5f)
        // Audio popup: 40% narrower
        val audioPopupWidth = ((((if (isCompactLandscape) 175f else if (isLandscape) 190f else 205f) * uiScale).dp).coerceAtMost(maxWidth * 0.75f)) * 0.6f
        // Speed/Sleep popups: 40% smaller footprint, compact rows below
        // Speed/Sleep popups: width stays 40% narrower, but landscape height
        // gets most of that back — the narrow width plus the previous height
        // cut made 5-6 stacked options feel cramped specifically in landscape.
        val smallMenuWidth = (((165f * uiScale).dp).coerceAtMost(maxWidth * 0.6f)) * 0.6f
        val smallMenuHeightScale = if (isLandscape) 0.95f else 0.6f
        val smallMenuMaxHeight = ((((if (isCompactLandscape) 150f else if (isLandscape) 190f else 230f) * uiScale).dp).coerceAtMost(maxHeight * 0.55f)) * smallMenuHeightScale
        val topIconSize = (44 * uiScale * scale.coerceAtLeast(0.75f)).dp

        val currentMeta = remember(currentVideo.path, episodeList) {
            episodeList.firstOrNull { it.video.path == currentVideo.path }
                ?: episodeList.firstOrNull { it.video.name == currentVideo.name }
        }

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
                                showSrtBrowser -> showSrtBrowser = false
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
                .pointerInput(currentVideo.path, episodeList) {
                    var dragStartX = 0f
                    var dragTotalX = 0f
                    var dragTotalY = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x; dragTotalX = 0f; dragTotalY = 0f
                        },
                        onDragEnd = {
                            brightnessGestureKey++; volumeGestureKey++
                            val w = size.width.toFloat()
                            val isHorizontal = kotlin.math.abs(dragTotalX) > kotlin.math.abs(dragTotalY) * 1.5f &&
                                    kotlin.math.abs(dragTotalX) > 48.dp.toPx()
                            if (isHorizontal) {
                                when {
                                    dragStartX < w * 0.12f && dragTotalX > 0f -> playPrevious()
                                    dragStartX > w * 0.88f && dragTotalX < 0f -> playNext()
                                }
                            }
                        },
                        onDragCancel = { brightnessGestureKey++; volumeGestureKey++ },
                        onDrag = { change, dragAmount ->
                            dragTotalX += dragAmount.x; dragTotalY += dragAmount.y
                            val x = change.position.x; val w = size.width
                            val absX = kotlin.math.abs(dragAmount.x); val absY = kotlin.math.abs(dragAmount.y)
                            val gestureIsVertical = kotlin.math.abs(dragTotalY) >= kotlin.math.abs(dragTotalX)
                            if (gestureIsVertical && absY > absX) {
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

        AnimatedVisibility(visible = showBrightnessCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).padding(top = 86.dp, start = 28.dp)) {
            VerticalBrightnessHud(value = brightnessPercent, size = hudSize)
        }
        AnimatedVisibility(visible = showVolumeCircle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 28.dp)) {
            val volumeColor = when { volumePercent > 120 -> Color.Red; volumePercent > 90 -> Color(0xFFFF9800); else -> Color.White }
            FilledCircleHud(value = volumePercent, maxValue = 150, color = volumeColor, size = hudSize)
        }

        AnimatedVisibility(visible = edgeSwipeHint.isNotBlank(), enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(200)), modifier = Modifier.align(Alignment.Center)) {
            Text(text = edgeSwipeHint, color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 20.dp, vertical = 10.dp))
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
        // Portrait now stacks a title row above the icon cluster (title + 10dp
        // spacer), so popups anchored below the cluster need that extra offset
        // or they'd land under the title instead of under the icons.
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
        AnimatedVisibility(visible = showSubtitleSettings, enter = fadeIn(animationSpec = tween(150)), exit = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = anchoredY(popupBottomPadding, subtitlePopupHeightEstimate)).offset { IntOffset(anchoredX(subIconX, subtitlePopupWidth), 0) }) {
            // SubtitleSettingsMenu.kt sizes and scrolls itself internally via
            // LocalConfiguration — no outer width/scroll wrapper needed (an
            // earlier pass added one, which just fought its own sizing logic).
            SubtitleSettingsMenu(
                isVisible = true,
                subtitlesEnabled = subtitlesEnabled, hasInternalSubtitles = hasInternalSubtitles,
            onInternalClick = {
                if (hasInternalSubtitles) {
                    subtitlesEnabled = true; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); showSubtitleSettings = false; showControls = true
                } else {
                    showSubtitleSettings = false; showSrtBrowser = true; showControls = true
                }
                subtitleMenuTouchKey++
            },
            onDownloadClick = { subtitleMenuTouchKey++; downloadExternalSubtitle() },
            onPickFileClick = { showSubtitleSettings = false; showSrtBrowser = true; showControls = true },
            onToggleSubtitles = { subtitlesEnabled = !subtitlesEnabled; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build(); showControls = true; subtitleMenuTouchKey++ },
            onDismiss = { showSubtitleSettings = false; showControls = true },
            currentFontSize = subtitleTextSizeSp, onFontSizeChange = { subtitleTextSizeSp = it; showControls = true; subtitleMenuTouchKey++ },
            currentVerticalPosition = subtitleBottomPadding, onVerticalPositionChange = { subtitleBottomPadding = it; showControls = true; subtitleMenuTouchKey++ },
            currentSyncOffset = subtitleSyncOffset, onSyncOffsetChange = { subtitleSyncOffset = it; showControls = true; subtitleMenuTouchKey++ },
            onReset = { subtitleTextSizeSp = if (isLandscape) 18f else 16f; subtitleBottomPadding = 0.02f; subtitleSyncOffset = 0.0f; subtitlesEnabled = true; trackSelector.parameters = trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build(); showControls = true; subtitleMenuTouchKey++ },
            onUserInteraction = { subtitleMenuTouchKey++; showControls = true }
        )
        }

        AnimatedVisibility(visible = showControls || isDraggingSeekbar || showAudioSelector || showSubtitleSettings || showSpeedMenu || showSleepMenu, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {

                // Title, rating pill, and the speed/sleep/PiP cluster all now fade
                // together with the rest of the controls (showControls' 4.5s timer)
                // instead of the title/pill previously using showTopBar's shorter
                // 2.8s timer, which made them vanish noticeably earlier than
                // everything else.
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
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = sidePadding)
                                .onGloballyPositioned { clusterHeightPx = it.size.height.toFloat() }
                        )
                    }
                } else {
                    // Portrait: title sits on its own row, above the pill/icon row —
                    // "on top" rather than overlapping/competing with them.
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

                AnimatedVisibility(visible = autoSubtitleStatus.isNotBlank() && !showSeekPreview, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(120)), modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isLandscape) 54.dp else 86.dp)) {
                    Text(text = autoSubtitleStatus, color = AmberCore, fontSize = if (isLandscape) 11.sp else 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 12.dp, vertical = 6.dp))
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
                    Text(text = "⛶  Fill", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong).padding(horizontal = 12.dp, vertical = 6.dp))
                }

                AnimatedVisibility(visible = !showSeekPreview && !isDraggingSeekbar, enter = fadeIn(animationSpec = tween(120)), exit = fadeOut(animationSpec = tween(90)), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Row(
                        modifier = Modifier.padding(bottom = bottomDockPadding, start = sidePadding, end = sidePadding)
                            .glassPanel(cornerRadius = 42.dp, fill = GlassSurfaceStrong)
                            .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp)
                            // Safety net: if the deck's natural width ever exceeds the
                            // available screen width on a narrow/portrait device, it now
                            // scrolls instead of clipping the last icon (the subtitle
                            // toggle) off-screen.
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
                            IconCircle(icon = Icons.Rounded.ClosedCaption, size = smallButton, tint = if (showSubtitleSettings) AmberCore else TextBright, modifier = Modifier.onGloballyPositioned { subIconX = it.positionInRoot().x + it.size.width / 2f }) {
                                val wasOpen = showSubtitleSettings; closeAllMenus(); showSubtitleSettings = !wasOpen; showControls = true; menuTouchKey++
                            }
                        }
                    }
                }

                SeekPreviewBubble(isVisible = showSeekPreview, bitmap = previewBitmap, timeText = formatTime(previewPosition), isLandscape = isLandscape, isLarge = isSeekPreviewLarge, progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f))

                // Bottom dock — time labels removed; the seek bar now sits alone,
                // closer to the screen edge. Current time only appears as a
                // floating pill above the scrub thumb while dragging (see
                // CinematicSeekBar), using the existing soundwave-bloom effect.
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
        IconCircle(icon = Icons.Rounded.Tv, size = iconSize, tint = TextBright, onClick = onPipClick)
        IconCircle(icon = Icons.Rounded.Timer, size = iconSize, tint = if (sleepTimerActive || showSleepMenu) AmberCore else TextBright, onClick = onSleepClick)
        IconCircle(icon = Icons.Rounded.Speed, size = iconSize, tint = if (playbackSpeed != 1f || showSpeedMenu) AmberCore else TextBright, onClick = onSpeedClick)
    }
    if (isLandscape) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
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
                ?.filter { it.isFile && it.extension.equals("srt", ignoreCase = true) }
                ?.sortedByDescending { it.nameWithoutExtension.lowercase().contains(base) }
                ?.take(25)
                ?.forEach { results.add(it) }
            if (results.size >= 40) break
        }
    } catch (_: Exception) {}
    return results.toList()
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

// Smaller-footprint selectable row used only by the (now 40% narrower)
// Speed/Sleep popups — keeps the shared GlassMenuRow (used by the SRT
// browser, which is already sized well) untouched.
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

// Contained breathing amber glow, drawn INSIDE the button's own circular
// bounds (radial gradient behind the icon). Replaces the old two-ring
// expanding ripple, which drew onto a canvas 1.9x the button's size — that
// oversized canvas was what inflated the whole transport bar's layout height.
// This version never measures larger than `size`, so the control bar stays
// its original, correct size.
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
    var lastChapterZone by remember { mutableIntStateOf(-1) }
    var waveformVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isDragging) {
        if (isDragging) { waveformVisible = true }
        else if (waveformVisible) { delay(2000); waveformVisible = false }
    }
    fun zoneOf(p: Long): Int {
        val fr = p.toFloat() / duration.coerceAtLeast(1L).toFloat()
        return (fr / 0.25f).toInt().coerceIn(0, 3)
    }
    val bloom by animateFloatAsState(targetValue = if (isDragging || waveformVisible) 1f else 0f, animationSpec = tween(if (isDragging || waveformVisible) 300 else 600, easing = FastOutSlowInEasing), label = "liquidBloom")
    val glow by animateFloatAsState(targetValue = if (isDragging) 1f else 0.45f, animationSpec = tween(220), label = "seekGlow")
    fun positionFromX(x: Float, width: Float): Long { if (duration <= 0L || width <= 0f) return 0L; return (duration * (x / width).coerceIn(0f, 1f)).toLong().coerceIn(0L, duration) }

    // BoxWithConstraints so the floating time pill can be positioned in Dp
    // directly above the scrub thumb, following the existing waveform-bloom
    // visibility (isDragging || waveformVisible) instead of a permanent label.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val trackWidth = maxWidth
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)
            .pointerInput(duration) { detectTapGestures { o -> val p = positionFromX(o.x, size.width.toFloat()); localPosition = p; onPreviewPositionChanged(p); onSeekFinished(p) } }
            .pointerInput(duration) { detectDragGestures(
                onDragStart = { o -> localPosition = positionFromX(o.x, size.width.toFloat()); lastChapterZone = zoneOf(localPosition); onPreviewPositionChanged(localPosition) },
                onDrag = { c, _ ->
                    localPosition = positionFromX(c.position.x, size.width.toFloat())
                    val z = zoneOf(localPosition)
                    if (z != lastChapterZone) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastChapterZone = z }
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

// Title pill — previously plain text on glass, which read as "dead" next to
// the amber-glow rating pills and buttons elsewhere in the design. Gives it
// the same visual language: a subtle amber gradient border and a small
// breathing dot (a quiet "now playing" cue) instead of static text sitting
// on its own.
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

// Smaller-footprint row used inside the narrowed audio popup — same visual
// language as GlassMenuRow but tighter text/padding for the reduced width.
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
