package com.sole.cinevault.subtitles

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.platform.LocalContext
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
// MANAGE added this round — lists every language already downloaded for
// THIS video (via OpenSubtitlesClient.listCachedSubtitlesForVideo) with a
// delete action per language, addressing "no way to manage downloaded
// subtitles" directly instead of leaving cached files to just accumulate
// silently.
enum class SubtitleStudioTab(val label: String, val icon: ImageVector) {
    TRACK("Tracks", Icons.Filled.ViewList),
    TIMING("Timing", Icons.Filled.Sync),
    APPEARANCE("Appearance", Icons.Filled.Palette),
    DUAL("Dual", Icons.Filled.SwapHoriz),
    MANAGE("Manage", Icons.Filled.Storage),
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
    // Needed by the new Manage tab to look up which languages are cached
    // for THIS specific video (OpenSubtitlesClient keys its cache by video
    // path). Every other tab already gets everything it needs passed in
    // explicitly rather than reaching for global state, so this follows
    // the same pattern instead of Manage being the one tab that's special.
    videoPath: String,
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
                            SubtitleStudioTab.MANAGE -> StudioManagerTab(
                                videoPath = videoPath,
                                onSelectTrack = onSelectTrack
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
            // NEW — Subtitle Manager, filling one of the two previously-
            // empty spacer slots in this row rather than adding a new row.
            ToolTile(icon = SubtitleStudioTab.MANAGE.icon, label = SubtitleStudioTab.MANAGE.label, modifier = Modifier.weight(1f)) { onSelectTool(SubtitleStudioTab.MANAGE) }
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

// ── Subtitle Manager ──────────────────────────────────────────────────────
// NEW this round. Lists every language currently cached for THIS video
// (OpenSubtitlesClient.listCachedSubtitlesForVideo scans the shared cache
// dir for files sharing this video's hash prefix — there's no separate
// index, the filenames on disk are the source of truth). Each row can be
// applied directly (routes through the existing SubtitleTrackChoice.
// Downloaded selection path — same cleaning/playback pipeline every other
// subtitle source already goes through) or deleted, with a lightweight
// inline confirm rather than a separate dialog since this is a low-stakes,
// easily-undone-by-redownloading action.
// NOTE: provider is now embedded in the filename itself
// (<hash>.<lang>.<providerSlug>.srt — see OpenSubtitlesClient.
// subtitleCacheFile), with a fallback for anything cached before this
// existed (legacy <hash>.<lang>.srt files are treated as OpenSubtitles,
// since that's the only provider that existed when those were written).
// One real gap left: subtitles downloaded through SubDL's OWN direct
// download path (SubDlClient.downloadSubtitle) aren't confirmed to write
// through this same scheme — that file wasn't available to verify against,
// so a SubDL download via that specific path might still land in the
// legacy/default slot until SubDlClient.kt is checked.
@Composable
private fun StudioManagerTab(
    videoPath: String,
    onSelectTrack: (SubtitleTrackChoice) -> Unit
) {
    val context = LocalContext.current
    var cached by remember(videoPath) { mutableStateOf(OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)) }
    var pendingDelete by remember { mutableStateOf<CachedSubtitle?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Downloaded Subtitles")
        Text(
            text = "Every language already downloaded for this video, with which provider it came from.",
            color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (cached.isEmpty()) {
            Text(text = "No downloaded subtitles for this video yet.", color = TextFaint, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cached.forEach { sub ->
                    val label = SubtitleLanguageRegistry.displayName(sub.language)
                    val file = sub.uri.path?.let { File(it) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SpaceDeep.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = sub.provider, color = TextMuted, fontSize = 10.sp)
                        }
                        if (file != null) {
                            Text(
                                text = "Use", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AmberCore)
                                    .clickable { onSelectTrack(SubtitleTrackChoice.Downloaded(file = file, language = sub.language)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete $label subtitle",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp).clickable { pendingDelete = sub }
                        )
                    }
                }
            }
        }

        pendingDelete?.let { toDelete ->
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpaceDeep.copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(text = "Delete ${SubtitleLanguageRegistry.displayName(toDelete.language)} (${toDelete.provider}) subtitle?", color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cancel", color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassSurface).clickable { pendingDelete = null }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                    Text(
                        text = "Delete", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFF5252)).clickable {
                            OpenSubtitlesClient.deleteCachedSubtitle(context, toDelete)
                            cached = OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath)
                            pendingDelete = null
                        }.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudioSectionLabel(text: String) {
    Text(text = text.uppercase(), color = Color(0xFFC9A765), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 6.dp))
}
