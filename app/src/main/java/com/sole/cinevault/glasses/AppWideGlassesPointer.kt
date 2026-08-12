package com.sole.cinevault.glasses

import android.app.Activity
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Navigation layer used while Android is mirroring CineVault to connected
 * glasses and the dedicated video Presentation is not active. Directional
 * touchpad movement is converted to Android focus navigation, so it works
 * across Compose screens without synthetic coordinate clicks or an
 * Accessibility Service. The halo is mirrored with the app itself.
 */
@Composable
fun AppWideGlassesPointer(
    activity: Activity?,
    sessionKey: Any?,
    onEmergencyReturnToTablet: () -> Unit
) {
    var pointer by remember(sessionKey) { mutableStateOf(Offset.Unspecified) }
    var touchPulse by remember { mutableStateOf(false) }
    fun pointerReady() = !pointer.x.isNaN() && !pointer.y.isNaN()

    fun sendKey(keyCode: Int) {
        val host = activity ?: return
        val now = android.os.SystemClock.uptimeMillis()
        host.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        host.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    LaunchedEffect(sessionKey) { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(sessionKey) {
                detectTapGestures(
                    onTap = { touchPulse = !touchPulse; sendKey(KeyEvent.KEYCODE_DPAD_CENTER) },
                    onLongPress = { sendKey(KeyEvent.KEYCODE_BACK) }
                )
            }
            .pointerInput(sessionKey) {
                var accumulated = Offset.Zero
                detectDragGestures(
                    onDragStart = { start ->
                        if (!pointerReady()) pointer = start
                        accumulated = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        pointer = if (pointerReady()) {
                            Offset(
                                (pointer.x + amount.x * 1.35f).coerceIn(12f, size.width - 12f),
                                (pointer.y + amount.y * 1.35f).coerceIn(12f, size.height - 12f)
                            )
                        } else change.position
                        accumulated += amount
                        val threshold = 54f
                        if (abs(accumulated.x) >= threshold || abs(accumulated.y) >= threshold) {
                            if (abs(accumulated.x) > abs(accumulated.y)) {
                                sendKey(if (accumulated.x > 0f) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                            } else {
                                sendKey(if (accumulated.y > 0f) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                            }
                            accumulated = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(sessionKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var spread = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 5) {
                            spread *= event.calculateZoom()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                            if (spread >= 1.35f) onEmergencyReturnToTablet()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = if (pointerReady()) pointer else center
            drawCircle(Color(0x55FFC24D), radius = if (touchPulse) 20f else 16f, center = p)
            drawCircle(Color(0xFFFFC24D), radius = 11f, center = p, style = Stroke(width = 3f))
            drawCircle(Color.White, radius = 2.5f, center = p)
        }
    }
}
