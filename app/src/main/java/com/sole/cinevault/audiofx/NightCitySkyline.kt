package com.sole.cinevault.audiofx

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Audio Studio's lightweight anime skyline. Render-only: never touches the
 * audio engine. The audio FFT is an optional input; silence yields still lights.
 * Decorative city shapes are deterministic to avoid flicker on recomposition.
 */
@Composable
internal fun NightCitySkyline(
    fft: ByteArray,
    modifier: Modifier = Modifier,
) {
    var environment by remember { mutableStateOf(NightCityEnvironment.ANIME_RAINFALL) }
    var motionEnabled by remember { mutableStateOf(true) }
    val animation = rememberInfiniteTransition(label = "NightCityAtmosphere")
    val motionPhase by animation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(9000), repeatMode = RepeatMode.Restart),
        label = "CityMotionPhase",
    )
    val phase = if (motionEnabled) motionPhase else 0f
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NightCityEnvironment.entries.forEach { option ->
                val selected = option == environment
                Text(
                    text = option.displayName,
                    color = if (selected) NightCityEnvironment.signatureAmber else Color(0xFFB7B7C4),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) Color(0xFF3B2B15) else Color(0xFF171925),
                            RoundedCornerShape(9.dp),
                        )
                        .clickable { environment = option }
                        .padding(horizontal = 5.dp, vertical = 10.dp),
                )
            }
        }
        Text(
            text = if (motionEnabled) "MOTION ON · TAP TO PAUSE" else "MOTION OFF · TAP TO RESUME",
            color = NightCityEnvironment.signatureAmber,
            fontSize = 10.sp,
            modifier = Modifier.clickable { motionEnabled = !motionEnabled }
                .padding(bottom = 7.dp),
        )
        Canvas(
            modifier = Modifier.fillMaxWidth().height(150.dp)
                .background(Color(0xFF05060A), RoundedCornerShape(12.dp)),
        ) {
            val w = size.width
            val h = size.height
            drawRect(brush = Brush.verticalGradient(listOf(environment.skyTop, environment.skyBottom)))
            drawCircle(
                color = environment.atmosphericGlow.copy(alpha = 0.34f),
                radius = w * 0.33f,
                center = Offset(w * 0.77f, h * 0.28f),
            )
            val count = 27
            val buildingWidth = w / count
            for (i in 0 until count) {
                val shape = abs(sin((i + 2) * 1.731)).toFloat()
                val buildingHeight = h * (0.24f + shape * 0.49f)
                val x = i * buildingWidth
                val y = h - buildingHeight
                drawRect(
                    color = environment.building,
                    topLeft = Offset(x + 1f, y),
                    size = Size(buildingWidth - 2f, buildingHeight),
                )
                drawLine(
                    color = environment.neonPrimary.copy(alpha = 0.55f),
                    start = Offset(x + 1f, y),
                    end = Offset(x + buildingWidth - 1f, y),
                    strokeWidth = 1.3f,
                )
                val bin = if (fft.isEmpty()) 0 else ((i * 2 + 1) % fft.size)
                val energy = if (fft.isEmpty()) 0f else (fft[bin].toInt() and 0xff) / 255f
                val rows = (buildingHeight / 13f).toInt()
                for (row in 1 until rows) {
                    if ((i * 7 + row * 11) % 5 == 0) continue
                    val pulse = if (motionEnabled) 0.10f * sin(phase * 6.283185f + i * 0.73f + row).toFloat() else 0f
                    val alpha = (0.28f + energy * 0.55f + pulse).coerceIn(0.08f, 1f)
                    drawRect(
                        color = if ((i + row) % 4 == 0) environment.neonSecondary.copy(alpha = alpha)
                        else environment.windowLight.copy(alpha = alpha),
                        topLeft = Offset(x + buildingWidth * 0.28f, y + row * 12f),
                        size = Size(buildingWidth * 0.4f, 3.5f),
                    )
                }
            }
            drawLine(
                color = NightCityEnvironment.signatureAmber.copy(alpha = 0.7f),
                start = Offset(0f, h - 1f),
                end = Offset(w, h - 1f),
                strokeWidth = 2f,
            )
            if (environment == NightCityEnvironment.ANIME_RAINFALL) {
                for (i in 0 until 44) {
                    val x = (i * 47.3f) % w
                    val y = ((i * 31.7f) + phase * h * 1.7f) % h
                    drawLine(
                        color = Color(0xFF7CCFFF).copy(alpha = 0.25f),
                        start = Offset(x, y),
                        end = Offset(x - 3f, y + 12f),
                        strokeWidth = 1f,
                    )
                }
            }
            // Elevated train: geometric light strip moves continuously across the skyline.
            val trackY = h * 0.81f
            drawLine(
                color = environment.neonSecondary.copy(alpha = 0.45f),
                start = Offset(0f, trackY + 8f),
                end = Offset(w, trackY + 8f),
                strokeWidth = 2f,
            )
            val trainWidth = w * 0.23f
            val trainX = -trainWidth + phase * (w + trainWidth)
            drawRect(
                color = Color(0xFF171923),
                topLeft = Offset(trainX, trackY),
                size = Size(trainWidth, 11f),
            )
            drawLine(
                color = NightCityEnvironment.signatureAmber,
                start = Offset(trainX, trackY),
                end = Offset(trainX + trainWidth, trackY),
                strokeWidth = 2f,
            )
            for (window in 0 until 7) {
                drawRect(
                    color = environment.windowLight.copy(alpha = 0.8f),
                    topLeft = Offset(trainX + (window + 0.5f) * trainWidth / 8f, trackY + 3f),
                    size = Size(trainWidth / 15f, 4f),
                )
            }
            if (environment == NightCityEnvironment.SKYLINE_AT_DUSK) {
                drawCircle(
                    color = NightCityEnvironment.signatureAmber.copy(alpha = 0.65f),
                    radius = h * 0.095f,
                    center = Offset(w * 0.78f, h * 0.24f),
                )
            }
            if (environment == NightCityEnvironment.NEO_KYOTO_MIDNIGHT) {
                // Minimal temple roof silhouette; detailed environment art comes later.
                val roofY = h * 0.73f
                drawLine(environment.neonSecondary, Offset(w * 0.04f, roofY),
                    Offset(w * 0.22f, roofY), 3f)
                drawLine(environment.neonSecondary, Offset(w * 0.13f, roofY - 14f),
                    Offset(w * 0.22f, roofY), 2f)
                drawLine(environment.neonSecondary, Offset(w * 0.13f, roofY - 14f),
                    Offset(w * 0.04f, roofY), 2f)
            }
        }
        Text(
            "ANIME NIGHT CITY  •  DECODED AUDIO VISUALIZATION",
            color = Color(0xFF9B96A7),
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}
