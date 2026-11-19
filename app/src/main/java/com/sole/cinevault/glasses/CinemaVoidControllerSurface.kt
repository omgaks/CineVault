package com.sole.cinevault.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.sole.cinevault.glasses.halo.HaloPointerMapper
import com.sole.cinevault.glasses.halo.HaloRemoteInputSession
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * D3-2 — host-side Cinema Void controller surface.
 *
 * This is intentionally not another player UI. While the external CineVault
 * presentation owns video, the host becomes a dark, adaptive touch surface.
 * The actual gesture ownership stays in PlayerPlaybackGestureLayer/Halo so
 * 100% of this surface remains available for Halo input.
 *
 * All sizing comes from the CURRENT available Compose window. No device,
 * vendor, physical-screen-size or fixed-resolution assumptions are used.
 */
@Composable
fun CinemaVoidControllerSurface(
    modifier: Modifier = Modifier,
    inPlayer: Boolean = true,
    onTwoFingerTap: (() -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (inPlayer) Modifier.playerPadTouch() else Modifier.appPadTouch(onTwoFingerTap = onTwoFingerTap))
    ) {
        val spec = CinemaVoidAdaptivePolicy.resolve(
            availableWidthDp = maxWidth.value.toInt(),
            availableHeightDp = maxHeight.value.toInt(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = spec.horizontalPaddingDp.dp,
                    vertical = spec.verticalPaddingDp.dp,
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = spec.statusCardMaxWidthDp.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.055f),
                contentColor = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = (spec.horizontalPaddingDp + 6).dp,
                        vertical = (spec.verticalPaddingDp + 4).dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "CINEMA VOID",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                    )

                    com.sole.cinevault.glasses.display.ExternalPlayerHudState.stereoNotice?.let { n ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = n.title + ": " + n.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = com.sole.cinevault.ui.theme.AmberCore,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // While a film plays the guide shows for a few seconds at the start, then
                    // fades so the surface is just a quiet touchpad. Outside a film it stays.
                    CinemaVoidGuide(
                        rows = if (inPlayer) PLAYER_GUIDE_ROWS else APP_GUIDE_ROWS,
                        autoHideMs = if (inPlayer) 18_000L else null,
                    )
                }
            }
        }
    }
}


/** Player pad: every touch goes straight to Halo, which owns the film gestures. */
private fun Modifier.playerPadTouch(): Modifier = pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event =
                            awaitPointerEvent(pass = PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: continue
                        val liveSize = this@pointerInput.size
                        if (liveSize.width <= 0 || liveSize.height <= 0) continue

                        HaloRemoteInputSession.publish(
                            sample =
                                HaloPointerMapper.sample(
                                    xPx = change.position.x,
                                    yPx = change.position.y,
                                    widthPx = liveSize.width,
                                    heightPx = liveSize.height,
                                    pressed = change.pressed,
                                ),
                            sourceWidthPx = liveSize.width,
                            sourceHeightPx = liveSize.height,
                        )
                    }
                }
            }
