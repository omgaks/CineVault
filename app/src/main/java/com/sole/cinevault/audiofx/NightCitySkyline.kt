package com.sole.cinevault.audiofx

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.clip
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
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("cinevault_audio_night_city", android.content.Context.MODE_PRIVATE)
    }
    var environment by remember(prefs) {
        mutableStateOf(NightCityEnvironment.fromSavedName(prefs.getString("environment", null)))
    }
    var motionEnabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean("motion_enabled", true))
    }
    var pausedPhase by remember { mutableStateOf(0f) }
    // Remove the infinite transition entirely while motion is paused.
    // This avoids continuous Compose invalidations when the visualizer is static.
    val phase = if (motionEnabled) {
        val animation = rememberInfiniteTransition(label = "NightCityAtmosphere")
        val motionPhase by animation.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(9000), repeatMode = RepeatMode.Restart),
            label = "CityMotionPhase",
        )
        pausedPhase = motionPhase
        motionPhase
    } else {
        pausedPhase
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "CITY ENVIRONMENT",
            color = Color(0xFFA8A6A0),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 7.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NightCityEnvironment.entries.forEach { option ->
                val selected = option == environment
                Text(
                    text = option.displayName,
                    color = if (selected) NightCityEnvironment.signatureAmber else Color(0xFFB7B7C4),
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            if (selected) Color(0xFF3B2B15) else Color(0xFF171925),
                            RoundedCornerShape(12.dp),
                        )
                        .selectable(selected = selected, role = Role.RadioButton) {
                            environment = option
                            prefs.edit().putString("environment", option.name).apply()
                        }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                )
            }
        }
        Text(
            text = if (motionEnabled) "MOTION ON · TAP TO PAUSE" else "MOTION OFF · TAP TO RESUME",
            color = NightCityEnvironment.signatureAmber,
            fontSize = 11.sp,
            modifier = Modifier
                .semantics { stateDescription = if (motionEnabled) "Animation on" else "Animation paused" }
                .clickable(role = Role.Switch) {
                    motionEnabled = !motionEnabled
                    prefs.edit().putBoolean("motion_enabled", motionEnabled).apply()
                }
                .heightIn(min = 48.dp)
                .padding(vertical = 16.dp),
        )
        Canvas(
            modifier = Modifier.fillMaxWidth().height(184.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF05060A)),
        ) {
            val w = size.width
            val h = size.height
            drawRect(brush = Brush.verticalGradient(listOf(environment.skyTop, environment.skyBottom)))
            drawCircle(
                color = environment.atmosphericGlow.copy(alpha = 0.34f),
                radius = w * 0.33f,
                center = Offset(w * 0.77f, h * 0.28f),
            )
            // Distant tower layer adds atmospheric depth.
            for (tower in 0 until 18) {
                val tw = w / 18f
                val th = h * (0.14f + abs(sin(tower * 2.13)).toFloat() * 0.36f)
                drawRect(
                    color = environment.building.copy(alpha = 0.48f),
                    topLeft = Offset(tower * tw, h - th),
                    size = Size(tw * 0.84f, th),
                )
            }
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
                val energy = NightCityFftEnergy.at(fft, i, count)
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
            // Spectrum follows actual decoded FFT energy.
            val spectrumBase = h * 0.965f
            for (bar in 0 until 32) {
                val energy = NightCityFftEnergy.at(fft, bar, 32)
                val barHeight = 2f + energy * h * 0.14f
                val bx = w * (bar + 0.5f) / 32f
                drawLine(
                    color = environment.neonPrimary.copy(alpha = 0.32f + energy * 0.55f),
                    start = Offset(bx, spectrumBase),
                    end = Offset(bx, spectrumBase - barHeight),
                    strokeWidth = (w / 32f * 0.42f).coerceAtLeast(1f),
                )
            }
            drawLine(
                color = NightCityEnvironment.signatureAmber.copy(alpha = 0.7f),
                start = Offset(0f, h - 1f),
                end = Offset(w, h - 1f),
                strokeWidth = 2f,
            )
            if (environment == NightCityEnvironment.ANIME_RAINFALL) {
                // Reflective wet pavement with scattered neon streaks.
                for (streak in 0 until 14) {
                    val sx = (streak * 83.3f) % w
                    val sy = h * (0.89f + (streak % 5) * 0.022f)
                    drawLine(
                        color = environment.neonPrimary.copy(alpha = 0.11f + (streak % 3) * 0.07f),
                        start = Offset(sx, sy),
                        end = Offset((sx + w * 0.07f).coerceAtMost(w), sy),
                        strokeWidth = 1.4f,
                    )
                }
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
                // Tiered pagoda silhouette, cyan roof edges, and drifting sakura.
                val baseX = w * 0.11f
                for (tier in 0 until 3) {
                    val roofY = h * (0.84f - tier * 0.15f)
                    val halfWidth = w * (0.13f - tier * 0.025f)
                    val roof = Path().apply {
                        moveTo(baseX - halfWidth, roofY)
                        lineTo(baseX - halfWidth * 0.78f, roofY - h * 0.045f)
                        lineTo(baseX, roofY - h * 0.085f)
                        lineTo(baseX + halfWidth * 0.78f, roofY - h * 0.045f)
                        lineTo(baseX + halfWidth, roofY)
                        close()
                    }
                    drawPath(roof, color = Color(0xFF0A1020))
                    drawLine(environment.neonSecondary.copy(alpha = 0.8f),
                        Offset(baseX - halfWidth, roofY),
                        Offset(baseX, roofY - h * 0.085f), 2f)
                    drawLine(environment.neonSecondary.copy(alpha = 0.8f),
                        Offset(baseX, roofY - h * 0.085f),
                        Offset(baseX + halfWidth, roofY), 2f)
                    drawRect(
                        color = environment.building,
                        topLeft = Offset(baseX - halfWidth * 0.48f, roofY),
                        size = Size(halfWidth * 0.96f, h * 0.12f),
                    )
                }
                for (petal in 0 until 23) {
                    val x = ((petal * 71.9f) + phase * w * 0.28f) % w
                    val y = ((petal * 37.1f) + phase * h * 0.19f) % h
                    drawCircle(
                        color = Color(0xFFFFA7C6).copy(alpha = 0.28f + (petal % 3) * 0.13f),
                        radius = 1.5f + (petal % 3) * 0.55f,
                        center = Offset(x, y),
                    )
                }
                // Architectural ground reflection.
                for (reflection in 0 until 9) {
                    val yy = h * 0.9f + reflection * h * 0.012f
                    drawLine(
                        environment.neonSecondary.copy(alpha = 0.26f * (1f - reflection / 10f)),
                        Offset(w * 0.02f + reflection * 2f, yy),
                        Offset(w * 0.23f - reflection * 2f, yy),
                        1f,
                    )
                }
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
