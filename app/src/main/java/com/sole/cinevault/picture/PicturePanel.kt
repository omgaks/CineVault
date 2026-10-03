package com.sole.cinevault.picture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel

/**
 * Hosts the Picture panel over the player. Closed = draws nothing.
 * Tap outside closes it; the system Back button closes it too.
 */
@Composable
fun PicturePanelHost(controller: PictureEnhanceController) {
    if (!controller.panelOpen) return

    BackHandler(enabled = true) { controller.closePanel() }

    // While "Hold to compare" is pressed the panel fades away so the picture is visible.
    val panelAlpha by animateFloatAsState(
        targetValue = if (controller.comparing) 0.08f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "picturePanelAlpha",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controller.closePanel() } }
        )

        PicturePanel(
            controller = controller,
            maxPanelHeight = maxHeight - 32.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .graphicsLayer(alpha = panelAlpha),
        )

        if (controller.comparing) {
            Text(
                text = "ORIGINAL",
                color = TextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun PicturePanel(
    controller: PictureEnhanceController,
    maxPanelHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val settings = controller.settings
    val available = controller.availability is PictureAvailability.Available
    val on = settings.preset != PicturePreset.OFF
    var fineTuneOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .heightIn(max = maxPanelHeight)
            .glassPanel(cornerRadius = 22.dp, fill = GlassSurfaceStrong.copy(alpha = 0.88f))
            .border(1.dp, AmberCore.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            // Swallow taps on empty panel areas so they don't reach the "tap outside" layer.
            .blockTaps()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(AmberCore.copy(alpha = 0.12f))
                    .border(1.dp, AmberCore.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = AmberCore, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("PICTURE", color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = { controller.closePanel() }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
            }
        }

        // Status
        val unavailable = controller.availability as? PictureAvailability.Unavailable
        val statusText = when {
            unavailable != null -> unavailable.reason
            controller.note != null -> controller.note.orEmpty()
            else -> "Detected: ${controller.detected.label}"
        }
        Text(
            text = statusText,
            color = if (unavailable != null || controller.note != null) AmberCore else TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )

        // One pill, like the Subtitles pill: amber glow + filled switch when on.
        PictureMasterPill(
            enabled = on,
            usable = available,
            onToggle = { controller.setEnabled(it) },
        )

        SectionLabel("LOOK")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                PicturePreset.NATURAL,
                PicturePreset.CINEMA,
                PicturePreset.VIVID,
                PicturePreset.SHARP,
            ).forEach { preset ->
                Chip(
                    label = preset.label,
                    selected = settings.preset == preset,
                    enabled = available,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.selectPreset(preset) },
                )
            }
        }

        SectionLabel("CONTENT")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PictureContent.values().forEach { content ->
                Chip(
                    label = content.label,
                    selected = settings.content == content,
                    enabled = available,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.setContent(content) },
                )
            }
        }

        SectionLabel("INTENSITY")
        PictureSlider(
            value = settings.intensity,
            enabled = available && on,
            onChange = { controller.setIntensity(it) },
            onFinished = { controller.commit() },
        )

        // Fine-tune
        Text(
            text = if (fineTuneOpen) "FINE-TUNE  ▴" else "FINE-TUNE  ▾",
            color = AmberCore,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50))
                .clickable { fineTuneOpen = !fineTuneOpen }
                .padding(vertical = 4.dp),
        )
        if (fineTuneOpen) {
            val fineEnabled = available && on
            LabeledSlider("Sharpness", settings.sharpen, fineEnabled,
                { controller.setFineTune(sharpen = it) }, { controller.commit() })
            LabeledSlider("Smooth gradients", settings.deband, fineEnabled,
                { controller.setFineTune(deband = it) }, { controller.commit() })
            LabeledSlider("Colour", settings.colour, fineEnabled,
                { controller.setFineTune(colour = it) }, { controller.commit() })
            LabeledSlider("Film grain", settings.grain, fineEnabled,
                { controller.setFineTune(grain = it) }, { controller.commit() })
        }

        Spacer(Modifier.height(14.dp))
        HoldToCompareButton(enabled = available && on, controller = controller)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = 0.26f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) AmberCore.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> TextMuted.copy(alpha = 0.5f)
                selected -> AmberCore
                else -> TextBright
            },
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Same look as the Subtitles pill: amber glow + filled switch when on, empty when off. */
@Composable
private fun PictureMasterPill(enabled: Boolean, usable: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val dark = Color(0xFF1A1206)
    val alpha = if (usable) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = alpha)
            .clip(shape)
            .background(if (enabled) AmberGlow.copy(alpha = 0.26f) else Color.Transparent)
            .border(
                1.dp,
                if (enabled) AmberCore.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                shape,
            )
            .clickable(enabled = usable) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Text(
            text = "Picture enhance",
            color = if (enabled) AmberCore else TextMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(shape)
                .background(if (enabled) AmberCore else Color.Transparent)
                .border(1.5.dp, if (enabled) AmberCore else TextMuted.copy(alpha = 0.6f), shape)
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(if (enabled) dark else Color.Transparent)
                    .border(1.5.dp, if (enabled) dark else TextMuted.copy(alpha = 0.6f), CircleShape),
            )
        }
    }
}

@Composable
private fun PictureSlider(
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            enabled = enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AmberCore,
                activeTrackColor = AmberGlow,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                disabledThumbColor = TextMuted.copy(alpha = 0.5f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.18f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.10f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = if (enabled) AmberCore else TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    Text(
        text = label,
        color = TextMuted,
        fontSize = 10.5.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    PictureSlider(value, enabled, onChange, onFinished)
}

/** Press and hold to see the original picture; release to see the enhanced one again. */
@Composable
private fun HoldToCompareButton(enabled: Boolean, controller: PictureEnhanceController) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .graphicsLayer(alpha = if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (controller.comparing) AmberCore else Color.Transparent)
            .border(1.2.dp, AmberCore.copy(alpha = 0.75f), shape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            controller.holdCompare(true)
                            tryAwaitRelease()
                            controller.holdCompare(false)
                        },
                    )
                }
            },
    ) {
        Text(
            text = if (controller.comparing) "SHOWING ORIGINAL" else "HOLD TO COMPARE",
            color = if (controller.comparing) Color.Black else AmberCore,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Swallows taps on a panel's empty areas; buttons and scrolling inside still get the touch first. */
private fun Modifier.blockTaps(): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
    )
}
