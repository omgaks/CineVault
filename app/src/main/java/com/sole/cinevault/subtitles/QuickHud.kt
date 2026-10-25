package com.sole.cinevault.subtitles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.LumenActionButton
import com.sole.cinevault.LumenChip
import com.sole.cinevault.LumenGhostScope
import com.sole.cinevault.LumenGold
import com.sole.cinevault.LumenHoldButton
import com.sole.cinevault.LumenMint
import com.sole.cinevault.LumenRailState
import com.sole.cinevault.LumenRose
import com.sole.cinevault.LumenSectionLabel
import com.sole.cinevault.LumenSky
import com.sole.cinevault.LumenSlider
import com.sole.cinevault.LumenStepper
import com.sole.cinevault.LumenSwitch
import com.sole.cinevault.LumenTab
import com.sole.cinevault.LumenTabStrip
import com.sole.cinevault.LumenType
import com.sole.cinevault.LUMEN_GHOST_ALPHA
import com.sole.cinevault.lumenFrame
import com.sole.cinevault.rememberLumenGhost
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import kotlin.math.roundToInt

/** The six tabs of the Sub Deck. */
enum class DeckTab(val label: String) { TRACKS("Tracks"), FIND("Find"), SYNC("Sync"), STYLE("Style"), AI("AI"), SETTINGS("Settings") }

private val deckTabs = listOf(
    LumenTab("Tracks", Icons.Rounded.List),
    LumenTab("Find", Icons.Rounded.Search),
    LumenTab("Sync", Icons.Rounded.Schedule),
    LumenTab("Style", Icons.Rounded.Palette),
    LumenTab("AI", Icons.Rounded.AutoAwesome),
    LumenTab("Settings", Icons.Rounded.Tune),
)

private val delayStepChoices = listOf(0.01f, 0.1f, 0.5f, 1f)

private val positionPresetsHud = listOf(
    "Bottom" to 0.02f,
    "Above controls" to 0.16f,
    "Centre" to 0.45f,
    "Top" to 0.85f,
)

/**
 * The Sub Deck (the Tap-CC window): one Lumen window with six tabs that holds everything the
 * old Quick HUD, the Download / Power tools lists and the standalone subtitle windows did.
 * Delay, Size and Position each have big − / + steppers (hold to repeat) plus a live slider.
 * Heavier tools (track list, search, dual subs, looks, behaviour, AI panels) open from here
 * and are moved inside the Deck step by step.
 */
@Composable
fun QuickHud(
    subtitleFileName: String?,
    delaySeconds: Float,
    onDelayChange: (Float) -> Unit,
    speechTimeline: FloatArray?,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    onReset: () -> Unit,
    onTracks: (() -> Unit)? = null,
    onAutoSync: (() -> Unit)? = null,
    onSmartSearch: (() -> Unit)? = null,
    containerSize: IntSize,
    initialOffset: Offset,
    windowWidth: Dp = 264.dp,
    modifier: Modifier = Modifier,
    autoSyncStatus: AutoSyncStatus = AutoSyncStatus.Idle,
    onManageDownloads: (() -> Unit)? = null,
    onWebSearch: (() -> Unit)? = null,
    onSpeechToSubs: (() -> Unit)? = null,
    onTranslate: (() -> Unit)? = null,
    onDialogueSync: (() -> Unit)? = null,
    onDriftSync: (() -> Unit)? = null,
    onDualSubs: (() -> Unit)? = null,
    onBehaviour: (() -> Unit)? = null,
    onMoreLooks: (() -> Unit)? = null,
    autoDownload: Boolean = false,
    onAutoDownloadChange: ((Boolean) -> Unit)? = null,
    initialTab: DeckTab = DeckTab.SYNC,
) {
    val density = LocalDensity.current
    var tab by remember { mutableStateOf(initialTab) }
    var delayStep by remember { mutableStateOf(0.1f) }
    val (ghost, ghostAlpha) = rememberLumenGhost()

    val maxHeight = with(density) { containerSize.height.toDp() } - LumenRailState.bottomInset - 12.dp

    DraggableStudioWindow(
        initialOffset = initialOffset,
        containerSize = containerSize,
        modifier = modifier,
    ) { dragHandleModifier ->
        LumenGhostScope(ghost) {
            Box {
                Column(
                    modifier = Modifier
                        .width(windowWidth)
                        .heightIn(max = maxHeight.coerceAtLeast(200.dp))
                        .graphicsLayer { alpha = ghostAlpha }
                        .lumenFrame(LumenGold)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Header: drag handle, title and the file that is showing.
                    Row(
                        modifier = Modifier.fillMaxWidth().then(dragHandleModifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to move", tint = TextFaint, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "SUB DECK",
                            color = LumenGold,
                            fontSize = LumenType.title,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = subtitleFileName ?: "No subtitle loaded",
                            color = TextMuted,
                            fontSize = LumenType.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LumenTabStrip(
                        tabs = deckTabs,
                        selected = tab.ordinal,
                        accent = LumenGold,
                        iconOnly = windowWidth < 300.dp,
                        onSelect = { tab = DeckTab.values()[it] },
                    )
                    Spacer(Modifier.height(10.dp))

                    Crossfade(
                        targetState = tab,
                        animationSpec = tween(160),
                        modifier = Modifier.weight(1f, fill = false),
                        label = "deckTab",
                    ) { current ->
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            when (current) {
                                DeckTab.TRACKS -> TracksPage(subtitleFileName, onTracks, onManageDownloads, onDualSubs)
                                DeckTab.FIND -> FindPage(onSmartSearch, onWebSearch, autoDownload, onAutoDownloadChange)
                                DeckTab.SYNC -> SyncPage(
                                    delaySeconds = delaySeconds,
                                    onDelayChange = onDelayChange,
                                    delayStep = delayStep,
                                    onDelayStepChange = { delayStep = it },
                                    speechTimeline = speechTimeline,
                                    autoSyncStatus = autoSyncStatus,
                                    onAutoSync = onAutoSync,
                                    onDialogueSync = onDialogueSync,
                                    onDriftSync = onDriftSync,
                                    onBubble = { ghost.bubble = it },
                                )
                                DeckTab.STYLE -> StylePage(
                                    fontSizeSp = fontSizeSp,
                                    onFontSizeChange = onFontSizeChange,
                                    bottomPadding = bottomPadding,
                                    onBottomPaddingChange = onBottomPaddingChange,
                                    onMoreLooks = onMoreLooks,
                                    onBubble = { ghost.bubble = it },
                                )
                                DeckTab.AI -> AiPage(onSpeechToSubs, onTranslate)
                                DeckTab.SETTINGS -> SettingsPage(onBehaviour, onReset)
                            }
                        }
                    }
                }

                // Pop-up number over the picture while a control is being adjusted.
                val bubble = ghost.bubble
                if (ghost.active && bubble != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (-104).dp)
                            .width(92.dp)
                            .lumenFrame(LumenGold)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(bubble, color = LumenGold, fontSize = LumenType.value, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TracksPage(
    fileName: String?,
    onTracks: (() -> Unit)?,
    onManage: (() -> Unit)?,
    onDual: (() -> Unit)?,
) {
    LumenSectionLabel("Now showing", LumenGold)
    Text(
        text = fileName ?: "No subtitle loaded",
        color = TextBright,
        fontSize = LumenType.body,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(10.dp))
    if (onTracks != null) LumenActionButton("Choose track", LumenGold) { onTracks() }
    Spacer(Modifier.height(8.dp))
    if (onManage != null) LumenActionButton("Downloaded files", LumenMint) { onManage() }
    Spacer(Modifier.height(8.dp))
    if (onDual != null) LumenActionButton("Dual subtitles", LumenSky) { onDual() }
}

@Composable
private fun FindPage(
    onSmartSearch: (() -> Unit)?,
    onWeb: (() -> Unit)?,
    autoDownload: Boolean,
    onAutoDownloadChange: ((Boolean) -> Unit)?,
) {
    if (onSmartSearch != null) LumenActionButton("Smart search", LumenGold) { onSmartSearch() }
    Text(
        "Matches by file fingerprint first, so timing usually fits straight away.",
        color = TextMuted,
        fontSize = LumenType.caption,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    if (onWeb != null) LumenActionButton("Search the web", LumenSky) { onWeb() }
    if (onAutoDownloadChange != null) {
        LumenSectionLabel("Automatic", LumenGold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Download when none found", color = TextBright, fontSize = LumenType.body, modifier = Modifier.weight(1f))
            LumenSwitch(autoDownload, onAutoDownloadChange, LumenGold)
        }
    }
}

@Composable
private fun SyncPage(
    delaySeconds: Float,
    onDelayChange: (Float) -> Unit,
    delayStep: Float,
    onDelayStepChange: (Float) -> Unit,
    speechTimeline: FloatArray?,
    autoSyncStatus: AutoSyncStatus,
    onAutoSync: (() -> Unit)?,
    onDialogueSync: (() -> Unit)?,
    onDriftSync: (() -> Unit)?,
    onBubble: (String) -> Unit,
) {
    fun setDelay(v: Float) {
        val clamped = (((v * 100f).roundToInt()) / 100f).coerceIn(-10f, 10f)
        onDelayChange(clamped)
        onBubble(formatDelay(clamped) + " s")
    }
    LumenStepper(
        label = "Delay",
        valueText = formatDelay(delaySeconds),
        unit = "s",
        accent = LumenGold,
        onStep = { n -> setDelay(delaySeconds + n * delayStep) },
    )
    Spacer(Modifier.height(4.dp))
    WaveformSlider(
        value = delaySeconds.coerceIn(-10f, 10f),
        onValueChange = { setDelay(it) },
        valueRange = -10f..10f,
        speechTimeline = speechTimeline,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Step", color = TextMuted, fontSize = LumenType.caption)
        delayStepChoices.forEach { s ->
            LumenChip(formatStep(s), delayStep == s, LumenMint) { onDelayStepChange(s) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LumenChip("Zero", false, LumenRose) { setDelay(0f) }
    }
    Text(
        "Hold − or + to repeat. It speeds up the longer you hold.",
        color = TextMuted,
        fontSize = LumenType.caption,
        modifier = Modifier.padding(top = 6.dp),
    )

    LumenSectionLabel("Auto-sync", LumenGold)
    AutoSyncCard(autoSyncStatus, onAutoSync)
    Spacer(Modifier.height(8.dp))
    if (onDialogueSync != null) LumenActionButton("Dialogue sync", LumenSky) { onDialogueSync() }
    Spacer(Modifier.height(8.dp))
    if (onDriftSync != null) LumenActionButton("Drift sync", LumenSky) { onDriftSync() }
}

@Composable
private fun AutoSyncCard(status: AutoSyncStatus, onAutoSync: (() -> Unit)?) {
    when (status) {
        is AutoSyncStatus.Analyzing -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LumenGold.copy(alpha = 0.08f))
                    .border(1.5.dp, LumenGold)
                    .padding(10.dp)
            ) {
                Text(status.stage.ifBlank { "Listening to the film" }, color = LumenGold, fontSize = LumenType.body, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                MarchingBar(LumenGold)
                Spacer(Modifier.height(6.dp))
                Text("Safe to close. A pill on the right keeps the job.", color = TextMuted, fontSize = LumenType.caption)
            }
        }
        is AutoSyncStatus.Failed -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LumenRose.copy(alpha = 0.08f))
                    .border(1.5.dp, LumenRose)
                    .padding(10.dp)
            ) {
                Text("Couldn't sync", color = LumenRose, fontSize = LumenType.body, fontWeight = FontWeight.Bold)
                Text(status.reason, color = TextBright, fontSize = LumenType.caption, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (onAutoSync != null) LumenActionButton("Try again", LumenGold) { onAutoSync() }
        }
        else -> {
            if (onAutoSync != null) LumenActionButton("Start auto-sync", LumenGold) { onAutoSync() }
            Text(
                "Listens at several points in the film and scores the best fit, so slow drift is caught too.",
                color = TextMuted,
                fontSize = LumenType.caption,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun MarchingBar(accent: Color) {
    val phase by com.sole.cinevault.rememberLoop(
        enabled = com.sole.cinevault.rememberMotionEnabled(),
        from = 0f,
        to = 24f,
        periodMs = 800,
    )
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        var x = -24f + phase
        val dash = 12.dp.toPx()
        while (x < size.width) {
            val start = x.coerceAtLeast(0f)
            val end = (x + dash).coerceAtMost(size.width)
            if (end > start) {
                drawRect(accent.copy(alpha = 0.9f), topLeft = Offset(start, 0f), size = androidx.compose.ui.geometry.Size(end - start, size.height))
            }
            x += dash * 2
        }
    }
}

@Composable
private fun StylePage(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    onMoreLooks: (() -> Unit)?,
    onBubble: (String) -> Unit,
) {
    fun setSize(v: Float) {
        val c = v.roundToInt().coerceIn(12, 32).toFloat()
        onFontSizeChange(c)
        onBubble("${c.toInt()} sp")
    }
    fun setPos(v: Float) {
        val c = (((v * 100f).roundToInt()) / 100f).coerceIn(0.02f, 0.90f)
        onBottomPaddingChange(c)
        onBubble("${(c * 100).roundToInt()} % up")
    }

    LumenStepper(
        label = "Size",
        valueText = fontSizeSp.roundToInt().toString(),
        unit = "sp",
        accent = LumenMint,
        onStep = { n -> setSize(fontSizeSp + n) },
    )
    LumenSlider(
        value = fontSizeSp,
        onValueChange = { setSize(it) },
        valueRange = 12f..32f,
        accent = LumenMint,
        modifier = Modifier.fillMaxWidth(),
    )

    LumenStepper(
        label = "Position",
        valueText = (bottomPadding * 100).roundToInt().toString(),
        unit = "% up",
        accent = LumenSky,
        onStep = { n -> setPos(bottomPadding + n * 0.01f) },
    )
    LumenSlider(
        value = bottomPadding,
        onValueChange = { setPos(it) },
        valueRange = 0.02f..0.90f,
        accent = LumenSky,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        positionPresetsHud.forEach { (label, value) ->
            LumenChip(label, kotlin.math.abs(bottomPadding - value) < 0.005f, LumenSky) { setPos(value) }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (onMoreLooks != null) LumenActionButton("Colours and background", LumenGold) { onMoreLooks() }
}

@Composable
private fun AiPage(onSpeech: (() -> Unit)?, onTranslate: (() -> Unit)?) {
    LumenSectionLabel("Speech to subtitles", LumenGold)
    Text("Writes subtitles from the film's audio with Whisper on this device.", color = TextMuted, fontSize = LumenType.caption)
    Spacer(Modifier.height(8.dp))
    if (onSpeech != null) LumenActionButton("Create from audio", LumenGold) { onSpeech() }
    LumenSectionLabel("Translate", LumenSky)
    Text("Translates the current track into another language.", color = TextMuted, fontSize = LumenType.caption)
    Spacer(Modifier.height(8.dp))
    if (onTranslate != null) LumenActionButton("Translate track", LumenSky) { onTranslate() }
    Text("Both run on this device. Nothing is uploaded.", color = TextMuted, fontSize = LumenType.caption, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingsPage(onBehaviour: (() -> Unit)?, onReset: () -> Unit) {
    if (onBehaviour != null) {
        LumenActionButton("Behaviour and cleaning", LumenGold) { onBehaviour() }
    }
    LumenSectionLabel("Reset", LumenRose)
    var armed by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (armed) "Armed. Hold the button to reset." else "Reset all subtitle settings",
            color = if (armed) LumenRose else TextBright,
            fontSize = LumenType.body,
            modifier = Modifier.weight(1f),
        )
        LumenSwitch(armed, { armed = it }, LumenRose)
    }
    if (armed) {
        Spacer(Modifier.height(8.dp))
        LumenHoldButton("Hold to reset all", LumenRose) {
            onReset()
            armed = false
        }
        Text(
            "Clears style, position, delay and remembered choices. Downloaded files stay.",
            color = TextMuted,
            fontSize = LumenType.caption,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun formatDelay(seconds: Float): String =
    (if (seconds >= 0f) "+" else "−") + "%.2f".format(kotlin.math.abs(seconds))

private fun formatStep(step: Float): String =
    if (step < 0.1f) "0.01" else if (step == 0.1f) "0.1" else if (step == 0.5f) "0.5" else "1"
