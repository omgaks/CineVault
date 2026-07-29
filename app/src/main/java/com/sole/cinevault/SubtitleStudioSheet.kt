package com.sole.cinevault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File
import kotlin.math.roundToInt

// ── Studio tabs ───────────────────────────────────────────────────────────
// FIX (UI redesign round): was TRACK/SYNC/STYLE/POSITION/ADVANCED (5 tabs,
// with Position and Style artificially split even though the approved
// mockup's own Style panel example shows Text Size and Vertical Offset
// together in one place) — now 5 tabs matching the 6-tile Tools Grid minus
// "Find" (which isn't a Studio tab at all; it closes Studio and opens the
// separate Search sheet instead, same as it always has). Position's
// controls now live inside Appearance; Dual Subtitles — previously buried
// at the bottom of the old catch-all Advanced tab — is its own tool now.
enum class SubtitleStudioTab(val label: String, val icon: ImageVector) {
    TRACK("Tracks", Icons.Filled.ViewList),
    TIMING("Timing", Icons.Filled.Sync),
    APPEARANCE("Appearance", Icons.Filled.Palette),
    DUAL("Dual", Icons.Filled.SwapHoriz),
    BEHAVIOUR("Behaviour", Icons.Filled.Settings)
}

private val positionPresets = listOf(
    "Bottom" to 0.02f, "Above Controls" to 0.16f, "Centre" to 0.45f, "Top" to 0.85f
)

private sealed class StudioScreen {
    object Grid : StudioScreen()
    data class Tool(val tab: SubtitleStudioTab) : StudioScreen()
}

@Composable
fun SubtitleStudioSheet(
    panelWidth: Dp,
    panelMaxHeight: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    initialTab: SubtitleStudioTab?,
    onOpenSearch: () -> Unit,
    onOpenManualSearch: () -> Unit,
    embeddedTracks: List<SubtitleTrackChoice.Embedded>,
    downloadedTrack: SubtitleTrackChoice.Downloaded?,
    localFiles: List<File>,
    selectedTrackKey: String?,
    onSelectTrack: (SubtitleTrackChoice) -> Unit,
    onDeleteLocalTrack: (File) -> Unit,
    onOpenFilePicker: () -> Unit,
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onDialogueSyncClick: () -> Unit,
    onDriftFixClick: () -> Unit,
    autoSyncStatus: AutoSyncStatus,
    autoSyncAvailable: Boolean,
    onAutoSyncClick: () -> Unit,
    onApplyAutoSync: (SubtitleSyncResult) -> Unit,
    onCancelAutoSync: () -> Unit,
    presetName: String,
    appearance: SubtitleAppearance,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    onApplyPreset: (String, SubtitleAppearance) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onEdgeColorChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    isAssOrSsaFormat: Boolean = false,
    preserveOriginalStyling: Boolean = false,
    onPreserveOriginalStylingChange: (Boolean) -> Unit = {},
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    dualSubtitlesEnabled: Boolean,
    dualCanEnable: Boolean,
    dualSecondaryLanguage: String,
    dualGapLines: Int,
    dualStatusText: String,
    onToggleDual: (Boolean) -> Unit,
    onDualSecondaryLanguageChange: (String) -> Unit,
    onDualGapLinesChange: (Int) -> Unit,
    behaviorPrefs: SubtitleBehaviorPrefs,
    onBehaviorPrefsChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit = {}
) {
    var screen by remember { mutableStateOf<StudioScreen>(if (initialTab != null) StudioScreen.Tool(initialTab) else StudioScreen.Grid) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // FIX: Studio was fixed to screen-center with no way to move it — on
    // a tablet especially, that can sit over exactly the part of the
    // video you're trying to watch while adjusting something. Drag is
    // scoped to a SEPARATE dedicated handle (not the header itself),
    // since the header already has its own horizontal swipe-to-switch-
    // tabs gesture — sharing one touch zone for two different gestures
    // would mean one interpretation winning unpredictably over the other.
    // Bounds are clamped against the actual container size (passed in
    // from VideoPlayerScreen.kt) so the panel can never be dragged fully
    // off-screen with no way to reach it again.
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxOffsetXPx = with(density) { ((containerWidth - panelWidth) / 2).coerceAtLeast(0.dp).toPx() }
    val maxOffsetYPx = with(density) { ((containerHeight - panelMaxHeight) / 2).coerceAtLeast(0.dp).toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
            .width(panelWidth)
            .heightIn(max = panelMaxHeight)
            .glassPanel(cornerRadius = 26.dp, fill = SpaceMid.copy(alpha = 0.98f))
            // FIX: root containment. Whatever the exact internal cause of
            // a touch on a small child control (back arrow, close button)
            // occasionally not resolving cleanly — a hair of finger jitter
            // during a "tap" getting picked up as a micro-drag by a parent
            // gesture detector is the classic culprit — the Studio's own
            // bounds must NEVER let an unconsumed touch fall through to
            // whatever's rendered behind it, which in this app is the
            // video surface's own "tap anywhere closes the open popup"
            // handler. This absorbs anything not already claimed by a
            // child control, so a swallowed/ambiguous tap can no longer
            // closes the entire Studio as a side effect.
            .pointerInput(Unit) { detectTapGestures { } }
            // FIX (C3): every touch-DOWN anywhere inside the Studio now
            // pings onUserInteraction(), which resets the auto-close
            // countdown in VideoPlayerScreen.kt. Uses requireUnconsumed =
            // false specifically so this fires regardless of whether a
            // child control (a slider, a toggle) goes on to consume the
            // gesture for its own purpose — this only needs to know
            // SOMETHING is happening in here, not what.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserInteraction()
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            val currentScreen = screen
            // FIX: whole-header long-press-then-drag replaces both the
            // old swipe-to-switch-tabs gesture AND the small dedicated
            // drag-handle icon. detectDragGesturesAfterLongPress is a
            // standard Compose primitive built exactly for this: it only
            // starts tracking movement after the platform's real long-
            // press timeout elapses, so a normal quick tap anywhere on
            // the header — including directly on the close/back buttons —
            // never engages it at all, leaving those buttons' own
            // clickable to handle the tap normally. No more competing
            // gesture detectors on the same touch area.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                        ) { change, dragAmount ->
                            change.consume()
                            dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-maxOffsetXPx, maxOffsetXPx)
                            dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-maxOffsetYPx, maxOffsetYPx)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FIX: restyled as an amber-filled pill, matching the
                // same style language now used for the lock button
                // elsewhere in the player, instead of the previous plain
                // glass-circle treatment — carried consistently across
                // every window this round, not just here.
                Box(
                    modifier = Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(AmberCore).clickable { onDismiss() }.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = if (currentScreen is StudioScreen.Tool) currentScreen.tab.label else "Subtitle Studio",
                    color = AmberCore, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // FIX: previously only shown when entered via long-press
                // (initialTab == null) — quick-menu shortcuts (Sync/Style
                // icons) jump straight into a tool with initialTab already
                // set, which hid this arrow entirely and left the X (full
                // close) as the ONLY visible option. Now always shown when
                // inside a tool (C4). Moved to the opposite end from
                // Close per request — close on the left, back on the
                // right — and restyled as the same amber-filled pill.
                if (currentScreen is StudioScreen.Tool) {
                    Box(
                        modifier = Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(AmberCore).clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            screen = StudioScreen.Grid
                        }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to tools", tint = Color.Black, modifier = Modifier.size(18.dp))
                    }
                } else {
                    // Reserves the same width the pill would occupy when
                    // on the Grid screen (no back arrow), so the title
                    // doesn't visibly re-center/shift when navigating
                    // between Grid and a Tool.
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorderBottom)
            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tabContentWidth = maxWidth
                    val tabContentHeight = maxHeight
                    when (val s = currentScreen) {
                        is StudioScreen.Grid -> SubtitleToolsGrid(
                            onSelectTool = { tab -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); screen = StudioScreen.Tool(tab) },
                            onOpenSearch = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenSearch() },
                            onOpenManualSearch = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onOpenManualSearch() }
                        )
                        is StudioScreen.Tool -> when (s.tab) {
                            SubtitleStudioTab.TRACK -> SubtitleTrackSelectorSheet(
                                embeddedTracks = embeddedTracks,
                                downloadedTrack = downloadedTrack,
                                localFiles = localFiles,
                                selectedKey = selectedTrackKey,
                                popupWidth = tabContentWidth,
                                popupMaxHeight = tabContentHeight,
                                onSelect = onSelectTrack,
                                onDeleteLocal = onDeleteLocalTrack,
                                onOpenFilePicker = onOpenFilePicker,
                                onDismiss = {}
                            )
                            SubtitleStudioTab.TIMING -> StudioTimingTab(
                                currentSyncOffset = currentSyncOffset,
                                onSyncOffsetChange = onSyncOffsetChange,
                                onDialogueSyncClick = onDialogueSyncClick,
                                onDriftFixClick = onDriftFixClick,
                                autoSyncStatus = autoSyncStatus,
                                autoSyncAvailable = autoSyncAvailable,
                                onAutoSyncClick = onAutoSyncClick,
                                onApplyAutoSync = onApplyAutoSync,
                                onCancelAutoSync = onCancelAutoSync
                            )
                            SubtitleStudioTab.APPEARANCE -> StudioAppearanceTab(
                                presetName = presetName,
                                appearance = appearance,
                                fontSizeSp = fontSizeSp,
                                onFontSizeChange = onFontSizeChange,
                                popupWidth = tabContentWidth,
                                popupMaxHeight = tabContentHeight,
                                onApplyPreset = onApplyPreset,
                                onForegroundChange = onForegroundChange,
                                onEdgeTypeChange = onEdgeTypeChange,
                                onEdgeColorChange = onEdgeColorChange,
                                onBackgroundChange = onBackgroundChange,
                                isAssOrSsaFormat = isAssOrSsaFormat,
                                preserveOriginalStyling = preserveOriginalStyling,
                                onPreserveOriginalStylingChange = onPreserveOriginalStylingChange,
                                bottomPadding = bottomPadding,
                                onBottomPaddingChange = onBottomPaddingChange
                            )
                            SubtitleStudioTab.DUAL -> StudioDualTab(
                                dualEnabled = dualSubtitlesEnabled,
                                dualCanEnable = dualCanEnable,
                                dualSecondaryLanguage = dualSecondaryLanguage,
                                dualGapLines = dualGapLines,
                                dualStatusText = dualStatusText,
                                onToggleDual = onToggleDual,
                                onDualSecondaryLanguageChange = onDualSecondaryLanguageChange,
                                onDualGapLinesChange = onDualGapLinesChange
                            )
                            SubtitleStudioTab.BEHAVIOUR -> StudioBehaviourTab(
                                prefs = behaviorPrefs,
                                onChange = onBehaviorPrefsChange,
                                cleaningOptions = cleaningOptions,
                                onCleaningOptionsChange = onCleaningOptionsChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleToolsGrid(onSelectTool: (SubtitleStudioTab) -> Unit, onOpenSearch: () -> Unit, onOpenManualSearch: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolTile(icon = SubtitleStudioTab.TRACK.icon, label = SubtitleStudioTab.TRACK.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.TRACK) }
            // FIX (D1): "Find" -> "Auto Search" — same underlying action
            // (the API-based OpenSubtitles + SubDL search), just relabeled
            // to sit clearly alongside the new Manual Search tile below.
            ToolTile(icon = Icons.Filled.Search, label = "Auto Search", modifier = Modifier.weight(1f)) { onOpenSearch() }
            // FIX (D1): NEW — previously the website fallback only ever
            // appeared as a small link inside the empty-results state of
            // Auto Search, which is exactly why it went unnoticed. Now a
            // permanent, always-reachable tool in its own right.
            ToolTile(icon = Icons.Filled.Language, label = "Manual Search", modifier = Modifier.weight(1f)) { onOpenManualSearch() }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolTile(icon = SubtitleStudioTab.TIMING.icon, label = SubtitleStudioTab.TIMING.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.TIMING) }
            ToolTile(icon = SubtitleStudioTab.APPEARANCE.icon, label = SubtitleStudioTab.APPEARANCE.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.APPEARANCE) }
            ToolTile(icon = SubtitleStudioTab.DUAL.icon, label = SubtitleStudioTab.DUAL.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.DUAL) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolTile(icon = SubtitleStudioTab.BEHAVIOUR.icon, label = SubtitleStudioTab.BEHAVIOUR.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.BEHAVIOUR) }
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(text = "Tap a tool for more options", color = TextMuted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.5f), AmberDeep.copy(alpha = 0.2f))), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = AmberCore, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = TextBright, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StudioTimingTab(
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onDialogueSyncClick: () -> Unit,
    onDriftFixClick: () -> Unit,
    autoSyncStatus: AutoSyncStatus,
    autoSyncAvailable: Boolean,
    onAutoSyncClick: () -> Unit,
    onApplyAutoSync: (SubtitleSyncResult) -> Unit,
    onCancelAutoSync: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Auto-Sync")
        Text(
            text = "Analyzes the actual audio's speech timing against the subtitle — offline, on-device, nothing leaves your phone.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        AutoSyncSection(
            status = autoSyncStatus,
            available = autoSyncAvailable,
            onStart = onAutoSyncClick,
            onApply = onApplyAutoSync,
            onCancel = onCancelAutoSync
        )

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Delay")
        Text(
            text = if (currentSyncOffset >= 0f) "+${"%.1f".format(currentSyncOffset)}s (later)" else "${"%.1f".format(currentSyncOffset)}s (earlier)",
            color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = currentSyncOffset.coerceIn(-10f, 10f), onValueChange = onSyncOffsetChange, valueRange = -10f..10f,
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.15f))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-5f, -1f, -0.1f, 0f, 0.1f, 1f, 5f).forEach { step ->
                Text(
                    text = if (step == 0f) "Reset" else if (step > 0) "+${step}s" else "${step}s",
                    color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GlassSurface).clickable {
                        onSyncOffsetChange(if (step == 0f) 0f else (currentSyncOffset + step).coerceIn(-10f, 10f))
                    }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Dialogue Sync")
        Text(text = "Pause on a visible line, tap Start, then tap the instant you hear it spoken.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StudioActionButton(label = "Start Dialogue Sync") { onDialogueSyncClick() }

        Spacer(modifier = Modifier.height(20.dp))
        StudioSectionLabel("Progressive Drift")
        Text(text = "Fixes subtitles that start in sync but drift later/earlier as the video plays.", color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StudioActionButton(label = "Fix Gradual Drift") { onDriftFixClick() }
    }
}

@Composable
private fun StudioAppearanceTab(
    presetName: String,
    appearance: SubtitleAppearance,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onApplyPreset: (String, SubtitleAppearance) -> Unit,
    onForegroundChange: (Int) -> Unit,
    onEdgeTypeChange: (Int) -> Unit,
    onEdgeColorChange: (Int) -> Unit,
    onBackgroundChange: (Int) -> Unit,
    isAssOrSsaFormat: Boolean,
    preserveOriginalStyling: Boolean,
    onPreserveOriginalStylingChange: (Boolean) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubtitleAppearanceStudioSheet(
            presetName = presetName,
            appearance = appearance,
            fontSizeSp = fontSizeSp,
            popupWidth = popupWidth,
            popupMaxHeight = popupMaxHeight,
            onApplyPreset = onApplyPreset,
            onForegroundChange = onForegroundChange,
            onEdgeTypeChange = onEdgeTypeChange,
            onEdgeColorChange = onEdgeColorChange,
            onBackgroundChange = onBackgroundChange,
            isAssOrSsaFormat = isAssOrSsaFormat,
            preserveOriginalStyling = preserveOriginalStyling,
            onPreserveOriginalStylingChange = onPreserveOriginalStylingChange,
            onDismiss = {}
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(14.dp))

        StudioSectionLabel("Text Size")
        Text(text = "${fontSizeSp.toInt()}sp", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Slider(
            value = fontSizeSp, onValueChange = onFontSizeChange, valueRange = 12f..32f,
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.15f))
        )

        Spacer(modifier = Modifier.height(16.dp))
        StudioSectionLabel("Placement Presets")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            positionPresets.forEach { (label, value) ->
                val selected = kotlin.math.abs(bottomPadding - value) < 0.005f
                Text(
                    text = label, color = if (selected) Color.Black else TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
                        .border(1.dp, if (selected) AmberCore else AmberCore.copy(alpha = 0.3f), RoundedCornerShape(50))
                        .clickable { onBottomPaddingChange(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        StudioSectionLabel("Fine Vertical Position")
        Slider(
            value = bottomPadding, onValueChange = onBottomPaddingChange, valueRange = 0.02f..0.90f,
            colors = SliderDefaults.colors(thumbColor = AmberCore, activeTrackColor = AmberGlow, inactiveTrackColor = Color.White.copy(alpha = 0.15f))
        )
        Text(
            text = "Placement automatically stays clear of the player controls while they're visible.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
        )
    }
}

@Composable
private fun StudioDualTab(
    dualEnabled: Boolean,
    dualCanEnable: Boolean,
    dualSecondaryLanguage: String,
    dualGapLines: Int,
    dualStatusText: String,
    onToggleDual: (Boolean) -> Unit,
    onDualSecondaryLanguageChange: (String) -> Unit,
    onDualGapLinesChange: (Int) -> Unit
) {
    val languages = SubtitleLanguageRegistry.allLanguages()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Dual Subtitles")
        Text(
            text = "Shows a second language underneath the primary one, using a real subtitle release for that language — not machine translation, which CineVault doesn't do. Only works when one exists.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (!dualCanEnable) {
            Text(
                text = "Unavailable for the current track — dual mode needs a downloaded or local subtitle as the primary (not an embedded track).",
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
            )
        } else {
            StudioToggleRow(label = "Enable dual subtitles", checked = dualEnabled, onCheckedChange = onToggleDual)
            if (dualEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Secondary language", color = Color(0xFFC9A765), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(5.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    languages.forEach { (code, label) ->
                        val selected = dualSecondaryLanguage == code
                        Text(
                            text = label, color = if (selected) Color.Black else TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
                                .border(1.dp, if (selected) AmberCore else AmberCore.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .clickable { onDualSecondaryLanguageChange(code) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Gap between lines", color = Color(0xFFC9A765), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 5.dp)) {
                    (0..2).forEach { n ->
                        val selected = dualGapLines == n
                        Text(
                            text = if (n == 0) "None" else "$n line${if (n > 1) "s" else ""}", color = if (selected) Color.Black else TextBright,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
                                .clickable { onDualGapLinesChange(n) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (dualStatusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = dualStatusText, color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StudioBehaviourTab(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit
) {
    val languages = SubtitleLanguageRegistry.allLanguages()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Preferred Languages (priority order)")
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            prefs.preferredLanguages.forEachIndexed { index, code ->
                val label = languages.firstOrNull { it.first == code }?.second ?: code.uppercase()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SpaceDeep.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = "${index + 1}.", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
                    Text(text = label, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (index > 0) {
                        Text(
                            text = "↑", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                val reordered = prefs.preferredLanguages.toMutableList()
                                reordered[index] = reordered[index - 1].also { reordered[index - 1] = reordered[index] }
                                onChange(prefs.copy(preferredLanguages = reordered))
                            }.padding(horizontal = 6.dp)
                        )
                    }
                    if (prefs.preferredLanguages.size > 1) {
                        Text(
                            text = "✕", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onChange(prefs.copy(preferredLanguages = prefs.preferredLanguages.filterIndexed { i, _ -> i != index }))
                            }.padding(horizontal = 6.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val addable = languages.filter { (code, _) -> code !in prefs.preferredLanguages }
        if (addable.isNotEmpty()) {
            Text(text = "Add language", color = Color(0xFFC9A765), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                addable.forEach { (code, label) ->
                    Text(
                        text = "+ $label", color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SpaceDeep.copy(alpha = 0.7f))
                            .border(1.dp, AmberCore.copy(alpha = 0.3f), RoundedCornerShape(50))
                            .clickable { onChange(prefs.copy(preferredLanguages = prefs.preferredLanguages + code)) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        StudioToggleRow(label = "Prefer forced subtitles", checked = prefs.preferForced) { onChange(prefs.copy(preferForced = it)) }
        StudioToggleRow(label = "Prefer SDH (hearing-impaired) subtitles", checked = prefs.preferSdh) { onChange(prefs.copy(preferSdh = it)) }

        Spacer(modifier = Modifier.height(16.dp))
        StudioSectionLabel("Automatic Behavior")
        StudioToggleRow(label = "Automatically enable embedded subtitles", checked = prefs.autoEnableEmbeddedSubtitles) { onChange(prefs.copy(autoEnableEmbeddedSubtitles = it)) }
        StudioToggleRow(label = "Automatically load matching local subtitle", checked = prefs.autoLoadMatchingLocalFile) { onChange(prefs.copy(autoLoadMatchingLocalFile = it)) }
        StudioToggleRow(label = "Automatically download when none exists", checked = prefs.autoDownloadWhenMissing) { onChange(prefs.copy(autoDownloadWhenMissing = it)) }
        StudioToggleRow(label = "Remember last selected language", checked = prefs.rememberLastSelectedLanguage) { onChange(prefs.copy(rememberLastSelectedLanguage = it)) }
        StudioToggleRow(label = "Disable subtitles when audio matches preferred language", checked = prefs.disableWhenAudioMatchesPreferred) { onChange(prefs.copy(disableWhenAudioMatchesPreferred = it)) }

        Spacer(modifier = Modifier.height(16.dp))
        StudioSectionLabel("Gestures")
        StudioToggleRow(label = "Enable subtitle gestures (swipe/pinch/long-press)", checked = prefs.enableSubtitleGestures) { onChange(prefs.copy(enableSubtitleGestures = it)) }
        Text(
            text = "Off by default. When on, a zone above the player controls responds to: drag up/down for position, drag left/right for sync, pinch to resize, long-press to pause, double-tap to reset sync. Off elsewhere on screen — brightness, volume, and seek gestures are unaffected either way.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Restricted-folder videos never auto-download subtitles, regardless of these settings — that protection is fixed, not optional.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(14.dp))

        StudioSectionLabel("Subtitle Cleaning")
        Text(
            text = "Applies to downloaded and local .srt files only — embedded tracks can't be rewritten this way. SDH users who want the sound descriptions should leave the first toggle off.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        StudioToggleRow(label = "Hide hearing-impaired descriptions ([MUSIC], (door opens))", checked = cleaningOptions.hideHearingImpairedDescriptions) {
            onCleaningOptionsChange(cleaningOptions.copy(hideHearingImpairedDescriptions = it))
        }
        StudioToggleRow(label = "Remove speaker names (JOHN:)", checked = cleaningOptions.removeSpeakerNames) {
            onCleaningOptionsChange(cleaningOptions.copy(removeSpeakerNames = it))
        }
        StudioToggleRow(label = "Fix broken line breaks", checked = cleaningOptions.fixBrokenLineBreaks) {
            onCleaningOptionsChange(cleaningOptions.copy(fixBrokenLineBreaks = it))
        }
        StudioToggleRow(label = "Merge very short lines", checked = cleaningOptions.mergeVeryShortLines) {
            onCleaningOptionsChange(cleaningOptions.copy(mergeVeryShortLines = it))
        }
        StudioToggleRow(label = "Correct encoding symbols", checked = cleaningOptions.correctEncodingSymbols) {
            onCleaningOptionsChange(cleaningOptions.copy(correctEncodingSymbols = it))
        }
        StudioToggleRow(label = "Remove HTML tags", checked = cleaningOptions.removeHtmlTags) {
            onCleaningOptionsChange(cleaningOptions.copy(removeHtmlTags = it))
        }
        StudioToggleRow(label = "Convert ALL CAPS lines", checked = cleaningOptions.convertAllCaps) {
            onCleaningOptionsChange(cleaningOptions.copy(convertAllCaps = it))
        }
        StudioToggleRow(label = "Remove duplicate lines", checked = cleaningOptions.removeDuplicateLines) {
            onCleaningOptionsChange(cleaningOptions.copy(removeDuplicateLines = it))
        }
        if (cleaningOptions.isAnyEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cleaning applies the next time a subtitle is (re)loaded — reopen Tracks and reselect if you don't see it yet.",
                color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun StudioToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = AmberCore, checkedTrackColor = AmberGlow.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun StudioSectionLabel(text: String) {
    Text(text = text.uppercase(), color = Color(0xFFC9A765), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun StudioActionButton(label: String, onClick: () -> Unit) {
    Text(
        text = label, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(AmberCore).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun AutoSyncSection(
    status: AutoSyncStatus,
    available: Boolean,
    onStart: () -> Unit,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit
) {
    when (status) {
        is AutoSyncStatus.Idle -> {
            if (!available) {
                Text(
                    text = "Not available right now — needs an .srt subtitle as the primary track, a readable local/downloaded video, and no network share. Try Dialogue Sync instead.",
                    color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp
                )
            } else {
                StudioActionButton(label = "Start Auto-Sync") { onStart() }
            }
        }
        is AutoSyncStatus.Analyzing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AmberCore, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = status.stage, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        is AutoSyncStatus.Success -> AutoSyncResultCard(
            result = status.result, highConfidence = true, onApply = { onApply(status.result) }, onCancel = onCancel
        )
        is AutoSyncStatus.LowConfidence -> AutoSyncResultCard(
            result = status.result, highConfidence = false, onApply = { onApply(status.result) }, onCancel = onCancel
        )
        is AutoSyncStatus.Failed -> {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SpaceDeep.copy(alpha = 0.6f)).padding(10.dp)
            ) {
                Text(text = "Couldn't confidently sync this subtitle", color = TextBright, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = status.reason, color = TextMuted, fontSize = 10.5.sp, lineHeight = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioActionButton(label = "Try Again") { onStart() }
                }
            }
        }
    }
}

@Composable
private fun AutoSyncResultCard(
    result: SubtitleSyncResult,
    highConfidence: Boolean,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val offsetSeconds = result.initialOffsetMs / 1000f
    val accentColor = if (highConfidence) AmberCore else Color(0xFFFF9800)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // FIX: previously always labeled this "Offset:" and never showed
        // timeScale at all — a drift correction (timeScale != 1.0) would
        // apply correctly once tapped, but the person had no way to see
        // that BEFORE applying it, since the card looked identical to a
        // flat-offset result.
        val isDrift = result.timeScale != 1.0
        Text(
            text = if (highConfidence) {
                if (isDrift) "Auto-sync complete (drift correction)" else "Auto-sync complete"
            } else "Possible correction found",
            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isDrift) "Starting offset: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s"
                else "Offset: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s",
            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        if (isDrift) {
            val driftPercent = (result.timeScale - 1.0) * 100.0
            Text(
                text = "Drift: ${if (driftPercent >= 0) "+" else ""}${"%.2f".format(driftPercent)}% — timing gradually shifts across the film, not just a fixed amount",
                color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "Confidence: ${(result.confidence * 100).toInt()}%",
            color = TextMuted, fontSize = 10.sp
        )
        if (!highConfidence) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Only a limited amount of matching dialogue was found — worth previewing before you commit, or try Dialogue Sync for a manually verified result.",
                color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Apply", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accentColor).clickable { onApply() }.padding(horizontal = 14.dp, vertical = 7.dp)
            )
            Text(
                text = "Cancel", color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassSurface).clickable { onCancel() }.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}
