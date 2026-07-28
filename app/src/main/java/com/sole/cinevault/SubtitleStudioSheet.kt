package com.sole.cinevault

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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.*
import java.io.File

enum class SubtitleStudioTab(val label: String, val icon: ImageVector) {
    TRACK("Track", Icons.Filled.ViewList),
    SYNC("Sync", Icons.Filled.Sync),
    STYLE("Style", Icons.Filled.Palette),
    POSITION("Position", Icons.Filled.SwapVert),
    ADVANCED("Advanced", Icons.Filled.Settings)
}

// Vertical placement presets — approximated via bottom-padding-fraction
// since that's the one positioning knob SubtitleView.setBottomPaddingFraction
// actually exposes. There's no horizontal control or on-video drag yet
// (that's gestures territory, #12 on the roadmap) — Position tab here only
// covers vertical placement + the safe-area-aware presets from the spec.
private val positionPresets = listOf(
    "Bottom" to 0.02f, "Above Controls" to 0.16f, "Centre" to 0.45f, "Top" to 0.85f
)

@Composable
fun SubtitleStudioSheet(
    panelWidth: Dp,
    panelMaxHeight: Dp,
    initialTab: SubtitleStudioTab,
    // Track tab
    embeddedTracks: List<SubtitleTrackChoice.Embedded>,
    downloadedTrack: SubtitleTrackChoice.Downloaded?,
    localFiles: List<File>,
    selectedTrackKey: String?,
    onSelectTrack: (SubtitleTrackChoice) -> Unit,
    onDeleteLocalTrack: (File) -> Unit,
    onOpenFilePicker: () -> Unit,
    // Sync tab
    currentSyncOffset: Float,
    onSyncOffsetChange: (Float) -> Unit,
    onDialogueSyncClick: () -> Unit,
    onDriftFixClick: () -> Unit,
    autoSyncStatus: AutoSyncStatus,
    autoSyncAvailable: Boolean,
    onAutoSyncClick: () -> Unit,
    onApplyAutoSync: (SubtitleSyncResult) -> Unit,
    onCancelAutoSync: () -> Unit,
    // Style tab
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
    // Position tab
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit,
    // Advanced tab
    behaviorPrefs: SubtitleBehaviorPrefs,
    onBehaviorPrefsChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    dualSubtitlesEnabled: Boolean,
    dualCanEnable: Boolean,
    dualSecondaryLanguage: String,
    dualGapLines: Int,
    dualStatusText: String,
    onToggleDual: (Boolean) -> Unit,
    onDualSecondaryLanguageChange: (String) -> Unit,
    onDualGapLinesChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .width(panelWidth)
            .heightIn(max = panelMaxHeight)
            .glassPanel(cornerRadius = 26.dp, fill = SpaceMid.copy(alpha = 0.98f))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Subtitle Studio", color = AmberCore, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(GlassSurface).clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextBright, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SubtitleStudioTab.values().forEach { tab ->
                    val selected = tab == selectedTab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) AmberGlow.copy(alpha = 0.18f) else Color.Transparent)
                            .then(
                                if (selected) Modifier.border(1.dp, Brush.verticalGradient(listOf(AmberGlow.copy(alpha = 0.8f), AmberDeep.copy(alpha = 0.3f))), RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); selectedTab = tab }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(imageVector = tab.icon, contentDescription = tab.label, tint = if (selected) AmberCore else TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(text = tab.label, color = if (selected) AmberCore else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorderBottom)
            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val tabContentWidth = maxWidth
                    val tabContentHeight = maxHeight
                    when (selectedTab) {
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
                        SubtitleStudioTab.SYNC -> StudioSyncTab(
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
                        SubtitleStudioTab.STYLE -> SubtitleAppearanceStudioSheet(
                            presetName = presetName,
                            appearance = appearance,
                            fontSizeSp = fontSizeSp,
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
                            onDismiss = {}
                        )
                        SubtitleStudioTab.POSITION -> StudioPositionTab(
                            fontSizeSp = fontSizeSp,
                            onFontSizeChange = onFontSizeChange,
                            bottomPadding = bottomPadding,
                            onBottomPaddingChange = onBottomPaddingChange
                        )
                        SubtitleStudioTab.ADVANCED -> StudioAdvancedTab(
                            prefs = behaviorPrefs,
                            onChange = onBehaviorPrefsChange,
                            cleaningOptions = cleaningOptions,
                            onCleaningOptionsChange = onCleaningOptionsChange,
                            dualEnabled = dualSubtitlesEnabled,
                            dualCanEnable = dualCanEnable,
                            dualSecondaryLanguage = dualSecondaryLanguage,
                            dualGapLines = dualGapLines,
                            dualStatusText = dualStatusText,
                            onToggleDual = onToggleDual,
                            onDualSecondaryLanguageChange = onDualSecondaryLanguageChange,
                            onDualGapLinesChange = onDualGapLinesChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSyncTab(
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
private fun StudioPositionTab(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    bottomPadding: Float,
    onBottomPaddingChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
private fun StudioAdvancedTab(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    dualEnabled: Boolean,
    dualCanEnable: Boolean,
    dualSecondaryLanguage: String,
    dualGapLines: Int,
    dualStatusText: String,
    onToggleDual: (Boolean) -> Unit,
    onDualSecondaryLanguageChange: (String) -> Unit,
    onDualGapLinesChange: (Int) -> Unit
) {
    val languages = listOf("en" to "English", "hi" to "Hindi", "sm" to "Samoan", "fr" to "French", "es" to "Spanish")
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

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(14.dp))

        StudioSectionLabel("Dual Subtitles")
        Text(
            text = "Shows a second language underneath the primary one, using a real subtitle release for that language — not machine translation, which CineVault doesn't do. Only works when one exists.",
            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!dualCanEnable) {
            Text(
                text = "Unavailable for the current track — dual mode needs a downloaded or local subtitle as the primary (not an embedded track).",
                color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
            )
        } else {
            StudioToggleRow(label = "Enable dual subtitles", checked = dualEnabled, onCheckedChange = onToggleDual)
            if (dualEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Secondary language", color = Color(0xFFC9A765), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Gap between lines", color = Color(0xFFC9A765), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = dualStatusText, color = AmberCore, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
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

// Never shows "Perfect Sync" or any unconditional success claim — result
// presentation is directly gated by the actual confidence score computed
// in AutoSyncEngine.kt, matching the spec's three-tier (high/medium/low)
// result design. Low confidence explicitly redirects to Dialogue Sync/
// manual controls rather than offering to apply a guess.
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
        Text(
            text = if (highConfidence) "Auto-sync complete" else "Possible correction found",
            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Offset: ${if (offsetSeconds >= 0f) "+" else ""}${"%.2f".format(offsetSeconds)}s",
            color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
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
