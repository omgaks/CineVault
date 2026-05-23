package com.sole.cinevault

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
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
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
    var isDraggingSeekbar by remember { mutableStateOf(false) }

    var volumePercent by remember { mutableIntStateOf(70) }
    var brightnessPercent by remember { mutableIntStateOf(90) }

    var showVolumeCircle by remember { mutableStateOf(false) }
    var showBrightnessCircle by remember { mutableStateOf(false) }

    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var subtitleStatus by remember { mutableStateOf("Download") }
    var subtitleTextSizeSp by remember { mutableFloatStateOf(18f) }
    var subtitleBottomPadding by remember { mutableFloatStateOf(0.08f) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var menuTouchKey by remember { mutableIntStateOf(0) }
    var playerViewForSubtitleStyle by remember { mutableStateOf<PlayerView?>(null) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(true) }

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

    LaunchedEffect(currentVideo.path) {
        val savedPosition = loadPlaybackPosition(context, currentVideo.path)

        position = savedPosition
        duration = 1L
        showControls = true
        showAudioSelector = false
        showSubtitleSelector = false

        subtitleStatus = "Download"
        playCurrentVideoWithSubtitle(resumePosition = savedPosition)
    }

    LaunchedEffect(Unit) {
        CineVaultPlayerHolder.currentPlayer = exoPlayer

        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
            delay(250)
        }
    }

    LaunchedEffect(currentVideo.path) {
        while (true) {
            delay(3000)
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

    LaunchedEffect(showControls, showAudioSelector, showSubtitleSelector, isDraggingSeekbar) {
        if (showControls && !showAudioSelector && !showSubtitleSelector && !isDraggingSeekbar) {
            delay(4500)
            if (!isDraggingSeekbar && !showAudioSelector && !showSubtitleSelector) {
                showControls = false
            }
        }
    }

    LaunchedEffect(showAudioSelector, showSubtitleSelector, menuTouchKey) {
        if (showAudioSelector || showSubtitleSelector) {
            delay(9000)
            showAudioSelector = false
            showSubtitleSelector = false
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

        val playButton = if (isLandscape) 58.dp else 66.dp
        val smallButton = if (isLandscape) 42.dp else 48.dp
        val hudSize = if (isLandscape) 50.dp else 56.dp
        val pillHeight = if (isLandscape) 38.dp else 40.dp
        val pillFont = if (isLandscape) 10.sp else 11.sp
        val popupWidth = if (isLandscape) 300.dp else 290.dp

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
                                showSubtitleSelector -> showSubtitleSelector = false
                                else -> showControls = !showControls
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
                            } else if (offset.x > width * 0.55f) {
                                exoPlayer.seekTo(
                                    (exoPlayer.currentPosition + 10000)
                                        .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                                )
                                position = exoPlayer.currentPosition
                                showControls = true
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

            FilledCircleHud(
                value = brightnessPercent,
                maxValue = 100,
                color = Color.White,
                icon = "☀",
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
                    .padding(bottom = if (isLandscape) 132.dp else 150.dp, end = 150.dp)
                    .width(popupWidth),
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

        if (showSubtitleSelector) {
            val subtitleTracks = exoPlayer.currentTracks.groups.filter {
                it.type == C.TRACK_TYPE_TEXT
            }

            val subtitleOffAction =
                TrackPopupRowData(
                    title = if (subtitlesEnabled) "Off" else "On",
                    subtitle = "",
                    onClick = {
                        subtitlesEnabled = !subtitlesEnabled

                        trackSelector.parameters =
                            trackSelector.buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                                .build()

                        showControls = true
                        menuTouchKey++
                    }
                )

            val subtitleRows =
                listOf(
                    TrackPopupRowData(
                        title = subtitleStatus,
                        subtitle = "Subtitle search",
                        onClick = {
                            showControls = true
                            subtitleStatus = "Searching..."
                            Toast.makeText(context, "Searching subtitles...", Toast.LENGTH_SHORT).show()

                            scope.launch {
                                val resumeAt = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val subtitleUri = OpenSubtitlesClient.downloadBestEnglishSubtitle(
                                    context = context,
                                    videoPath = currentVideo.path
                                )

                                if (subtitleUri != null) {
                                    subtitleStatus = "Loaded"
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
                                    subtitleStatus = "Not Found"
                                    Toast.makeText(context, "No subtitle found", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ),
                    TrackPopupRowData(
                        title = "Text +",
                        subtitle = "${subtitleTextSizeSp.toInt()}sp",
                        onClick = {
                            subtitleTextSizeSp = (subtitleTextSizeSp + 2f).coerceAtMost(30f)
                            showControls = true
                        }
                    ),
                    TrackPopupRowData(
                        title = "Text -",
                        subtitle = "${subtitleTextSizeSp.toInt()}sp",
                        onClick = {
                            subtitleTextSizeSp = (subtitleTextSizeSp - 2f).coerceAtLeast(12f)
                            showControls = true
                        }
                    ),
                    TrackPopupRowData(
                        title = "Sub Up",
                        subtitle = "Move up",
                        onClick = {
                            subtitleBottomPadding = (subtitleBottomPadding + 0.03f).coerceAtMost(0.30f)
                            showControls = true
                        }
                    ),
                    TrackPopupRowData(
                        title = "Sub Down",
                        subtitle = "Move down",
                        onClick = {
                            subtitleBottomPadding = (subtitleBottomPadding - 0.03f).coerceAtLeast(0.02f)
                            showControls = true
                        }
                    )
                ) + subtitleTracks.flatMap { group ->
                    List(group.length) { index ->
                        val format = group.getTrackFormat(index)
                        val language = friendlyLanguageName(format.language)

                        TrackPopupRowData(
                            title = language,
                            subtitle = "",
                            onClick = {
                                val override = TrackSelectionOverride(
                                    group.mediaTrackGroup,
                                    listOf(index)
                                )

                                subtitlesEnabled = true

                                trackSelector.parameters =
                                    trackSelector.buildUponParameters()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(override)
                                        .build()

                                showSubtitleSelector = false
                                showControls = true
                                menuTouchKey++
                            }
                        )
                    }
                }

            FloatingTrackPopup(
                title = "Subtitles",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (isLandscape) 132.dp else 150.dp, end = 82.dp)
                    .width(popupWidth),
                rows = subtitleRows,
                headerAction = subtitleOffAction,
                onAnyClick = {
                    menuTouchKey++
                },
                onClose = {
                    showSubtitleSelector = false
                    showControls = true
                }
            )
        }

        AnimatedVisibility(
            visible = showControls || isDraggingSeekbar || showAudioSelector || showSubtitleSelector,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← Back",
                        color = Color.White,
                        fontSize = if (isLandscape) 15.sp else 17.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { onBack() }
                            }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = cleanVideoTitle(currentVideo.path),
                        color = Color.White,
                        fontSize = if (isLandscape) 16.sp else 18.sp,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = if (isLandscape) 24.dp else 14.dp,
                            bottom = if (isLandscape) 84.dp else 104.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(if (isLandscape) 6.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlCircle("<<", smallButton) {
                        exoPlayer.seekTo(
                            (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                        )
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

                    ControlCircle(">>", smallButton) {
                        exoPlayer.seekTo(
                            (exoPlayer.currentPosition + 10000)
                                .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                        )
                        position = exoPlayer.currentPosition
                        showControls = true
                    }

                    PlayerPill("AUDIO", pillHeight, pillFont) {
                        showSubtitleSelector = false
                        showAudioSelector = !showAudioSelector
                        showControls = true
                        menuTouchKey++
                    }

                    PlayerPill("CC", pillHeight, pillFont) {
                        showAudioSelector = false
                        showSubtitleSelector = !showSubtitleSelector
                        showControls = true
                        menuTouchKey++
                    }

                    PlayerPill("MINI", pillHeight, pillFont) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()

                            activity?.enterPictureInPictureMode(params)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.18f))
                        .padding(horizontal = 26.dp, vertical = 18.dp)
                ) {
                    Slider(
                        value = position.coerceIn(0L, duration).toFloat(),
                        onValueChange = {
                            isDraggingSeekbar = true
                            showControls = true
                            position = it.toLong()
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(position.coerceIn(0L, duration))
                            isDraggingSeekbar = false
                            showControls = true
                        },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF03A9F4),
                            activeTrackColor = Color(0xFF03A9F4),
                            inactiveTrackColor = Color.DarkGray
                        )
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
private fun TimePill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
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
                fontSize = 14.sp
            )

            Text(
                text = "$value%",
                color = if (value > 60) Color.Black else Color.White,
                fontSize = 10.sp,
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
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF242424))
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text == "Ⅱ" || text == "▶") 25.sp else 14.sp,
            fontWeight = FontWeight.Bold
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
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF242424))
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

private data class TrackPopupRowData(
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun FloatingTrackPopup(
    title: String,
    modifier: Modifier,
    rows: List<TrackPopupRowData>,
    headerAction: TrackPopupRowData? = null,
    onAnyClick: () -> Unit = {},
    onClose: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (headerAction != null) {
                MiniMenuChip(
                    TrackPopupRowData(
                        title = headerAction.title,
                        subtitle = headerAction.subtitle,
                        onClick = {
                            onAnyClick()
                            headerAction.onClick()
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (rows.isEmpty()) {
            Text(
                text = "No tracks found",
                color = Color.LightGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows) { row ->
                    TrackPopupRow(
                        TrackPopupRowData(
                            title = row.title,
                            subtitle = row.subtitle,
                            onClick = {
                                onAnyClick()
                                row.onClick()
                            }
                        )
                    )
                }

                item {
                    TrackPopupRow(
                        TrackPopupRowData(
                            title = "Close",
                            subtitle = "",
                            onClick = {
                                onAnyClick()
                                onClose()
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMenuChip(row: TrackPopupRowData) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable { row.onClick() }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            row.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackPopupRow(row: TrackPopupRowData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .pointerInput(Unit) {
                detectTapGestures { row.onClick() }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(row.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)

        if (row.subtitle.isNotBlank()) {
            Text(row.subtitle, color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
        }
    }
}

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
