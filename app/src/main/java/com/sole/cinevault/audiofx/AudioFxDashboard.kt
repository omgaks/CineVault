package com.sole.cinevault.audiofx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.responsive.CineResponsive
import com.sole.cinevault.ui.responsive.cineAdaptiveTokens
import com.sole.cinevault.ui.responsive.rememberCineWindowSizeInfo
import com.sole.cinevault.ui.theme.*

/*
 * AudioFxDashboard.kt
 *
 * Compose UI for AudioFxController — Phase 0's dashboard, built entirely
 * on android.media.audiofx. Same visual language as the rest of the
 * player (glassPanel, AmberCore accents) rather than a new style
 * introduced just for this.
 *
 * One honesty note carried over from the controller: every number here
 * (band gain, bass strength, reverb wet/dry) is verified against Android's
 * documented range for that effect — the UI won't let you set an
 * impossible value, and the code compiles against the real API contract.
 * Whether a given setting actually sounds good is a different question
 * this dashboard can't answer for you; there's no substitute for turning
 * a slider and listening.
 */
// but is private to that package — replicated here (same styling) rather
// than exposing it cross-package or duplicating a public API surface that
// belongs to a different concern.
@Composable
private fun AmberSectionPill(text: String) {
    Text(
        text = text.uppercase(),
        color = AmberCore,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.45.sp,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore.copy(alpha = 0.12f))
            .border(1.dp, AmberCore.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
internal fun AudioFxDashboard(
    controller: AudioFxController,
    onDismiss: () -> Unit,
    popupWidth: Dp? = null,
    popupMaxHeight: Dp? = null
) {
    val windowInfo = rememberCineWindowSizeInfo()
    val tokens = cineAdaptiveTokens(windowInfo)
    val resolvedWidth = popupWidth ?: CineResponsive.popupMaxWidth(windowInfo)
    val resolvedMaxHeight = popupMaxHeight ?: CineResponsive.popupMaxHeight(windowInfo)

    Column(
        modifier = Modifier
            .width(resolvedWidth)
            .heightIn(max = resolvedMaxHeight)
            .glassPanel(cornerRadius = 20.dp, fill = SpaceMid.copy(alpha = 0.90f))
            .border(1.dp, AmberCore.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .verticalScroll(rememberScrollState())
            .padding(tokens.componentGap)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Equalizer, contentDescription = null, tint = AmberCore, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AUDIO FX",
                color = AmberCore,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(1.dp, AmberCore.copy(alpha = 0.28f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Icon(
                imageVector = Icons.Filled.Close, contentDescription = "Close", tint = AmberCore,
                modifier = Modifier
                    .sizeIn(minWidth = tokens.minimumTouchTarget, minHeight = tokens.minimumTouchTarget)
                    .clip(CircleShape)
                    .background(GlassSurface)
                    .clickable { onDismiss() }
                    .padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Enabled", color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(
                checked = controller.enabled,
                onCheckedChange = { controller.updateEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        AmberSectionPill("Loudness Normalization")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto LUFS", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val status = when {
                    controller.isAnalyzingLoudness -> "Analyzing…"
                    controller.lastMeasuredLufs != null -> String.format(
                        "Measured %.1f LUFS · %+.1f dB",
                        controller.lastMeasuredLufs,
                        controller.lastAppliedGainDb ?: 0.0,
                    )
                    else -> "Analyze once when the video is ready"
                }
                Text(status, color = TextMuted, fontSize = 9.5.sp)
            }
            Switch(
                checked = controller.loudnessNormalizationEnabled,
                onCheckedChange = controller::updateLoudnessNormalizationEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AmberCore,
                    checkedTrackColor = AmberGlow.copy(alpha = 0.4f),
                ),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        AdaptiveAudioSection(controller)

        if (controller.bandCount > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            AmberSectionPill("Equalizer")
            Spacer(modifier = Modifier.height(6.dp))
            controller.bandCenterFreqHz.forEachIndexed { index, freqHz ->
                val db = controller.bandLevelsDb.getOrElse(index) { 0 }
                EqBandRow(
                    label = formatFreqLabel(freqHz),
                    valueDb = db,
                    rangeDb = controller.bandLevelRangeDb,
                    onValueChange = { controller.setBandLevel(index, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        AmberSectionPill("Bass Boost")
        FxSliderRow(
            valueText = "${(controller.bassBoostStrength / 10)}%",
            value = controller.bassBoostStrength.toFloat(),
            range = 0f..1000f,
            onValueChange = { controller.updateBassBoostStrength(it.toInt()) }
        )

        Spacer(modifier = Modifier.height(10.dp))
        AmberSectionPill("Virtualizer (Surround Width)")
        FxSliderRow(
            valueText = "${(controller.virtualizerStrength / 10)}%",
            value = controller.virtualizerStrength.toFloat(),
            range = 0f..1000f,
            onValueChange = { controller.updateVirtualizerStrength(it.toInt()) }
        )

        Spacer(modifier = Modifier.height(10.dp))
        AmberSectionPill("Loudness Enhancer")
        Text(
            text = "Boosts overall loudness beyond 0dB — useful for quiet source material, but can introduce clipping distortion at high settings.",
            color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        FxSliderRow(
            valueText = "+${controller.loudnessGainMillibels / 100}dB",
            value = controller.loudnessGainMillibels.toFloat(),
            range = 0f..2000f,
            onValueChange = { controller.setLoudnessGain(it.toInt()) }
        )

        if (controller.dynamicsProcessingAvailable) {
            Spacer(modifier = Modifier.height(10.dp))
            AmberSectionPill("Compressor / Limiter")
            Text(
                text = "Multi-band mastering chain (Android's DynamicsProcessing, API 28+) — evens out quiet/loud passages and catches peaks before clipping.",
                color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            FxSliderRow(
                valueText = "${controller.compressorAmount}%",
                value = controller.compressorAmount.toFloat(),
                range = 0f..100f,
                onValueChange = { controller.updateCompressorAmount(it.toInt()) }
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Peak limiter", color = TextBright, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = controller.limiterEnabled,
                    onCheckedChange = { controller.updateLimiterEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        AmberSectionPill("Spectrum")
        SpectrumVisualizer(fft = controller.fft, waveform = controller.waveform)

        Spacer(modifier = Modifier.height(14.dp))
        AmberSectionPill("Reverb")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            AudioFxController.REVERB_PRESETS.forEach { (value, label) ->
                val selected = controller.reverbPreset == value
                Text(
                    text = label,
                    color = if (selected) Color.Black else TextBright,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AmberCore else GlassSurface)
                        .clickable { controller.updateReverbPreset(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        PresetsSection(controller)

        Spacer(modifier = Modifier.height(14.dp))
        AutoSwitchSection(controller)

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "↺  Reset All",
            color = AmberCore,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(AmberCore.copy(alpha = 0.10f))
                .border(1.dp, AmberCore.copy(alpha = 0.30f), RoundedCornerShape(50))
                .clickable { controller.resetAll() }
                .padding(vertical = 8.dp)
        )
    }
}

// Adaptive Audio (Beta) — see AudioFxController.kt / AudioSceneAnalyzer.kt
// for the full design reasoning. This section's job is honesty: it's
// labeled Beta, it shows exactly what's currently detected and WHY (the
// debug readout), rather than presenting a black-box "smart" toggle that
// gives no way to judge whether it's actually helping.
@Composable
private fun AdaptiveAudioSection(controller: AudioFxController) {
    AmberSectionPill("Adaptive Audio (Beta)")
    Text(
        text = "Automatically nudges dialogue presence, compression, and the limiter based on what's playing right now — real-time signal analysis, not scene recognition. A heuristic, not AI; it can be wrong. Your own EQ/FX settings above are the baseline this adjusts on top of, never overwritten.",
        color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Enable adaptive audio", color = TextBright, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = controller.adaptiveAudioEnabled,
            onCheckedChange = { controller.updateAdaptiveAudioEnabled(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
        )
    }
    if (controller.adaptiveAudioEnabled) {
        Spacer(modifier = Modifier.height(6.dp))
        val sceneColor = when (controller.detectedScene) {
            AudioScene.DIALOGUE -> AmberCore
            AudioScene.ACTION -> Color(0xFFFF6B5B)
            AudioScene.MUSIC -> Color(0xFF7FB8FF)
            AudioScene.NEUTRAL -> TextMuted
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Now: ${controller.detectedScene.label}",
                color = sceneColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(sceneColor.copy(alpha = 0.14f))
                    .border(1.dp, sceneColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        if (controller.sceneDebugText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = controller.sceneDebugText, color = TextFaint, fontSize = 9.sp)
        }
    }
}

// Save the current EQ/FX state under a name, and load/delete previously
// saved ones. "My gym earbuds" / "Car Bluetooth" / "Late-night quiet" are
// the kind of names this is actually for.
@Composable
private fun PresetsSection(controller: AudioFxController) {
    var newPresetName by remember { mutableStateOf("") }

    AmberSectionPill("Presets")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = newPresetName,
            onValueChange = { newPresetName = it },
            placeholder = { Text("Preset name", fontSize = 10.sp, color = TextFaint) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp, color = TextBright),
            modifier = Modifier.weight(1f).height(52.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AmberCore.copy(alpha = 0.6f),
                unfocusedBorderColor = AmberCore.copy(alpha = 0.25f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        val onSave = {
            if (newPresetName.isNotBlank()) {
                controller.saveCurrentAsPreset(newPresetName)
                newPresetName = ""
            }
        }
        Text(
            text = "Save",
            color = Color.Black,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AmberGlow)
                .clickable(onClick = onSave)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        )
    }

    if (controller.presets.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        controller.presets.forEach { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .clickable { controller.applyPreset(preset.name) }
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text(text = preset.name, color = TextBright, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    text = "✕",
                    color = TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable(onClick = { controller.deletePreset(preset.name) })
                        .padding(6.dp)
                )
            }
        }
    }
}

// Auto-loads a saved preset the moment CineVault detects a matching
// output device connect — the actual Poweramp feature this was built
// for. currentOutputProfile shows what CineVault currently believes is
// active so the mapping below isn't a guess in the dark.
@Composable
private fun AutoSwitchSection(controller: AudioFxController) {
    AmberSectionPill("Auto-Switch by Output")
    Text(
        text = "Automatically loads a preset when a matching output connects. " +
            (controller.currentOutputProfile?.let { "Currently: ${it.label}." } ?: "No known output detected yet."),
        color = TextMuted, fontSize = 9.5.sp, lineHeight = 13.sp, modifier = Modifier.padding(bottom = 6.dp)
    )
    if (controller.presets.isEmpty()) {
        Text(text = "Save a preset above first.", color = TextFaint, fontSize = 10.sp)
        return
    }
    AudioOutputProfile.entries.forEach { profile ->
        val assigned = controller.profileAssignments[profile]
        Text(
            text = profile.label,
            color = if (profile == controller.currentOutputProfile) AmberCore else Color(0xFFC9A765),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            controller.presets.forEach { preset ->
                val selected = assigned == preset.name
                val onToggle = {
                    controller.assignPresetToProfile(profile, if (selected) null else preset.name)
                }
                Text(
                    text = preset.name,
                    color = if (selected) Color.Black else TextBright,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AmberCore else GlassSurface)
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

// Renders whatever the controller's Visualizer last captured — real data
// from the actual audio stream, not simulated/decorative bars. Android's
// Visualizer.getFft() output is packed as: byte[0] = DC (real) component,
// byte[1] = Nyquist (real) component, then alternating (real, imaginary)
// pairs for each subsequent bin — magnitude per bin is sqrt(re² + im²),
// the standard way to turn that packed format into bar heights.
@Composable
private fun SpectrumVisualizer(fft: ByteArray, waveform: ByteArray) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(6.dp)
    ) {
        if (fft.size < 4) {
            Text(
                text = "Waiting for playback…",
                color = TextFaint,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val binCount = (fft.size / 2) - 1
            if (binCount <= 0) return@Canvas
            val barWidth = size.width / binCount
            for (bin in 0 until binCount) {
                val reIndex = (bin + 1) * 2
                val imIndex = reIndex + 1
                if (imIndex >= fft.size) break
                val re = fft[reIndex].toInt()
                val im = fft[imIndex].toInt()
                val magnitude = kotlin.math.sqrt((re * re + im * im).toFloat())
                // Raw magnitude from an 8-bit-per-component capture tops
                // out low; scaled and clamped to fill the bar height
                // reasonably rather than rendering near-invisible slivers.
                val normalized = (magnitude / 40f).coerceIn(0f, 1f)
                val barHeight = size.height * normalized
                drawRect(
                    color = AmberGlow.copy(alpha = 0.85f),
                    topLeft = Offset(bin * barWidth, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth * 0.7f, barHeight)
                )
            }
        }
    }
}

@Composable
private fun EqBandRow(label: String, valueDb: Int, rangeDb: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = TextMuted, fontSize = 9.5.sp, modifier = Modifier.width(42.dp))
        Slider(
            value = valueDb.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = rangeDb.first.toFloat()..rangeDb.last.toFloat(),
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = GlassSurface)
        )
        Text(
            text = "${if (valueDb >= 0) "+" else ""}$valueDb",
            color = AmberCore, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp), textAlign = TextAlign.End
        )
    }
}

@Composable
private fun FxSliderRow(valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = GlassSurface)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = valueText, color = AmberCore, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

// "31" -> "31", "1000" -> "1k", "16000" -> "16k" — compact labels since
// this column is narrow; matches the abbreviation convention used
// elsewhere for tight-space frequency/size labels in this app.
private fun formatFreqLabel(hz: Int): String =
    if (hz >= 1000) {
        val khz = hz / 1000f
        if (khz == khz.toInt().toFloat()) "${khz.toInt()}k" else "${"%.1f".format(khz)}k"
    } else {
        "$hz"
    }

