package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.AmberGlow
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.GlassSurface
import com.sole.cinevault.ui.theme.SpaceDeep
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.TextBright
import com.sole.cinevault.ui.theme.TextMuted
import com.sole.cinevault.ui.theme.glassPanel
import java.io.File

@Composable
internal fun StudioRadial(
    onSelectRoom: (SubtitleStudioTab) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "STUDIO",
            color = AmberCore,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadialPetal("SOURCE", Icons.Filled.ViewList, SubtitleStudioTab.SOURCE, onSelectRoom)
            RadialPetal("TIME", Icons.Filled.Sync, SubtitleStudioTab.TIME, onSelectRoom)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadialPetal("LOOK", Icons.Filled.Palette, SubtitleStudioTab.LOOK, onSelectRoom)
            RadialPetal("BRAIN", Icons.Filled.Settings, SubtitleStudioTab.BRAIN, onSelectRoom)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Long-press a petal next time for shortcuts",
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun RadialPetal(
    label: String,
    icon: ImageVector,
    tab: SubtitleStudioTab,
    onSelect: (SubtitleStudioTab) -> Unit
) {
    Column(
        modifier = Modifier
            .width(118.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SpaceDeep.copy(alpha = 0.72f))
            .border(1.dp, AmberCore.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
            .clickable { onSelect(tab) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextBright, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudioSourceRoom(
    embeddedTracks: List<SubtitleTrackChoice.Embedded>,
    downloadedTrack: SubtitleTrackChoice.Downloaded?,
    localFiles: List<File>,
    selectedTrackKey: String?,
    onSelectTrack: (SubtitleTrackChoice) -> Unit,
    onDeleteLocalTrack: (File) -> Unit,
    onOpenFilePicker: () -> Unit,
    videoPath: String,
    dualEnabled: Boolean,
    dualCanEnable: Boolean,
    dualSecondaryLanguage: String,
    dualGapLines: Int,
    dualStatusText: String,
    onToggleDual: (Boolean) -> Unit,
    onDualSecondaryLanguageChange: (String) -> Unit,
    onDualGapLinesChange: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenManualSearch: () -> Unit,
    onOpenSpeech: () -> Unit,
    contentWidth: Dp,
    contentHeight: Dp
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StudioSectionLabel("Current")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SourceAction("Auto Search", Icons.Filled.Search, Modifier.weight(1f), onOpenSearch)
            SourceAction("Web Search", Icons.Filled.Language, Modifier.weight(1f), onOpenManualSearch)
            SourceAction("Speech", Icons.Rounded.AutoAwesome, Modifier.weight(1f), onOpenSpeech)
        }
        Spacer(modifier = Modifier.height(12.dp))

        StudioSectionLabel("Dual layer")
        if (!dualCanEnable) {
            Text(
                text = "Needs a downloaded, local, or generated file as the primary — embedded-only tracks can't host a second language.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        } else {
            StudioToggleRow(label = "Layer a second language", checked = dualEnabled, onCheckedChange = onToggleDual)
            if (dualEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleLanguageRegistry.allLanguages().forEach { (code, label) ->
                        val selected = dualSecondaryLanguage == code
                        Text(
                            text = label,
                            color = if (selected) Color.Black else TextBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
                                .clickable { onDualSecondaryLanguageChange(code) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "None", 1 to "1 line", 2 to "2 lines").forEach { (n, label) ->
                        val selected = dualGapLines == n
                        Text(
                            text = label,
                            color = if (selected) Color.Black else TextBright,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AmberCore else GlassSurface)
                                .clickable { onDualGapLinesChange(n) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (dualStatusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(dualStatusText, color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = GlassBorderBottom)
        Spacer(modifier = Modifier.height(10.dp))

        SubtitleTrackSelectorSheet(
            embeddedTracks = embeddedTracks,
            downloadedTrack = downloadedTrack,
            localFiles = localFiles,
            selectedKey = selectedTrackKey,
            popupWidth = contentWidth,
            popupMaxHeight = contentHeight * 0.62f,
            onSelect = onSelectTrack,
            onDeleteLocal = onDeleteLocalTrack,
            onOpenFilePicker = onOpenFilePicker,
            onDismiss = {}
        )

        Spacer(modifier = Modifier.height(12.dp))
        StudioManagerTab(videoPath = videoPath, onSelectTrack = onSelectTrack)
    }
}

@Composable
private fun SourceAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextBright, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun StudioTimeRoom(
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
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Start Auto-Sync and this window collapses to a right-edge pill.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        StudioTimingTab(
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
    }
}

@Composable
internal fun StudioLookRoom(
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
    StudioAppearanceTab(
        presetName = presetName,
        appearance = appearance,
        fontSizeSp = fontSizeSp,
        onFontSizeChange = onFontSizeChange,
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
        bottomPadding = bottomPadding,
        onBottomPaddingChange = onBottomPaddingChange
    )
}

@Composable
internal fun StudioBrainRoom(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    onOpenTranslate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AmberCore)
                .clickable { onOpenTranslate() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI Translate current track", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(14.dp))
        StudioBehaviourTab(
            prefs = prefs,
            onChange = onChange,
            cleaningOptions = cleaningOptions,
            onCleaningOptionsChange = onCleaningOptionsChange
        )
    }
}

@Composable
fun SubtitleMakePop(
    onSpeech: () -> Unit,
    onTranslate: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .glassPanel(cornerRadius = 22.dp, fill = SpaceMid.copy(alpha = 0.97f))
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Make", color = AmberCore, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("Close", color = TextMuted, fontSize = 11.sp, modifier = Modifier.clickable { onDismiss() })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MakeTile("Speech → Subs", "Creates a track", Modifier.weight(1f), onSpeech)
            MakeTile("AI Translate", "Rewrites this track", Modifier.weight(1f), onTranslate)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("Starts a right-edge pill. Film stays clean.", color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MakeTile(title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceDeep.copy(alpha = 0.75f))
            .border(1.dp, AmberCore.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, color = TextMuted, fontSize = 10.sp)
    }
}
