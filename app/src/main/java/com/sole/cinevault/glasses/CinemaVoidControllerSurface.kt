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
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
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

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "External display active",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.54f),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "Touch anywhere to control CineVault",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.42f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
