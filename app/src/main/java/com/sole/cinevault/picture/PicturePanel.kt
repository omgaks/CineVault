package com.sole.cinevault.picture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassSurfaceStrong
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class PicturePage { MAIN, FINE }

@Composable
fun PicturePanelHost(controller: PictureEnhanceController) {
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
        delay(450)
        controller.refreshFrame()
    }
    if (!controller.panelOpen && !controller.splitView && !controller.comparing) return

    var page by remember { mutableStateOf(PicturePage.MAIN) }
    var panelOffset by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    fun closePanelOnly() {
        // Closing settings must not cancel the explicit Split viewing mode.
        controller.panelOpen = false
    }

    BackHandler(enabled = controller.panelOpen) {
        if (page == PicturePage.FINE) page = PicturePage.MAIN else closePanelOnly()
    }

    LaunchedEffect(controller) {
        while (true) {
            controller.refreshActivity()
            delay(700)
        }
    }

    val panelAlpha by animateFloatAsState(
        targetValue = if (controller.comparing) 0.08f else 1f,
        animationSpec = tween(120),
        label = "picturePanelAlpha",
    )
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }

        // Background dismissal stays available during Split View. The old full-screen split
        // drag detector sat above this layer and swallowed empty-space taps.
        if (controller.panelOpen) {
            Box(
                Modifier.fillMaxSize().pointerInput(controller.panelOpen) {
                    detectTapGestures { closePanelOnly() }
                }
            )
        }

        if (controller.splitView && !controller.comparing) {
            SplitViewOverlay(
                position = controller.splitPosition,
                containerW = containerW,
                onDrag = { delta ->
                    controller.moveSplit(controller.splitPosition + delta / containerW)
                },
            )
        }

        if (controller.panelOpen) {
            PicturePanel(
                controller = controller,
                page = page,
                onPage = { page = it },
                maxPanelHeight = maxHeight - 32.dp,
                dragHandle = Modifier.pointerInput(containerW, containerH, panelSize) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val minX = -(containerW - panelSize.width - marginPx).coerceAtLeast(0f)
                        val maxY = ((containerH - panelSize.height) / 2f).coerceAtLeast(0f)
                        panelOffset = Offset(
                            (panelOffset.x + drag.x).coerceIn(minX, marginPx),
                            (panelOffset.y + drag.y).coerceIn(-maxY, maxY),
                        )
                    }
                },
                onClose = { closePanelOnly() },
                onToggleSplit = { controller.toggleSplit() },
                onResetAll = { controller.resetAll() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .offset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) }
                    .onSizeChanged { panelSize = it }
                    .graphicsLayer(alpha = panelAlpha),
            )
        }

        if (controller.comparing) {
            Text(
                "ORIGINAL",
                color = TextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
                    .glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.SplitViewOverlay(
    position: Float,
    containerW: Float,
    onDrag: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val dividerX = containerW * position
    SplitLabel(
        "LIVE · ENHANCED", true,
        Modifier.align(Alignment.TopStart).padding(top = 28.dp)
            .offset { IntOffset((dividerX * .5f).roundToInt() - with(density) { 44.dp.roundToPx() }, 0) }
    )
    SplitLabel(
        "ORIGINAL", false,
        Modifier.align(Alignment.TopStart).padding(top = 28.dp)
            .offset { IntOffset(((dividerX + containerW) * .5f).roundToInt() - with(density) { 36.dp.roundToPx() }, 0) }
    )

    // Compose owns the visible divider. It follows the finger immediately even when
    // playback is paused and Media3 is not submitting a new frame to the shader.
    Box(
        Modifier.align(Alignment.CenterStart)
            .offset { IntOffset(dividerX.roundToInt(), 0) }
            .width(1.5.dp)
            .fillMaxHeight()
            .background(AmberCore)
    )

    // Only the divider owns the split drag gesture. Empty player space remains tappable.
    Box(
        Modifier.align(Alignment.CenterStart)
            .offset { IntOffset(dividerX.roundToInt() - with(density) { 24.dp.roundToPx() }, 0) }
            .width(48.dp)
            .fillMaxHeight()
            .pointerInput(containerW) {
                detectHorizontalDragGestures { change, delta ->
                    change.consume()
                    onDrag(delta)
                }
            },
    )

    Box(
        Modifier.align(Alignment.CenterStart)
            .offset { IntOffset(dividerX.roundToInt() - with(density) { 16.dp.roundToPx() }, 0) }
            .size(32.dp).clip(CircleShape).background(AmberCore)
            .border(2.dp, Color(0xFF1A1206), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("↔", color = Color(0xFF1A1206), fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SplitLabel(text: String, highlighted: Boolean, modifier: Modifier) {
    Text(
        text, color = if (highlighted) AmberCore else TextBright,
        fontSize = 10.5.sp, fontWeight = FontWeight.Black,
        modifier = modifier.glassPanel(cornerRadius = 50.dp, fill = GlassSurfaceStrong)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun PicturePanel(
    controller: PictureEnhanceController,
    page: PicturePage,
    onPage: (PicturePage) -> Unit,
    maxPanelHeight: Dp,
    dragHandle: Modifier,
    onClose: () -> Unit,
    onToggleSplit: () -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = controller.settings
    val available = controller.availability is PictureAvailability.Available
    val on = settings.preset != PicturePreset.OFF

    Column(
        modifier.widthIn(min = 292.dp, max = 360.dp).heightIn(max = maxPanelHeight)
            .glassPanel(cornerRadius = 24.dp, fill = GlassSurfaceStrong.copy(alpha = .90f))
            .border(1.dp, AmberCore.copy(alpha = .24f), RoundedCornerShape(24.dp))
            .blockTaps().verticalScroll(rememberScrollState()).padding(14.dp)
    ) {
        if (page == PicturePage.MAIN) {
            MainPage(
                controller, settings, available, on, dragHandle,
                onClose, onToggleSplit, onResetAll
            ) { onPage(PicturePage.FINE) }
        } else {
            FinePage(controller, settings, available && on, dragHandle) { onPage(PicturePage.MAIN) }
        }
    }
}

@Composable
private fun MainPage(
    controller: PictureEnhanceController,
    settings: PictureSettings,
    available: Boolean,
    on: Boolean,
    dragHandle: Modifier,
    onClose: () -> Unit,
    onToggleSplit: () -> Unit,
    onResetAll: () -> Unit,
    onFine: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).then(dragHandle)) {
            Text("PICTURE", color = AmberCore, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("ENHANCEMENT ENGINE", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
        }
    }

    val unavailable = controller.availability as? PictureAvailability.Unavailable
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape)
                .background(if (controller.statusLive) AmberCore else TextMuted.copy(alpha = .5f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            controller.statusLine,
            color = if (controller.statusLive) AmberCore else TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (settings.content == PictureContent.AUTO) "AUTO · ${controller.detected.label.uppercase()}"
            else settings.content.label.uppercase(),
            color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold,
        )
    }

    val message = unavailable?.reason ?: controller.note
    if (message != null) {
        Text(message, color = AmberCore, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
    controller.lastError?.let {
        Text("Reason: $it", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
    }

    Spacer(Modifier.height(10.dp))
    PictureMasterPill(on, available) { controller.setEnabled(it) }

    val selectedFamily = PictureExperienceBridge.selectedFamily(settings)
    val sourceProfile = remember(controller.detected) {
        PictureSourceProfile(
            content = controller.detected,
            resolution = PictureSourceResolution.UNKNOWN,
            quality = PictureSourceQuality.UNKNOWN,
            scalingNeed = PictureSourceScalingNeed.UNKNOWN,
        )
    }
    val recommendation = PictureExperienceRecommender.recommend(
        sourceProfile,
        PictureExperienceBridge.qualityFor(settings),
    )

    SectionLabel("EXPERIENCE")
    PictureSourceBadgeView(sourceProfile.toSourceBadge(), Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    PictureExperienceGallery(
        cards = PictureExperiencePresentation.cards(selectedFamily, recommendation),
        enabled = available,
        onSelect = { family ->
            controller.setContent(PictureExperienceBridge.contentFor(family))
            if (!on) controller.setEnabled(true)
        },
    )

    SectionLabel("QUALITY")
    PictureQualityRail(
        choices = PictureQualityPresentation.choices(PictureExperienceBridge.qualityFor(settings)),
        enabled = available && on,
        onSelect = { tier ->
            controller.setIntensity(PictureExperienceBridge.intensityFor(tier))
            controller.commit()
        },
    )

    SectionLabel("LOOK")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(PicturePreset.NATURAL, PicturePreset.CINEMA, PicturePreset.VIVID, PicturePreset.SHARP)
            .forEach { preset ->
                Chip(
                    preset.label, settings.preset == preset, available,
                    Modifier.weight(1f)
                ) { controller.selectPreset(preset) }
            }
    }

    SectionLabel("INTENSITY · ${(settings.intensity * 100).toInt()}%")
    PictureSlider(
        settings.intensity, available && on,
        { controller.setIntensity(it) }, { controller.commit() }
    )

    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Chip("Fine Tune  ›", false, available && on, Modifier.weight(1f), onFine)
        Chip("Split", controller.splitView, available && on, Modifier.weight(1f), onToggleSplit)
        Chip("Reset", false, available, Modifier.weight(1f), onResetAll)
    }

    Spacer(Modifier.height(12.dp))
    HoldToCompareButton(available && on, controller)
}

@Composable
private fun FinePage(
    controller: PictureEnhanceController,
    settings: PictureSettings,
    enabled: Boolean,
    dragHandle: Modifier,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back", tint = AmberCore)
        }
        TitlePill("FINE TUNE", Modifier.weight(1f).then(dragHandle))
        Spacer(Modifier.width(8.dp))
        Chip("Reset", false, enabled, Modifier.width(72.dp)) { controller.resetFineTune() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (controller.statusLive) AmberCore else TextMuted.copy(alpha = .5f))
        )
        Spacer(Modifier.width(8.dp))
        Text(controller.statusLine, color = if (controller.statusLive) AmberCore else TextMuted, fontSize = 11.sp)
    }
    LabeledSlider("Sharpness", settings.sharpen, enabled,
        { controller.setFineTune(sharpen = it) }, { controller.commit() })
    LabeledSlider("Smooth gradients", settings.deband, enabled,
        { controller.setFineTune(deband = it) }, { controller.commit() })
    LabeledSlider("Colour", settings.colour, enabled,
        { controller.setFineTune(colour = it) }, { controller.commit() })
    LabeledSlider("Film grain", settings.grain, enabled,
        { controller.setFineTune(grain = it) }, { controller.commit() })
    Spacer(Modifier.height(14.dp))
    HoldToCompareButton(enabled, controller)
}

@Composable
private fun TitlePill(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(AmberCore.copy(alpha = .12f))
            .border(1.dp, AmberCore.copy(alpha = .30f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(Icons.Rounded.Tune, null, tint = AmberCore, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
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
        modifier = modifier.height(34.dp).clip(shape)
            .background(if (selected) AmberGlow.copy(alpha = .26f) else Color.Transparent)
            .border(1.dp, if (selected) AmberCore.copy(alpha = .85f) else Color.White.copy(alpha = .16f), shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = when {
                !enabled -> TextMuted.copy(alpha = .5f)
                selected -> AmberCore
                else -> TextBright
            },
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PictureMasterPill(enabled: Boolean, usable: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    val dark = Color(0xFF1A1206)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = if (usable) 1f else .5f)
            .clip(shape)
            .background(if (enabled) AmberGlow.copy(alpha = .26f) else Color.Transparent)
            .border(1.dp, if (enabled) AmberCore.copy(alpha = .85f) else Color.White.copy(alpha = .18f), shape)
            .clickable(enabled = usable) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Picture Enhance", color = if (enabled) AmberCore else TextMuted,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (enabled) "Processing live" else "Original picture",
                color = TextMuted, fontSize = 8.sp)
        }
        Box(
            Modifier.width(42.dp).height(24.dp).clip(shape)
                .background(if (enabled) AmberCore else Color.Transparent)
                .border(1.5.dp, if (enabled) AmberCore else TextMuted.copy(alpha = .6f), shape)
                .padding(3.dp)
        ) {
            Box(
                Modifier.size(16.dp).align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(if (enabled) dark else Color.Transparent)
                    .border(1.5.dp, if (enabled) dark else TextMuted.copy(alpha = .6f), CircleShape)
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
            value, onChange, Modifier.weight(1f), enabled,
            onValueChangeFinished = onFinished, valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AmberCore, activeTrackColor = AmberGlow,
                inactiveTrackColor = Color.White.copy(alpha = .15f),
                disabledThumbColor = TextMuted.copy(alpha = .5f),
                disabledActiveTrackColor = Color.White.copy(alpha = .18f),
                disabledInactiveTrackColor = Color.White.copy(alpha = .10f),
            ),
        )
        Text("${(value * 100).toInt()}%", color = if (enabled) AmberCore else TextMuted,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, enabled: Boolean,
    onChange: (Float) -> Unit, onFinished: () -> Unit,
) {
    Text(label, color = TextMuted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 10.dp))
    PictureSlider(value, enabled, onChange, onFinished)
}

@Composable
private fun HoldToCompareButton(enabled: Boolean, controller: PictureEnhanceController) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(46.dp)
            .graphicsLayer(alpha = if (enabled) 1f else .45f)
            .clip(shape)
            .background(if (controller.comparing) AmberCore else Color.Transparent)
            .border(1.2.dp, AmberCore.copy(alpha = .75f), shape)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(onPress = {
                        controller.holdCompare(true)
                        tryAwaitRelease()
                        controller.holdCompare(false)
                    })
                }
            },
    ) {
        Text(
            if (controller.comparing) "SHOWING ORIGINAL" else "HOLD TO COMPARE",
            color = if (controller.comparing) Color.Black else AmberCore,
            fontSize = 12.sp, fontWeight = FontWeight.Black,
        )
    }
}

private fun Modifier.blockTaps(): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
    )
}
