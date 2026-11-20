package com.sole.cinevault.glasses.display

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * What the glasses show on top of the film besides the picture and subtitles.
 *
 * The tablet owns the real controls (play, subtitles, Subtitle Studio, Audio Studio and so
 * on, shown on the tablet screen), so the glasses only mirror a quiet status: a bar with the
 * title and progress when controls are showing, short messages for gestures (speed, seek,
 * zoom ...) and the seek preview. Nothing here is clickable, so it can never fight the
 * touchpad gestures.
 */
internal object ExternalPlayerHudState {
    var controlsVisible by mutableStateOf(false)
    var title by mutableStateOf("")

    var gestureLabel by mutableStateOf("")
    var gestureValue by mutableStateOf<String?>(null)
    var gestureProgress by mutableStateOf<Int?>(null)
    var gestureStamp by mutableLongStateOf(0L)

    /** True while a supported 3D side-by-side film fills the whole glasses frame. */
    var fullFrame3d by mutableStateOf(false)

    var stereoNotice by mutableStateOf<com.sole.cinevault.glasses.stereo.StereoNotice?>(null)

    var seekPreviewVisible by mutableStateOf(false)
    var seekPreviewMs by mutableLongStateOf(0L)
    var seekPreviewBitmap by mutableStateOf<Bitmap?>(null)

    fun showGesture(label: String, value: String?, progress: Int?) {
        gestureLabel = label
        gestureValue = value
        gestureProgress = progress
        gestureStamp = System.nanoTime()
    }

    /** The glasses asked to hide the controls (pointer tapped empty picture). */
    var hideRequested by mutableLongStateOf(0L)

    fun requestHide() {
        hideRequested = System.nanoTime()
    }

    fun reset() {
        controlsVisible = false
        stereoNotice = null
        fullFrame3d = false
        seekPreviewVisible = false
        seekPreviewBitmap = null
        gestureLabel = ""
        gestureValue = null
        gestureProgress = null
    }
}

internal fun formatHudTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
internal fun ExternalPlayerHudOverlay(player: Player, modifier: Modifier = Modifier) {
    val hud = ExternalPlayerHudState

    // Gesture messages disappear by themselves.
    var gestureShown by remember { mutableStateOf(false) }
    LaunchedEffect(hud.gestureStamp) {
        if (hud.gestureStamp != 0L) {
            gestureShown = true
            delay(1300)
            gestureShown = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ExternalStereoNoticeOverlay()

        AnimatedVisibility(
            visible = hud.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ExternalPlayerControls(player = player, title = hud.title)
        }

        AnimatedVisibility(
            visible = gestureShown && !hud.seekPreviewVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            GesturePill(hud.gestureLabel, hud.gestureValue, hud.gestureProgress)
        }

        AnimatedVisibility(
            visible = hud.seekPreviewVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SeekPreview(hud.seekPreviewBitmap, hud.seekPreviewMs, player.duration)
        }
    }
}

/** The 3D note: shows for a few seconds when a film starts (or the choice changes). */
@Composable
internal fun ExternalStereoNoticeOverlay(modifier: Modifier = Modifier) {
    val hud = ExternalPlayerHudState
    // The 3D note shows for a few seconds when a film starts (or the choice changes).
    var noticeShown by remember { mutableStateOf(false) }
    LaunchedEffect(hud.stereoNotice) {
        if (hud.stereoNotice != null) {
            noticeShown = true
            delay(7000)
            noticeShown = false
        } else {
            noticeShown = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = noticeShown && hud.stereoNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
        ) {
            hud.stereoNotice?.let { n ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(n.title, color = AmberCore, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(n.detail, color = TextBright, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GesturePill(label: String, value: String?, progress: Int?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        if (value != null) {
            Text(value, color = AmberCore, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { (progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.width(180.dp).height(4.dp).clip(RoundedCornerShape(50)),
                color = AmberCore,
                trackColor = Color.White.copy(alpha = 0.22f),
            )
        }
    }
}

@Composable
private fun SeekPreview(bitmap: Bitmap?, positionMs: Long, durationMs: Long) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
        Text(
            text = formatHudTime(positionMs) + if (durationMs > 0) "  /  " + formatHudTime(durationMs) else "",
            color = AmberCore,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
