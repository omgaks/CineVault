package com.sole.cinevault.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.glasses.display.GlassesScreenSize
import com.sole.cinevault.glasses.display.GlassPill
import com.sole.cinevault.glasses.display.GlassesBackBridge
import com.sole.cinevault.glasses.display.GlassSizeCapsule
import com.sole.cinevault.glasses.display.ExternalPlayerHudState
import com.sole.cinevault.glasses.display.ExternalViewportSessionState
import com.sole.cinevault.glasses.display.GlassesDisplay3d
import com.sole.cinevault.ui.theme.AmberCore

private const val FIVE_FINGER_SPREAD = 1.35f

/**
 * The tablet as a touchpad on EVERY screen while glasses are connected, not just inside a
 * film. Without it nothing on the tablet can move the glasses pointer outside playback.
 *
 * It is never a trap: "Use tablet" hides it, a small chip brings it back, and a five-finger
 * spread ends the glasses session. Hidden only while the tablet shows the player screen (it has its own pad).
 */
@Composable
internal fun AppCinemaVoidPad() {
    val session = LocalAppGlassesSession.current ?: return
    // Only when the tablet itself shows the player screen (which has its own pad). Music or a
    // film shown on the glasses must not take the touchpad away.
    val filmPlaying = GlassesFilmOwner.hostShowsPlayer
    // Resets whenever a different pair of glasses is connected.
    var padOn by remember(session.display.displayId) { mutableStateOf(true) }
    val traceContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(session.active, filmPlaying, padOn) {
        GlassesTrace.log(traceContext, "app pad: active=${session.active} filmPlaying=$filmPlaying padOn=$padOn")
    }
    if (!session.active || filmPlaying) return

    if (padOn) {
        Box(modifier = Modifier.fillMaxSize()) {
            CinemaVoidControllerSurface(
                modifier = Modifier.fillMaxSize().fiveFingerReturn(session.disable),
                inPlayer = false,
                onTwoFingerTap = { GlassesBackBridge.handler?.invoke() },
                // A film on the glasses: two fingers zoom (50% to 200%) and move the picture.
                // A 3D side-by-side film keeps its full frame, so it is not zoomed.
                onPinchPan = if (GlassesFilmOwner.externalShowsPlayer && !ExternalPlayerHudState.fullFrame3d) {
                    { zoom, fx, fy -> ExternalViewportSessionState.applyPadGesture(zoom, fx, fy) }
                } else {
                    null
                },
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPill("Use tablet") { padOn = false }
                if (GlassesFilmOwner.externalShowsPlayer && !ExternalPlayerHudState.fullFrame3d) {
                    val zoomPercent = (ExternalViewportSessionState.transform.scale * 100f).toInt()
                    GlassPill(if (zoomPercent == 100) "Zoom: fit" else "Zoom $zoomPercent% (tap to fit)") {
                        ExternalViewportSessionState.reset()
                    }
                }
                GlassPill("Keyboard") { com.sole.cinevault.glasses.keyboard.GlassesKeyboardState.toggleManual() }
                GlassPill(if (GlassesDisplay3d.enabled) "3D display: on" else "3D display: off") {
                    GlassesDisplay3d.set(traceContext, !GlassesDisplay3d.enabled)
                }
                GlassSizeCapsule(
                    scale = GlassesScreenSize.scale,
                    onSmaller = { GlassesScreenSize.smaller(traceContext) },
                    onReset = { GlassesScreenSize.set(traceContext, 1f) },
                    onBigger = { GlassesScreenSize.bigger(traceContext) },
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSizeCapsule(
                    scale = GlassesScreenSize.scale,
                    onSmaller = { GlassesScreenSize.smaller(traceContext) },
                    onReset = { GlassesScreenSize.set(traceContext, 1f) },
                    onBigger = { GlassesScreenSize.bigger(traceContext) },
                )
                GlassPill(if (GlassesDisplay3d.enabled) "3D display: on" else "3D display: off") {
                    GlassesDisplay3d.set(traceContext, !GlassesDisplay3d.enabled)
                }
                GlassPill("Glasses touchpad") { padOn = true }
            }
        }
    }
}

/** Five fingers spreading apart = give the tablet back. Observes only; never consumes. */
private fun Modifier.fiveFingerReturn(onReturn: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var spread = 1f
        var fired = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 5) {
                spread *= event.calculateZoom()
                if (!fired && spread >= FIVE_FINGER_SPREAD) {
                    fired = true
                    onReturn()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
