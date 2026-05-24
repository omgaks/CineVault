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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoFile,
    episodeList: List<VideoWithMetadata>,
    onBack: () -> Unit,
    onPlayNext: (VideoWithMetadata) -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()

    var currentVideo by remember { mutableStateOf(video) }
    var showControls by remember { mutableStateOf(true) }
    var showTopBar by remember { mutableStateOf(true) }
    var isDraggingSeekbar by remember { mutableStateOf(false) }

    var volumePercent by remember { mutableIntStateOf(70) }
    var brightnessPercent by remember { mutableIntStateOf(90) }

    var showVolumeCircle by remember { mutableStateOf(false) }
    var showBrightnessCircle by remember { mutableStateOf(false) }

    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }

    var subtitleTextSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleBottomPadding by remember { mutableFloatStateOf(0.08f) }
    var subtitleSyncOffset by remember { mutableFloatStateOf(0.0f) }
    var subtitleMenuTouchKey by remember { mutableIntStateOf(0) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var menuTouchKey by remember { mutableIntStateOf(0) }
    var playerViewForSubtitleStyle by remember { mutableStateOf<PlayerView?>(null) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(true) }

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
        showControls = true
        showSubtitleSettings = false

        Toast.makeText(context, "Searching subtitles...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)

            val subtitleUri = OpenSubtitlesClient.downloadBestEnglishSubtitle(
                context = context,
                videoPath = currentVideo.path
            )

            if (subtitleUri != null) {
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
            } else {
                Toast.makeText(context, "No subtitle found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(currentVideo.path) {
        val savedPosition = loadPlaybackPosition(context, currentVideo.path)

        position = savedPosition
        duration = 1L
        showControls = true
        showTopBar = true
        showAudioSelector = false
        showSubtitleSettings = false
        previewBitmap = null
        previewFrames = emptyList()

        playCurrentVideoWithSubtitle(resumePosition = savedPosition)
    }

    LaunchedEffect(Unit) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer

        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = 1.0f
        }
    }

    DisposableEffect(exoPlayer, currentVideo.path, episodeList) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && episodeList.isNotEmpty()) {
                    val currentIndex = episodeList.indexOfFirst {
                        it.video.path == currentVideo.path
                    }
                    val nextEpisode = episodeList.getOrNull(currentIndex + 1)

                    if (nextEpisode != null) {
                        currentVideo = nextEpisode.video
                        onPlayNext(nextEpisode)
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
            savePlaybackPosition(
                context = context,
                videoPath = currentVideo.path,
                position = exoPlayer.currentPosition.coerceAtLeast(0L)
            )

            exoPlayer.release()

            if (CineVaultPlayerHolder.currentPlayer == exoPlayer) {
                CineVaultPlayerHolder.currentPlayer = null
            }

            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
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
        if (duration > 1000L) {
            previewFrames = emptyList()

            // Fast warm-up cache first, so dragging never shows a blank preview.
            val quickFrames = VideoThumbnailHelper.generatePreviewCache(
                videoPath = currentVideo.path,
                durationMs = duration,
                frameCount = 36
            )

            if (quickFrames.isNotEmpty()) {
                previewFrames = quickFrames
                previewBitmap = quickFrames.firstOrNull()?.bitmap ?: previewBitmap
            }

            // Dense cache loads after the quick cache for better sync while scrubbing.
            val denseFrames = VideoThumbnailHelper.generatePreviewCache(
                videoPath = currentVideo.path,
                durationMs = duration,
                frameCount = 180
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

            if (current > 5000L && current < total - 5000L) {
                savePlaybackPosition(
                    context = context,
                    videoPath = currentVideo.path,
                    position = current
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isLandscape = maxWidth > maxHeight

        val playButton = if (isLandscape) 62.dp else 70.dp
        val smallButton = if (isLandscape) 40.dp else 46.dp
        val hudSize = if (isLandscape) 65.dp else 73.dp
        val pillHeight = if (isLandscape) 38.dp else 40.dp
        val pillFont = if (isLandscape) 10.sp else 11.sp
        val popupWidth = if (isLandscape) 300.dp else 280.dp

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    subtitleView?.setViewType(SubtitleView.VIEW_TYPE_CANVAS)
                    playerViewForSubtitleStyle = this
                }
            },
            update = {
                it.player = exoPlayer
                playerViewForSubtitleStyle = it
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
                            }
                        }
                    )
                }
                .pointerInput(currentVideo.path) {
                    detectDragGestures(
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

                                showVolumeCircle = true
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = showBrightnessCircle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 86.dp, start = 28.dp)
        ) {
            LaunchedEffect(brightnessPercent) {
                delay(1000)
                showBrightnessCircle = false
            }

            VerticalBrightnessHud(
                value = brightnessPercent,
                size = hudSize
            )
        }

        AnimatedVisibility(
            visible = showVolumeCircle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 86.dp, end = 28.dp)
        ) {
            LaunchedEffect(volumePercent) {
                delay(1000)
                showVolumeCircle = false
            }

            val volumeColor = when {
                volumePercent > 120 -> Color.Red
                volumePercent > 90 -> Color(0xFFFF9800)
                else -> Color.White
            }

            FilledCircleHud(
                value = volumePercent,
                maxValue = 150,
                color = volumeColor,
                icon = "🔊",
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
                        bottom = if (isLandscape) 265.dp else 320.dp,
                        end = 22.dp
                    )
                    .width(if (isLandscape) 260.dp else 270.dp),
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
                subtitleBottomPadding = 0.08f
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

                AnimatedVisibility(
                    visible = (showTopBar || showAudioSelector || showSubtitleSettings) && !showSeekPreview,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TopGlassTitleBar(
                        title = cleanVideoTitle(currentVideo.path),
                        isLandscape = isLandscape,
                        onBack = onBack
                    )
                }

                AnimatedVisibility(
                    visible = !showSeekPreview && !isDraggingSeekbar,
                    enter = fadeIn(animationSpec = tween(120)),
                    exit = fadeOut(animationSpec = tween(90)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(
                                bottom = if (isLandscape) 118.dp else 140.dp,
                                start = 18.dp,
                                end = 18.dp
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
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 14.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlCircle("◀◀", smallButton + 8.dp) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                            position = exoPlayer.currentPosition
                            showControls = true
                        }

                        ControlCircle(
                            text = if (isPlaying) "Ⅱ" else "▶",
                            size = playButton + 12.dp
                        ) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            showControls = true
                        }

                        ControlCircle("▶▶", smallButton + 8.dp) {
                            exoPlayer.seekTo(
                                (exoPlayer.currentPosition + 10000)
                                    .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                            )
                            position = exoPlayer.currentPosition
                            showControls = true
                        }

                        Spacer(modifier = Modifier.width(if (isLandscape) 18.dp else 8.dp))

                        PlayerPill("≡", pillHeight, pillFont) {
                            showSubtitleSettings = false
                            showAudioSelector = !showAudioSelector
                            showControls = true
                            menuTouchKey++
                        }

                        PlayerPill("CC", pillHeight, pillFont) {
                            showAudioSelector = false
                            showSubtitleSettings = !showSubtitleSettings
                            showControls = true
                            menuTouchKey++
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
                        .padding(start = 18.dp, end = 18.dp, bottom = 28.dp)
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
                        .padding(horizontal = 18.dp, vertical = 8.dp)
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

                            // Keep preview visible after release. First show nearest cached frame instantly,
                            // then replace it with the exact release frame once it is generated.
                            previewBitmap = VideoThumbnailHelper.nearestPreviewFrame(
                                previewFrames,
                                safePosition
                            ) ?: previewBitmap

                            showSeekPreview = true

                            scope.launch {
                                val exactBitmap = VideoThumbnailHelper.generateFrameAtTime(
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

            val safeOffset = when {
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
                    .padding(bottom = if (isLandscape) 110.dp else 128.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.52f)
                            )
                        )
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


@Composable
private fun TopGlassTitleBar(
    title: String,
    isLandscape: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (isLandscape) 14.dp else 18.dp
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
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "← Back",
            color = Color.White,
            fontSize = if (isLandscape) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.11f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures { onBack() }
                }
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = if (isLandscape) 15.sp else 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "⋮",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
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

@Composable
private fun VerticalBrightnessHud(
    value: Int,
    size: androidx.compose.ui.unit.Dp
) {
    val fill = (value.toFloat() / 100f).coerceIn(0f, 1f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "☀",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
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
                            colors = listOf(
                                Color(0xFFFFF3D6),
                                Color(0xFFFFC857)
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.width(9.dp))

        Text(
            text = "$value%",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilledCircleHud(
    value: Int,
    maxValue: Int,
    color: Color,
    icon: String,
    size: androidx.compose.ui.unit.Dp
) {
    val fill = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fill)
                .align(Alignment.BottomCenter)
                .background(color.copy(alpha = 0.70f))
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = icon,
                color = if (value > 60) Color.Black else Color.White,
                fontSize = 18.sp
            )

            Text(
                text = "$value%",
                color = if (value > 60) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
                if (isPlayPause) {
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFA726).copy(alpha = 0.22f),
                            Color(0xFFFF5A36).copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.18f)
                        )
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.09f),
                            Color.Black.copy(alpha = 0.18f)
                        )
                    )
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isPlayPause) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color(0xFFFF8A00).copy(alpha = 0.30f),
                    radius = size.toPx() * 0.49f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFC857).copy(alpha = 0.18f),
                    radius = size.toPx() * 0.34f,
                    center = center
                )
            }
        }

        Text(
            text = text,
            color = if (isPlayPause) Color(0xFFFF5A36) else Color.White,
            fontSize = when {
                isPlayPause && text == "▶" -> 36.sp
                isPlayPause -> 34.sp
                else -> 24.sp
            },
            fontWeight = FontWeight.Black,
            modifier = Modifier.offset(
                x = if (text == "▶") 2.dp else 0.dp,
                y = 0.dp
            )
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
            .widthIn(min = 58.dp)
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
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text == "CC") 18.sp else 19.sp,
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
        .replace(".", " ")
        .replace("_", " ")
        .replace("-", " ")
        .replace(Regex("\\[.*?]"), "")
        .replace(Regex("\\(.*?\\)"), "")

    title = title.replace(
        Regex(
            "\\b(1080p|720p|2160p|4k|imax|aac5|aac|ddp|5 1|7 1|x264|x265|h264|h265|bluray|brrip|hdrip|webrip|web|dts|yts|rarbg|mkv|mp4|avi|bz|proper|repack|extended|remux|multi|dual|audio|english|hindi|ita|eng|amzn|pir8|yts|ag)\\b",
            RegexOption.IGNORE_CASE
        ),
        ""
    )

    title = title.replace(
        Regex("\\b(19|20)\\d{2}\\b.*$", RegexOption.IGNORE_CASE),
        ""
    )

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
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        rows.forEach { row ->
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable {
                        onAnyClick()
                        row.onClick()
                    }
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "Close",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable {
                    onAnyClick()
                    onClose()
                }
                .padding(12.dp)
        )
    }
}