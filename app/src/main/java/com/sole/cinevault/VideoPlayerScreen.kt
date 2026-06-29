package com.sole.cinevault

import androidx.compose.ui.graphics.Brush
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.util.TypedValue
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeDown
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// FIX: Proper immersive mode helper using WindowInsetsController (replaces deprecated systemUiVisibility)
private fun Activity.enterImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private fun Activity.exitImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        show(WindowInsetsCompat.Type.systemBars())
    }
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
    val activity = context.findActivity()
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

    // FIX: Separate gesture-active state from display state so icon dismisses correctly
    var isBrightnessDragging by remember { mutableStateOf(false) }
    var isVolumeDragging by remember { mutableStateOf(false) }

    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }

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
    var pendingNextEpisode by remember { mutableStateOf<VideoWithMetadata?>(null) }
    var nextEpisodeCountdown by remember { mutableIntStateOf(0) }
    var showNextEpisodeOverlay by remember { mutableStateOf(false) }

    var isZoomMode by remember { mutableStateOf(false) }
    var showSeekPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableLongStateOf(0L) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSeekPreviewLarge by remember { mutableStateOf(false) }
    var previewFrames by remember {
        mutableStateOf<List<VideoThumbnailHelper.PreviewFrame>>(emptyList())
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("en")
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
    }

    val canDownloadExternalSubtitles =
        currentMediaType.equals("movie", ignoreCase = true) ||
                currentMediaType.equals("tv", ignoreCase = true)

    val isCurrentTvShow =
        currentMediaType.equals("tv", ignoreCase = true)

    val isStreamMedia =
        currentMediaType.equals("stream", ignoreCase = true)

    fun playCurrentVideoWithSubtitle(subtitleUri: Uri? = null, resumePosition: Long = 0L) {
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(currentVideo.path)

        if (subtitleUri != null) {
            val subtitleConfiguration =
                MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage("en")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfiguration))
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.seekTo(resumePosition.coerceAtLeast(0L))
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    fun downloadExternalSubtitle() {
        if (!canDownloadExternalSubtitles) {
            Toast.makeText(context, "Subtitle download is only for Movies and TV Shows", Toast.LENGTH_SHORT).show()
            showSubtitleSettings = false
            autoSubtitleStatus = ""
            return
        }

        if (subtitleDownloadInProgress) {
            Toast.makeText(context, "Subtitle search already running", Toast.LENGTH_SHORT).show()
            return
        }

        showControls = true
        showSubtitleSettings = false
        autoSubtitleStatus = "Searching subtitles..."
        subtitleDownloadInProgress = true

        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)

            try {
                val subtitleUri =
                    OpenSubtitlesClient.downloadBestEnglishSubtitle(
                        context = context,
                        videoPath = currentVideo.path
                    )

                if (subtitleUri != null) {
                    autoSubtitleStatus = "Subtitle loaded"
                    Toast.makeText(context, "Subtitle loaded", Toast.LENGTH_SHORT).show()
                    subtitlesEnabled = true

                    trackSelector.parameters =
                        trackSelector.buildUponParameters()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .build()

                    playCurrentVideoWithSubtitle(
                        subtitleUri = subtitleUri,
                        resumePosition = resumeAt
                    )

                    delay(1400)
                    autoSubtitleStatus = ""
                } else {
                    autoSubtitleStatus = "No subtitle found"
                    Toast.makeText(context, "No subtitle found", Toast.LENGTH_SHORT).show()
                    delay(1400)
                    autoSubtitleStatus = ""
                }
            } catch (e: Exception) {
                autoSubtitleStatus = "Subtitle failed"
                Toast.makeText(context, "Subtitle failed", Toast.LENGTH_SHORT).show()
                delay(1400)
                autoSubtitleStatus = ""
            } finally {
                subtitleDownloadInProgress = false
            }
        }
    }

    LaunchedEffect(currentVideo.path) {
        val savedPosition =
            if (isStreamMedia) {
                0L
            } else {
                loadPlaybackPosition(context, currentVideo.path)
            }

        position = savedPosition
        duration = 1L
        showControls = true
        showTopBar = true
        showAudioSelector = false
        showSubtitleSettings = false
        pendingNextEpisode = null
        nextEpisodeCountdown = 0
        showNextEpisodeOverlay = false
        previewBitmap = null
        previewFrames = emptyList()

        if (!isStreamMedia) {
            recordWatchHistory(
                context = context,
                videoPath = currentVideo.path,
                title = cleanVideoTitle(currentVideo.path)
            )
        }

        playCurrentVideoWithSubtitle(resumePosition = savedPosition)

        if (!isStreamMedia && canDownloadExternalSubtitles && autoSubtitleAttemptedForPath != currentVideo.path) {
            autoSubtitleAttemptedForPath = currentVideo.path
            autoSubtitleStatus = ""

            scope.launch {
                delay(1200)

                if (subtitleDownloadInProgress) {
                    return@launch
                }

                subtitleDownloadInProgress = true
                val resumeAt = exoPlayer.currentPosition.coerceAtLeast(savedPosition)
                autoSubtitleStatus = "Searching subtitles..."

                try {
                    val subtitleUri =
                        OpenSubtitlesClient.downloadBestEnglishSubtitle(
                            context = context,
                            videoPath = currentVideo.path
                        )

                    if (subtitleUri != null) {
                        subtitlesEnabled = true
                        trackSelector.parameters =
                            trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()

                        autoSubtitleStatus = "Subtitle loaded"

                        playCurrentVideoWithSubtitle(
                            subtitleUri = subtitleUri,
                            resumePosition = resumeAt
                        )

                        delay(1400)
                        autoSubtitleStatus = ""
                    } else {
                        autoSubtitleStatus = "No subtitle found"
                        delay(1100)
                        autoSubtitleStatus = ""
                    }
                } catch (e: Exception) {
                    autoSubtitleStatus = "Subtitle failed"
                    delay(1100)
                    autoSubtitleStatus = ""
                } finally {
                    subtitleDownloadInProgress = false
                }
            }
        }
    }

    // FIX: Use WindowInsetsController for proper immersive mode — hides system nav bar completely
    LaunchedEffect(Unit) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer

        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = 1.0f
        }

        activity?.enterImmersiveMode()
    }

    DisposableEffect(exoPlayer, currentVideo.path, episodeList) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && episodeList.isNotEmpty()) {
                    val currentIndex = episodeList.indexOfFirst {
                        it.video.path == currentVideo.path
                    }

                    val nextItem = episodeList.getOrNull(currentIndex + 1)

                    if (nextItem != null) {
                        if (currentMediaType.equals("local", ignoreCase = true)) {
                            showNextEpisodeOverlay = false
                            pendingNextEpisode = null
                            currentMediaType = "local"
                            currentVideo = nextItem.video
                            onPlayNext(nextItem)
                        } else if (currentMediaType.equals("tv", ignoreCase = true)) {
                            pendingNextEpisode = nextItem
                            nextEpisodeCountdown = 5
                            showNextEpisodeOverlay = true
                            showControls = true
                            showTopBar = true
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!isStreamMedia) {
                savePlaybackPosition(
                    context = context,
                    videoPath = currentVideo.path,
                    position = exoPlayer.currentPosition.coerceAtLeast(0L)
                )
            }

            exoPlayer.release()

            if (CineVaultPlayerHolder.currentPlayer == exoPlayer) {
                CineVaultPlayerHolder.currentPlayer = null
            }

            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }

            // FIX: Restore system bars when leaving player
            activity?.exitImmersiveMode()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSeekbar) {
                position = exoPlayer.currentPosition.coerceAtLeast(0)
            }

            duration = exoPlayer.duration.coerceAtLeast(1)
            isPlaying = exoPlayer.isPlaying
            delay(350)
        }
    }

    LaunchedEffect(currentVideo.path, duration) {
        if (!isStreamMedia && duration > 1000L) {
            previewFrames = emptyList()

            val quickFrames = VideoThumbnailHelper.generatePreviewCache(
                context = context,
                videoPath = currentVideo.path,
                durationMs = duration,
                frameCount = 18
            )

            if (quickFrames.isNotEmpty()) {
                previewFrames = quickFrames
                previewBitmap = quickFrames.firstOrNull()?.bitmap ?: previewBitmap
            }

            val denseFrames = VideoThumbnailHelper.generatePreviewCache(
                context = context,
                videoPath = currentVideo.path,
                durationMs = duration,
                frameCount = 72
            )

            if (denseFrames.isNotEmpty()) {
                previewFrames = denseFrames
            }
        } else {
            previewFrames = emptyList()
            previewBitmap = null
        }
    }

    LaunchedEffect(showSeekPreview, previewPosition) {
        if (showSeekPreview) {
            isSeekPreviewLarge = false
            delay(650)
            if (showSeekPreview) {
                isSeekPreviewLarge = true
            }
        } else {
            isSeekPreviewLarge = false
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(currentVideo.path) {
        while (true) {
            delay(5000)
            val current = exoPlayer.currentPosition.coerceAtLeast(0L)
            val total = exoPlayer.duration.coerceAtLeast(1L)

            if (!isStreamMedia && current > 5000L && current < total - 5000L) {
                savePlaybackPosition(
                    context = context,
                    videoPath = currentVideo.path,
                    position = current
                )

                recordWatchHistory(
                    context = context,
                    videoPath = currentVideo.path,
                    title = cleanVideoTitle(currentVideo.path)
                )
            }
        }
    }

    LaunchedEffect(showControls, showAudioSelector, showSubtitleSettings, isDraggingSeekbar) {
        if (showControls && !showAudioSelector && !showSubtitleSettings && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !showAudioSelector && !showSubtitleSettings) {
                showControls = false
            }
        }
    }

    LaunchedEffect(showTopBar, showAudioSelector, showSubtitleSettings, isDraggingSeekbar) {
        if (showTopBar && !showAudioSelector && !showSubtitleSettings && !isDraggingSeekbar) {
            delay(2800)
            if (!isDraggingSeekbar && !showAudioSelector && !showSubtitleSettings) {
                showTopBar = false
            }
        }
    }

    LaunchedEffect(showAudioSelector, menuTouchKey) {
        if (showAudioSelector) {
            delay(9000)
            showAudioSelector = false
        }
    }

    LaunchedEffect(showSubtitleSettings, subtitleMenuTouchKey) {
        if (showSubtitleSettings) {
            delay(9000)
            showSubtitleSettings = false
        }
    }

    // FIX: Brightness HUD dismiss — watch isBrightnessDragging, not brightnessPercent value
    // Previously watched brightnessPercent which restarted the timer on every drag tick
    LaunchedEffect(isBrightnessDragging) {
        if (!isBrightnessDragging && showBrightnessCircle) {
            delay(1200)
            showBrightnessCircle = false
        }
    }

    // FIX: Volume HUD dismiss — same fix
    LaunchedEffect(isVolumeDragging) {
        if (!isVolumeDragging && showVolumeCircle) {
            delay(1200)
            showVolumeCircle = false
        }
    }

    LaunchedEffect(playerViewForSubtitleStyle, subtitleTextSizeSp, subtitleBottomPadding) {
        val subtitleView = playerViewForSubtitleStyle?.subtitleView

        subtitleView?.setUserDefaultStyle()
        subtitleView?.setApplyEmbeddedStyles(false)
        subtitleView?.setApplyEmbeddedFontSizes(false)
        subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
        subtitleView?.setBottomPaddingFraction(subtitleBottomPadding)

        subtitleView?.setStyle(
            CaptionStyleCompat(
                AndroidColor.WHITE,
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                AndroidColor.BLACK,
                null
            )
        )
    }

    LaunchedEffect(showNextEpisodeOverlay, pendingNextEpisode) {
        if (showNextEpisodeOverlay && pendingNextEpisode != null) {
            for (count in 5 downTo 1) {
                nextEpisodeCountdown = count
                delay(1000)
                if (!showNextEpisodeOverlay || pendingNextEpisode == null) {
                    return@LaunchedEffect
                }
            }

            val next = pendingNextEpisode
            if (next != null) {
                showNextEpisodeOverlay = false
                pendingNextEpisode = null
                currentMediaType = next.type
                currentVideo = next.video
                onPlayNext(next)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight
        val isSmallPhone = maxWidth < 430.dp || maxHeight < 760.dp
        val isCompactLandscape = isLandscape && maxHeight < 430.dp

        val scale = when {
            isCompactLandscape -> 0.70f
            isSmallPhone && !isLandscape -> 0.78f
            isSmallPhone -> 0.82f
            isLandscape -> 0.90f
            else -> 1f
        }

        val playButton = (98 * scale).dp
        val smallButton = (66 * scale).dp
        val hudSize = (72 * scale).dp
        val pillHeight = (54 * scale).dp
        val pillFont = (17 * scale).sp
        val sidePadding = if (isCompactLandscape) 8.dp else 16.dp

        val bottomDockPadding =
            when {
                isCompactLandscape -> 112.dp
                isLandscape -> 126.dp
                else -> 152.dp
            }

        val seekBottomPadding =
            when {
                isCompactLandscape -> 18.dp
                isLandscape -> 24.dp
                else -> 30.dp
            }

        val showIntroSkip =
            isCurrentTvShow &&
                    position in 5_000L..95_000L

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (isZoomMode)
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else
                        androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
                    playerViewForSubtitleStyle = this
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = if (isZoomMode)
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                playerViewForSubtitleStyle = playerView
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentVideo.path) {
                    detectTapGestures(
                        onTap = {
                            when {
                                showAudioSelector -> showAudioSelector = false
                                showSubtitleSettings -> showSubtitleSettings = false
                                else -> {
                                    val nextVisibility = !showControls
                                    showControls = nextVisibility
                                    showTopBar = nextVisibility
                                }
                            }
                        },
                        onDoubleTap = { offset: Offset ->
                            val width = size.width

                            if (offset.x < width * 0.45f) {
                                exoPlayer.seekTo(
                                    (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                )
                                position = exoPlayer.currentPosition
                                showControls = true
                                showTopBar = true
                            } else if (offset.x > width * 0.55f) {
                                exoPlayer.seekTo(
                                    (exoPlayer.currentPosition + 10000)
                                        .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                                )
                                position = exoPlayer.currentPosition
                                showControls = true
                                showTopBar = true
                            } else {
                                isZoomMode = !isZoomMode
                                showControls = true
                                showTopBar = true
                            }
                        }
                    )
                }
                .pointerInput(currentVideo.path) {
                    // FIX: Track drag start/end to correctly dismiss HUD icons
                    detectDragGestures(
                        onDragStart = { _ -> },
                        onDrag = { change, dragAmount ->
                            val x = change.position.x
                            val screenWidth = size.width

                            if (x < screenWidth * 0.50f) {
                                brightnessPercent =
                                    (brightnessPercent - dragAmount.y.toInt() / 8)
                                        .coerceIn(5, 100)

                                activity?.window?.attributes =
                                    activity?.window?.attributes?.apply {
                                        screenBrightness = brightnessPercent / 100f
                                    }

                                isBrightnessDragging = true
                                showBrightnessCircle = true
                            } else {
                                volumePercent =
                                    (volumePercent - dragAmount.y.toInt() / 8)
                                        .coerceIn(0, 150)

                                val maxVol =
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

                                val realVol =
                                    ((volumePercent.coerceAtMost(100) / 100f) * maxVol).toInt()

                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    realVol,
                                    0
                                )

                                isVolumeDragging = true
                                showVolumeCircle = true
                            }
                        },
                        onDragEnd = {
                            isBrightnessDragging = false
                            isVolumeDragging = false
                        },
                        onDragCancel = {
                            isBrightnessDragging = false
                            isVolumeDragging = false
                        }
                    )
                }
        )

        // FIX: Brightness HUD — no longer uses LaunchedEffect inside AnimatedVisibility
        // Dismissal is now controlled by isBrightnessDragging LaunchedEffect above
        AnimatedVisibility(
            visible = showBrightnessCircle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 86.dp, start = 28.dp)
        ) {
            VerticalBrightnessHud(
                value = brightnessPercent,
                size = hudSize
            )
        }

        // FIX: Volume HUD — same, no LaunchedEffect inside
        AnimatedVisibility(
            visible = showVolumeCircle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 86.dp, end = 28.dp)
        ) {
            val volumeColor = when {
                volumePercent > 120 -> Color.Red
                volumePercent > 90 -> Color(0xFFFF9800)
                else -> Color.White
            }

            FilledVolumeHud(
                value = volumePercent,
                maxValue = 150,
                color = volumeColor,
                size = hudSize
            )
        }

        if (showAudioSelector) {
            val audioTracks = exoPlayer.currentTracks.groups.filter {
                it.type == C.TRACK_TYPE_AUDIO
            }

            FloatingTrackPopup(
                title = "Audio",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = if (isCompactLandscape) 160.dp else if (isLandscape) 240.dp else 300.dp,
                        end = sidePadding
                    )
                    .width(
                        if (isCompactLandscape) 145.dp
                        else if (isLandscape) 170.dp
                        else 190.dp
                    ),
                rows = audioTracks.flatMap { group ->
                    List(group.length) { index ->
                        val format = group.getTrackFormat(index)
                        val language = friendlyLanguageName(format.language)
                        val audioTitle =
                            if (language == "Unknown" || language == "UND") {
                                "Default Audio"
                            } else {
                                language
                            }

                        TrackPopupRowData(
                            title = audioTitle,
                            subtitle = "Track ${index + 1}",
                            onClick = {
                                val override = TrackSelectionOverride(
                                    group.mediaTrackGroup,
                                    listOf(index)
                                )

                                trackSelector.parameters =
                                    trackSelector.buildUponParameters()
                                        .setOverrideForType(override)
                                        .build()

                                showAudioSelector = false
                                showControls = true
                            }
                        )
                    }
                },
                onAnyClick = {
                    menuTouchKey++
                },
                onClose = {
                    showAudioSelector = false
                    showControls = true
                }
            )
        }

        val hasInternalSubtitles =
            exoPlayer.currentTracks.groups.any {
                it.type == C.TRACK_TYPE_TEXT && it.length > 0
            }

        SubtitleSettingsMenu(
            isVisible = showSubtitleSettings,
            subtitlesEnabled = subtitlesEnabled,
            hasInternalSubtitles = hasInternalSubtitles,
            onInternalClick = {
                subtitlesEnabled = true
                trackSelector.parameters =
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                showSubtitleSettings = false
                showControls = true
                subtitleMenuTouchKey++
            },
            onDownloadClick = {
                subtitleMenuTouchKey++
                downloadExternalSubtitle()
            },
            onToggleSubtitles = {
                subtitlesEnabled = !subtitlesEnabled

                trackSelector.parameters =
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                        .build()

                showControls = true
                subtitleMenuTouchKey++
            },
            onDismiss = {
                showSubtitleSettings = false
                showControls = true
            },
            currentFontSize = subtitleTextSizeSp,
            onFontSizeChange = {
                subtitleTextSizeSp = it
                showControls = true
                subtitleMenuTouchKey++
            },
            currentVerticalPosition = subtitleBottomPadding,
            onVerticalPositionChange = {
                subtitleBottomPadding = it
                showControls = true
                subtitleMenuTouchKey++
            },
            currentSyncOffset = subtitleSyncOffset,
            onSyncOffsetChange = {
                subtitleSyncOffset = it
                showControls = true
                subtitleMenuTouchKey++
            },
            onReset = {
                subtitleTextSizeSp = 18f
                subtitleBottomPadding = 0.13f
                subtitleSyncOffset = 0.0f
                subtitlesEnabled = true

                trackSelector.parameters =
                    trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()

                showControls = true
                subtitleMenuTouchKey++
            },
            onUserInteraction = {
                subtitleMenuTouchKey++
                showControls = true
            }
        )

        AnimatedVisibility(
            visible = showControls || isDraggingSeekbar || showAudioSelector || showSubtitleSettings,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                // FIX: TopBar now only shows title + 3-dot menu, no back button
                // Back button has moved to the bottom control row
                AnimatedVisibility(
                    visible = (showTopBar || showAudioSelector || showSubtitleSettings) && !showSeekPreview,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopGlassTitleBar(
                        title = if (isStreamMedia) currentVideo.name else cleanVideoTitle(currentVideo.path),
                        isLandscape = isLandscape
                    )
                }

                AnimatedVisibility(
                    visible = autoSubtitleStatus.isNotBlank() && !showSeekPreview,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isLandscape) 54.dp else 86.dp)
                ) {
                    Text(
                        text = autoSubtitleStatus,
                        color = Color(0xFFFFD36A),
                        fontSize = if (isLandscape) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showNextEpisodeOverlay && pendingNextEpisode != null && !showSeekPreview,
                    enter = fadeIn(animationSpec = tween(140)),
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    NextEpisodeCountdownOverlay(
                        nextEpisode = pendingNextEpisode,
                        countdown = nextEpisodeCountdown,
                        isLandscape = isLandscape,
                        onPlayNow = {
                            val next = pendingNextEpisode
                            if (next != null) {
                                showNextEpisodeOverlay = false
                                pendingNextEpisode = null
                                currentMediaType = next.type
                                currentVideo = next.video
                                onPlayNext(next)
                            }
                        },
                        onCancel = {
                            showNextEpisodeOverlay = false
                            pendingNextEpisode = null
                            nextEpisodeCountdown = 0
                            showControls = true
                        }
                    )
                }

                AnimatedVisibility(
                    visible = showIntroSkip && !showSeekPreview && !isDraggingSeekbar,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = sidePadding)
                ) {
                    SkipIntroButton(
                        isLandscape = isLandscape,
                        onClick = {
                            val target = 95_000L.coerceAtMost(duration.coerceAtLeast(1L))
                            exoPlayer.seekTo(target)
                            position = target
                            showControls = true
                        }
                    )
                }

                // Zoom mode indicator
                AnimatedVisibility(
                    visible = isZoomMode && !showSeekPreview,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(120)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isLandscape) 54.dp else 90.dp)
                ) {
                    Text(
                        text = "⛶  Fill",
                        color = Color(0xFFFFD36A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // FIX: Bottom control row now includes back button as first item
                AnimatedVisibility(
                    visible = !showSeekPreview && !isDraggingSeekbar,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(
                                bottom = bottomDockPadding,
                                start = sidePadding,
                                end = sidePadding
                            )
                            .clip(RoundedCornerShape(42.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.16f),
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Black.copy(alpha = 0.32f)
                                    )
                                )
                            )
                            .padding(horizontal = (12 * scale).dp, vertical = (6 * scale).dp),
                        horizontalArrangement = Arrangement.spacedBy((7 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // FIX: Back button moved here from top bar — easy thumb reach
                        BackCircle(size = smallButton, onClick = onBack)

                        ControlCircle("◀◀", smallButton) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            position = exoPlayer.currentPosition
                            showControls = true
                        }

                        ControlCircle(
                            text = if (isPlaying) "Ⅱ" else "▶",
                            size = playButton
                        ) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        }

                        ControlCircle("▶▶", smallButton) {
                            exoPlayer.seekTo(
                                (exoPlayer.currentPosition + 10000)
                                    .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                            )
                            position = exoPlayer.currentPosition
                            showControls = true
                        }

                        Spacer(modifier = Modifier.width((8 * scale).dp))

                        PlayerPill("≡", pillHeight, pillFont) {
                            showSubtitleSettings = false
                            showAudioSelector = !showAudioSelector
                            showControls = true
                            menuTouchKey++
                        }

                        if (!isStreamMedia) {
                            PlayerPill("CC", pillHeight, pillFont) {
                                showAudioSelector = false
                                showSubtitleSettings = !showSubtitleSettings
                                showControls = true
                                menuTouchKey++
                            }
                        }

                        PlayerPill("⛶", pillHeight, pillFont) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()

                                activity?.enterPictureInPictureMode(params)
                            }
                        }
                    }
                }

                SeekPreviewBubble(
                    isVisible = showSeekPreview,
                    bitmap = previewBitmap,
                    timeText = formatTime(previewPosition),
                    isLandscape = isLandscape,
                    isLarge = isSeekPreviewLarge,
                    progress = (previewPosition.toFloat() / duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = sidePadding, end = sidePadding, bottom = seekBottomPadding)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Black.copy(alpha = 0.35f)
                                )
                            )
                        )
                        .padding(horizontal = (14 * scale).dp, vertical = (7 * scale).dp)
                ) {
                    CinematicSeekBar(
                        position = position,
                        duration = duration,
                        isDragging = isDraggingSeekbar,
                        onPreviewPositionChanged = { newPosition ->
                            isDraggingSeekbar = true
                            showSeekPreview = true
                            showControls = true
                            showTopBar = true
                            position = newPosition.coerceIn(0L, duration)
                            previewPosition = position
                            val nearestFrame = VideoThumbnailHelper.nearestPreviewFrame(
                                previewFrames,
                                previewPosition
                            )

                            if (nearestFrame != null) {
                                previewBitmap = nearestFrame
                            }
                        },
                        onSeekFinished = { finalPosition ->
                            val safePosition = finalPosition.coerceIn(0L, duration)
                            position = safePosition
                            previewPosition = safePosition
                            exoPlayer.seekTo(safePosition)
                            isDraggingSeekbar = false

                            previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(
                                previewFrames,
                                safePosition
                            ) ?: previewBitmap

                            showSeekPreview = true

                            if (isStreamMedia) {
                                scope.launch {
                                    delay(360)
                                    if (!isDraggingSeekbar) {
                                        showSeekPreview = false
                                    }
                                }
                            } else {
                                scope.launch {
                                    val exactBitmap = VideoThumbnailHelper.generateFrameAtTime(
                                        context = context,
                                        videoPath = currentVideo.path,
                                        positionMs = safePosition
                                    )

                                    if (exactBitmap != null && previewPosition == safePosition) {
                                        previewBitmap = exactBitmap
                                    }

                                    delay(620)
                                    if (previewPosition == safePosition && !isDraggingSeekbar) {
                                        showSeekPreview = false
                                    }
                                }
                            }

                            showControls = true
                            showTopBar = true
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimePill(formatTime(position))
                        TimePill(formatTime(duration))
                    }
                }
            }
        }
    }
}

@Composable
private fun CinematicSeekBar(
    position: Long,
    duration: Long,
    isDragging: Boolean,
    onPreviewPositionChanged: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit
) {
    var localPosition by remember { mutableLongStateOf(position) }

    LaunchedEffect(position, isDragging) {
        if (!isDragging) {
            localPosition = position
        }
    }

    val glow by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.45f,
        animationSpec = tween(220),
        label = "seekGlow"
    )

    fun positionFromX(x: Float, width: Float): Long {
        if (duration <= 0L || width <= 0f) return 0L
        val progress = (x / width).coerceIn(0f, 1f)
        return (duration * progress).toLong().coerceIn(0L, duration)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val newPosition = positionFromX(offset.x, size.width.toFloat())
                    localPosition = newPosition
                    onPreviewPositionChanged(newPosition)
                    onSeekFinished(newPosition)
                }
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val newPosition = positionFromX(offset.x, size.width.toFloat())
                        localPosition = newPosition
                        onPreviewPositionChanged(newPosition)
                    },
                    onDrag = { change, _ ->
                        val newPosition = positionFromX(change.position.x, size.width.toFloat())
                        localPosition = newPosition
                        onPreviewPositionChanged(newPosition)
                    },
                    onDragEnd = {
                        onSeekFinished(localPosition)
                    },
                    onDragCancel = {
                        onSeekFinished(localPosition)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 5.dp.toPx()
            val centerY = size.height / 2f
            val radius = trackHeight / 2f
            val safeDuration = duration.coerceAtLeast(1L)
            val progress = (localPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
            val activeWidth = size.width * progress
            val thumbX = activeWidth.coerceIn(0f, size.width)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.13f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )

            drawRoundRect(
                color = Color(0xFFFFC857).copy(alpha = 0.95f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(activeWidth, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )

            listOf(0.25f, 0.50f, 0.75f).forEach { marker ->
                val markerX = size.width * marker
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = 2.2.dp.toPx(),
                    center = Offset(markerX, centerY)
                )
            }

            drawCircle(
                color = Color(0xFFFFC857).copy(alpha = 0.22f * glow),
                radius = 16.dp.toPx(),
                center = Offset(thumbX, centerY)
            )

            drawCircle(
                color = Color(0xFFFFE7A3).copy(alpha = 0.45f * glow),
                radius = 10.dp.toPx(),
                center = Offset(thumbX, centerY)
            )

            drawCircle(
                color = Color(0xFFFFF3D6),
                radius = if (isDragging) 6.6.dp.toPx() else 5.2.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
        }
    }
}

@Composable
private fun SeekPreviewBubble(
    isVisible: Boolean,
    bitmap: Bitmap?,
    timeText: String,
    isLandscape: Boolean,
    isLarge: Boolean,
    progress: Float
) {
    val previewWidth by animateDpAsState(
        targetValue = if (isLarge) {
            if (isLandscape) 220.dp else 210.dp
        } else {
            if (isLandscape) 150.dp else 160.dp
        },
        animationSpec = tween(160),
        label = "previewWidth"
    )

    val previewHeight by animateDpAsState(
        targetValue = if (isLarge) {
            if (isLandscape) 124.dp else 118.dp
        } else {
            if (isLandscape) 84.dp else 90.dp
        },
        animationSpec = tween(160),
        label = "previewHeight"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(80)),
        exit = fadeOut(animationSpec = tween(80)),
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val horizontalPadding = 18.dp
            val availableWidth = maxWidth - (horizontalPadding * 2)
            val targetCenter = availableWidth * progress.coerceIn(0f, 1f)
            val rawOffset = targetCenter - (previewWidth / 2)
            val maxOffset = availableWidth - previewWidth

            val safeOffset =
                when {
                    maxOffset < 0.dp -> 0.dp
                    rawOffset < 0.dp -> 0.dp
                    rawOffset > maxOffset -> maxOffset
                    else -> rawOffset
                }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = horizontalPadding + safeOffset,
                        y = 0.dp
                    )
                    .padding(bottom = if (isLandscape) 116.dp else 134.dp)
                    .graphicsLayer {
                        val animatedScale = if (isLarge) 1.02f else 0.98f
                        scaleX = animatedScale
                        scaleY = animatedScale
                        shadowElevation = if (isLarge) 18f else 10f
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.58f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFFFD36A).copy(alpha = if (isLarge) 0.55f else 0.25f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Seek preview",
                        modifier = Modifier
                            .width(previewWidth)
                            .height(previewHeight)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(previewWidth)
                            .height(previewHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timeText,
                            color = Color(0xFFFFE7A3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = timeText,
                    color = Color(0xFFFFE7A3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// FIX: TopGlassTitleBar no longer has back button — just title + 3-dot menu
@Composable
private fun TopGlassTitleBar(
    title: String,
    isLandscape: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isLandscape) 8.dp else 12.dp,
                end = if (isLandscape) 8.dp else 12.dp,
                top = if (isLandscape) 0.dp else 4.dp
            )
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.30f)
                    )
                )
            )
            .padding(
                horizontal = if (isLandscape) 10.dp else 13.dp,
                vertical = if (isLandscape) 3.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = if (isLandscape) 13.sp else 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "⋮",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = if (isLandscape) 24.sp else 26.sp,
            fontWeight = FontWeight.Black
        )
    }
}

// FIX: New back button as a circle control matching the existing skip buttons
@Composable
private fun BackCircle(
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD36A).copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.07f),
                        Color.Black.copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFFD36A).copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color(0xFFFFD36A),
            modifier = Modifier.size((size.value * 0.46f).dp)
        )
    }
}

@Composable
private fun TimePill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

// FIX: Brightness HUD — replaced emoji ☀ with Material Icon, styled to match design
@Composable
private fun VerticalBrightnessHud(
    value: Int,
    size: androidx.compose.ui.unit.Dp
) {
    val fill = (value.toFloat() / 100f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color(0xFFFFD36A).copy(alpha = 0.30f), RoundedCornerShape(26.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.BrightnessHigh,
            contentDescription = "Brightness",
            tint = Color(0xFFFFD36A),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(9.dp))

        Box(
            modifier = Modifier
                .width(9.dp)
                .height(size)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fill)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFF3D6), Color(0xFFFFC857))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(9.dp))

        Text(text = "$value%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// FIX: Volume HUD — replaced emoji 🔊 with Material Icon, icon changes based on volume level
@Composable
private fun FilledVolumeHud(
    value: Int,
    maxValue: Int,
    color: Color,
    size: androidx.compose.ui.unit.Dp
) {
    val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

    val volumeIcon = when {
        value == 0 -> Icons.Rounded.VolumeMute
        value < 50 -> Icons.Rounded.VolumeDown
        else -> Icons.Rounded.VolumeUp
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = volumeIcon,
            contentDescription = "Volume",
            tint = color,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(9.dp))

        Box(
            modifier = Modifier
                .width(9.dp)
                .height(size)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fill)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.9f), color)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(9.dp))

        Text(text = "$value%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ControlCircle(
    text: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val isPlayPause = text == "▶" || text == "Ⅱ"
    val shape = if (isPlayPause) CircleShape else RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isPlayPause) {
                        listOf(Color.Black.copy(alpha = 0.32f), Color.Black.copy(alpha = 0.32f))
                    } else {
                        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.07f), Color.Black.copy(alpha = 0.20f))
                    }
                )
            )
            .border(
                width = if (isPlayPause) 1.7.dp else 1.dp,
                color = if (isPlayPause) Color(0xFFFFC857).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.18f),
                shape = shape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isPlayPause) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val outer = size.toPx() * 0.48f
                val ringWidth = size.toPx() * 0.045f

                drawCircle(
                    color = Color(0xFFFF8A00).copy(alpha = 0.26f),
                    radius = outer,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
                )

                drawCircle(
                    color = Color(0xFFFFD66B).copy(alpha = 0.18f),
                    radius = outer * 0.82f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth * 0.65f)
                )
            }
        }

        Text(
            text = text,
            color = if (isPlayPause) Color(0xFFFF7A2F) else Color.White,
            fontSize = when {
                isPlayPause && text == "▶" -> (size.value * 0.48f).sp
                isPlayPause -> (size.value * 0.46f).sp
                else -> (size.value * 0.42f).sp
            },
            fontWeight = FontWeight.Black,
            modifier = Modifier.offset(x = if (text == "▶") 2.dp else 0.dp, y = 0.dp)
        )
    }
}

@Composable
private fun PlayerPill(
    text: String,
    height: androidx.compose.ui.unit.Dp,
    fontSize: TextUnit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(height)
            .widthIn(min = height * 1.45f)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.17f),
                        Color.White.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.18f)
                    )
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text == "CC") fontSize else (fontSize.value + 1).sp,
            fontWeight = FontWeight.Black
        )
    }
}

private data class TrackPopupRowData(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

private fun friendlyLanguageName(code: String?): String {
    return when (code?.lowercase()) {
        null, "", "und" -> "Unknown"
        "en", "eng" -> "English"
        "it", "ita" -> "Italian"
        "ja", "jpn" -> "Japanese"
        "hi", "hin" -> "Hindi"
        "fr", "fre", "fra" -> "French"
        "es", "spa" -> "Spanish"
        "ko", "kor" -> "Korean"
        "de", "ger", "deu" -> "German"
        "pt", "por" -> "Portuguese"
        "zh", "chi", "zho" -> "Chinese"
        "ar", "ara" -> "Arabic"
        "ru", "rus" -> "Russian"
        else -> code?.uppercase() ?: "Unknown"
    }
}

private fun cleanVideoTitle(path: String): String {
    var title = path
        .substringAfterLast("/")
        .substringAfterLast("\\")
        .substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ")
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(".", " ")
        .replace("_", " ")
        .replace("-", " ")

    title = title.replace(
        Regex(
            "\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby\\s*vision|dolby|vision|imax|remux|bluray|blu\\s*ray|brrip|hdrip|webrip|web\\s*dl|webdl|web|nf|amzn|dsnp|hulu|itunes|x264|x265|h264|h265|hevc|10bit|8bit|aac5?|aac|ddp5?\\.?1?|dd\\+|dts|truehd|atmos|5\\s*1|7\\s*1|yts|rarbg|tgx|eztv|pir8|ag|proper|repack|extended|theatrical|directors?\\s*cut|multi|dual|audio|english|hindi|ita|eng|mkv|mp4|avi|subs?|esub)\\b",
            RegexOption.IGNORE_CASE
        ),
        " "
    )

    title = title.replace(Regex("\\bS\\d{1,2}E\\d{1,2}\\b", RegexOption.IGNORE_CASE), " ")
    title = title.replace(Regex("\\bseason\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    title = title.replace(Regex("\\bepisode\\s*\\d+\\b", RegexOption.IGNORE_CASE), " ")
    title = title.replace(Regex("\\b(19|20)\\d{2}\\b.*$", RegexOption.IGNORE_CASE), " ")

    return title.replace(Regex("\\s+"), " ").trim().ifBlank { "Now Playing" }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun cleanEpisodeDisplayName(fileName: String): String {
    val seasonEpisode = Regex("s(\\d{1,2})e(\\d{1,2})", RegexOption.IGNORE_CASE).find(fileName)
    val prefix = if (seasonEpisode != null) {
        val s = seasonEpisode.groupValues[1].padStart(2, '0')
        val e = seasonEpisode.groupValues[2].padStart(2, '0')
        "S${s}E${e}"
    } else {
        "Episode"
    }

    var name = fileName
        .substringAfterLast("/")
        .substringAfterLast("\\")
        .substringBeforeLast(".")
        .replace(Regex("\\[.*?]"), " ")
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(".", " ")
        .replace("_", " ")
        .replace("-", " ")

    name = name.replace(Regex("s\\d{1,2}e\\d{1,2}", RegexOption.IGNORE_CASE), " ")
    name = name.replace(
        Regex(
            "\\b(2160p|1080p|720p|480p|4k|uhd|hdr10\\+?|hdr|dv|dolby|vision|bluray|brrip|webrip|webdl|web|x264|x265|h264|h265|hevc|10bit|aac|ddp|dts|atmos|mkv|mp4|avi|rarbg|yts|eztv|tgx|nf|amzn)\\b",
            RegexOption.IGNORE_CASE
        ),
        " "
    )

    name = name.replace(Regex("\\s+"), " ").trim()

    return if (name.isBlank()) prefix else "$prefix • $name"
}

@Composable
private fun SkipIntroButton(
    isLandscape: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = "SKIP INTRO",
        color = Color.Black,
        fontSize = if (isLandscape) 11.sp else 12.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFFD36A), Color(0xFFFFA000))
                )
            )
            .clickable { onClick() }
            .padding(
                horizontal = if (isLandscape) 13.dp else 15.dp,
                vertical = if (isLandscape) 7.dp else 8.dp
            )
    )
}

@Composable
private fun NextEpisodeCountdownOverlay(
    nextEpisode: VideoWithMetadata?,
    countdown: Int,
    isLandscape: Boolean,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit
) {
    if (nextEpisode == null) return

    Column(
        modifier = Modifier
            .width(if (isLandscape) 310.dp else 300.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.50f))
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFFFFD36A).copy(alpha = 0.38f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Next episode starts in",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = if (isLandscape) 13.sp else 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = countdown.coerceAtLeast(1).toString(),
            color = Color(0xFFFFD36A),
            fontSize = if (isLandscape) 38.sp else 42.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = nextEpisode.subtitle.ifBlank { cleanEpisodeDisplayName(nextEpisode.video.name) },
            color = Color.White,
            fontSize = if (isLandscape) 13.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onCancel() }
                    .padding(horizontal = 15.dp, vertical = 8.dp)
            )

            Text(
                text = "Play Now",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFD36A))
                    .clickable { onPlayNow() }
                    .padding(horizontal = 15.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun FloatingTrackPopup(
    title: String,
    modifier: Modifier,
    rows: List<TrackPopupRowData>,
    onAnyClick: () -> Unit = {},
    onClose: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.52f))
            .padding(7.dp)
    ) {
        Text(text = title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(5.dp))

        rows.forEach { row ->
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable {
                        onAnyClick()
                        row.onClick()
                    }
                    .padding(7.dp)
            )

            Spacer(modifier = Modifier.height(5.dp))
        }

        Text(
            text = "Close",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable {
                    onAnyClick()
                    onClose()
                }
                .padding(7.dp)
        )
    }
}
