package com.sole.cinevault.subtitles

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import com.sole.cinevault.ui.theme.AmberCore
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
        Text("STUDIO", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(modifier = Modifier.height(16.dp))
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
        Text("One room. No nested windows.", color = TextMuted, fontSize = 10.sp)
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
            .width(124.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(SpaceDeep.copy(alpha = 0.78f))
            .border(1.dp, AmberCore.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
            .clickable { onSelect(tab) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
    val context = LocalContext.current
    val cached = remember(videoPath) { OpenSubtitlesClient.listCachedSubtitlesForVideo(context, videoPath) }
    val currentLabel = when {
        selectedTrackKey == null || selectedTrackKey == SubtitleTrackChoice.Off.key -> "Off"
        downloadedTrack != null && selectedTrackKey == downloadedTrack.key ->
            "${SubtitleLanguageRegistry.displayName(downloadedTrack.language)} · Downloaded"
        else -> embeddedTracks.firstOrNull { it.key == selectedTrackKey }?.let {
            buildString {
                append(SubtitleLanguageRegistry.displayName(it.language))
                if (it.isSdh) append(" SDH")
                if (it.isForced) append(" Forced")
                append(" · Embedded")
            }
        } ?: localFiles.firstOrNull { SubtitleTrackChoice.Local(it).key == selectedTrackKey }?.name ?: "On"
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RoomCard {
            Text("ON  ·  $currentLabel", color = AmberCore, fontSize = 13.sp, fontWeight = FontWeight.Black)
            if (dualEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Dual layer  ·  ${SubtitleLanguageRegistry.displayName(dualSecondaryLanguage)}  ·  $dualGapLines-line gap",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SourceAction("Auto", Icons.Filled.Search, Modifier.weight(1f), onOpenSearch)
            SourceAction("Web", Icons.Filled.Language, Modifier.weight(1f), onOpenManualSearch)
            SourceAction("Speech", Icons.Rounded.AutoAwesome, Modifier.weight(1f), onOpenSpeech)
        }
        Spacer(modifier = Modifier.height(14.dp))

        StudioSectionLabel("Dual layer")
        if (!dualCanEnable) {
            Text(
                "Needs a downloaded, local, or generated file as the primary.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        } else {
            StudioToggleRow("Layer a second language", dualEnabled, onToggleDual)
            if (dualEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleLanguageRegistry.allLanguages().forEach { (code, label) ->
                        AmberChip(label, dualSecondaryLanguage == code) { onDualSecondaryLanguageChange(code) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "None", 1 to "1 line", 2 to "2 lines").forEach { (n, label) ->
                        AmberChip(label, dualGapLines == n) { onDualGapLinesChange(n) }
                    }
                }
                if (dualStatusText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(dualStatusText, color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        StudioSectionLabel("Tracks")
        TrackRow("Off", selectedTrackKey == SubtitleTrackChoice.Off.key || selectedTrackKey == null) {
            onSelectTrack(SubtitleTrackChoice.Off)
        }
        embeddedTracks.forEach { track ->
            val label = buildString {
                append(SubtitleLanguageRegistry.displayName(track.language))
                if (track.isSdh) append("  SDH")
                if (track.isForced) append("  Forced")
            }
            TrackRow(label, selectedTrackKey == track.key) { onSelectTrack(track) }
        }
        downloadedTrack?.let { track ->
            TrackRow(
                "${SubtitleLanguageRegistry.displayName(track.language)}  ·  file",
                selectedTrackKey == track.key
            ) { onSelectTrack(track) }
        }
        localFiles.forEach { file ->
            val choice = SubtitleTrackChoice.Local(file)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selectedTrackKey == choice.key) AmberCore.copy(alpha = 0.18f) else SpaceDeep.copy(alpha = 0.55f))
                    .clickable { onSelectTrack(choice) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(file.name, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp).clickable { onDeleteLocalTrack(file) })
            }
        }
        if (cached.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            StudioSectionLabel("Downloaded")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cached.forEach { sub ->
                    val file = sub.uri.path?.let(::File)
                    AmberChip(SubtitleLanguageRegistry.displayName(sub.language), false) {
                        if (file != null) onSelectTrack(SubtitleTrackChoice.Downloaded(file, sub.language))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Import file",
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(AmberCore)
                .clickable(onClick = onOpenFilePicker)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SourceAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceDeep.copy(alpha = 0.7f))
            .border(1.dp, AmberCore.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = AmberCore, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextBright, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    var showManual by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Auto-Sync collapses to the right-edge pill. Nudge a cue on the strip to shift delay.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                !autoSyncAvailable -> "Need a file-based track"
                autoSyncStatus is AutoSyncStatus.Analyzing -> "Listening…"
                else -> "Auto-Sync"
            },
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (autoSyncAvailable) AmberCore else GlassSurface)
                .clickable(enabled = autoSyncAvailable && autoSyncStatus !is AutoSyncStatus.Analyzing, onClick = onAutoSyncClick)
                .padding(vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        when (val status = autoSyncStatus) {
            is AutoSyncStatus.Analyzing -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(status.stage, color = AmberCore, fontSize = 11.sp)
            }
            is AutoSyncStatus.Success -> AutoSyncResultCard(status.result, highConfidence = true, onApplyAutoSync, onCancelAutoSync)
            is AutoSyncStatus.LowConfidence -> AutoSyncResultCard(status.result, highConfidence = false, onApplyAutoSync, onCancelAutoSync)
            is AutoSyncStatus.Failed -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(status.reason, color = Color(0xFFFF8A80), fontSize = 11.sp)
            }
            else -> Unit
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = (if (currentSyncOffset >= 0f) "+" else "") + "%.1fs".format(currentSyncOffset),
            color = TextBright,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        CueFilmstrip(currentSyncOffset, onSyncOffsetChange)
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StepCircle { onSyncOffsetChange((currentSyncOffset - 0.1f).coerceIn(-10f, 10f)) }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Nudge 0.1s", color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(16.dp))
            StepCircle(add = true) { onSyncOffsetChange((currentSyncOffset + 0.1f).coerceIn(-10f, 10f)) }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            if (showManual) "Hide manual tools" else "Dialogue tap  ·  two-point drift",
            color = AmberCore,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { showManual = !showManual }
        )
        if (showManual) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmberChip("Dialogue Tap", false, Modifier.weight(1f), onDialogueSyncClick)
                AmberChip("Fix drift", false, Modifier.weight(1f), onDriftFixClick)
            }
        }
    }
}

@Composable
private fun AutoSyncResultCard(
    result: SubtitleSyncResult,
    highConfidence: Boolean,
    onApply: (SubtitleSyncResult) -> Unit,
    onCancel: () -> Unit
) {
    val seconds = result.initialOffsetMs / 1000f
    Spacer(modifier = Modifier.height(10.dp))
    RoomCard {
        Text(
            if (highConfidence) "Ready  ·  %+.2fs  ·  ${(result.confidence * 100).toInt()}%".format(seconds)
            else "Low confidence  ·  %+.2fs".format(seconds),
            color = AmberCore,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberChip("Apply", true) { onApply(result) }
            AmberChip("Keep current", false, onClick = onCancel)
        }
    }
}

@Composable
private fun CueFilmstrip(offset: Float, onOffsetChange: (Float) -> Unit) {
    val cells = listOf(-0.4f, -0.2f, 0f, 0.2f, 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceDeep.copy(alpha = 0.65f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cells.forEach { delta ->
            val selected = kotlin.math.abs(delta) < 0.01f
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) AmberCore else GlassSurface)
                    .clickable { onOffsetChange((offset + delta).coerceIn(-10f, 10f)) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(if (selected) "NOW" else "%+.1f".format(delta), color = if (selected) Color.Black else TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(if (selected) Color.Black else AmberCore.copy(alpha = 0.5f)))
            }
        }
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
    val previewPresets = listOf("CineVault", "Cinema", "High Contrast")
    val colors = listOf(
        0xFFFFF3D6.toInt(),
        AndroidColor.WHITE,
        AndroidColor.YELLOW,
        AndroidColor.CYAN,
        0xFFFFC857.toInt()
    )
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 18.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "I am not afraid of the dark.",
                color = Color(appearance.foregroundColor.toLong() and 0xFFFFFFFF),
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        StudioSectionLabel("Presets")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            previewPresets.forEach { name ->
                val preset = SubtitlePresets.all.first { it.first == name }.second
                val selected = presetName == name
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AmberCore.copy(alpha = 0.2f) else SpaceDeep.copy(alpha = 0.65f))
                        .border(1.dp, if (selected) AmberCore else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onApplyPreset(name, preset) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Aa", color = Color(preset.foregroundColor.toLong() and 0xFFFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(name, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        StudioSectionLabel("Paint")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            colors.forEach { color ->
                val selected = appearance.foregroundColor == color
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(color.toLong() and 0xFFFFFFFF))
                        .border(2.dp, if (selected) AmberCore else Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { onForegroundChange(color) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmberChip("Outline", appearance.edgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
                onEdgeTypeChange(CaptionStyleCompat.EDGE_TYPE_OUTLINE)
                onEdgeColorChange(AndroidColor.BLACK)
            }
            AmberChip("Shadow", appearance.edgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
                onEdgeTypeChange(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW)
                onEdgeColorChange(AndroidColor.BLACK)
            }
            AmberChip("None", appearance.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE) {
                onEdgeTypeChange(CaptionStyleCompat.EDGE_TYPE_NONE)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmberChip("Box off", appearance.backgroundColor == AndroidColor.TRANSPARENT) { onBackgroundChange(AndroidColor.TRANSPARENT) }
            AmberChip("Soft", appearance.backgroundColor == 0x99000000.toInt()) { onBackgroundChange(0x99000000.toInt()) }
            AmberChip("Solid", appearance.backgroundColor == AndroidColor.BLACK) { onBackgroundChange(AndroidColor.BLACK) }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StepCircle { onFontSizeChange((fontSizeSp - 1f).coerceIn(12f, 32f)) }
            Text("${fontSizeSp.toInt()}", color = AmberCore, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepCircle(add = true) { onFontSizeChange((fontSizeSp + 1f).coerceIn(12f, 32f)) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Place", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sharedPositionPresets.take(3).forEach { (label, value) ->
                    AmberChip(label, kotlin.math.abs(bottomPadding - value) < 0.05f) { onBottomPaddingChange(value) }
                }
            }
        }
        if (isAssOrSsaFormat) {
            Spacer(modifier = Modifier.height(10.dp))
            StudioToggleRow("Keep original ASS/SSA styling", preserveOriginalStyling, onPreserveOriginalStylingChange)
        }
    }
}

@Composable
internal fun StudioBrainRoom(
    prefs: SubtitleBehaviorPrefs,
    onChange: (SubtitleBehaviorPrefs) -> Unit,
    cleaningOptions: SubtitleCleaningOptions,
    onCleaningOptionsChange: (SubtitleCleaningOptions) -> Unit,
    onOpenTranslate: () -> Unit
) {
    val languages = SubtitleLanguageRegistry.allLanguages()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RoomCard {
            StudioSectionLabel("1  Language policy")
            prefs.preferredLanguages.forEachIndexed { index, code ->
                val label = languages.firstOrNull { it.first == code }?.second ?: code.uppercase()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", color = AmberCore, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
                    Text(label, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (index > 0) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            "Move up",
                            tint = AmberCore,
                            modifier = Modifier.size(22.dp).clickable {
                                val next = prefs.preferredLanguages.toMutableList()
                                val item = next.removeAt(index)
                                next.add(index - 1, item)
                                onChange(prefs.copy(preferredLanguages = next))
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RoomCard {
            StudioSectionLabel("2  Auto policy")
            Text("If this video has no subs", color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(8.dp))
            StudioToggleRow("Use embedded", prefs.autoEnableEmbeddedSubtitles) {
                onChange(prefs.copy(autoEnableEmbeddedSubtitles = it))
            }
            StudioToggleRow("Load local file", prefs.autoLoadMatchingLocalFile) {
                onChange(prefs.copy(autoLoadMatchingLocalFile = it))
            }
            StudioToggleRow("Download", prefs.autoDownloadWhenMissing) {
                onChange(prefs.copy(autoDownloadWhenMissing = it))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        RoomCard {
            StudioSectionLabel("3  Cleaner")
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Before", color = TextMuted, fontSize = 10.sp)
                    Text("[DOOR SLAMS]\nJOHN: We leave.", color = TextBright, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("After", color = TextMuted, fontSize = 10.sp)
                    Text("We leave.", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            StudioToggleRow("Clean noise", cleaningOptions.hideHearingImpairedDescriptions && cleaningOptions.removeSpeakerNames) { on ->
                onCleaningOptionsChange(
                    cleaningOptions.copy(
                        hideHearingImpairedDescriptions = on,
                        removeSpeakerNames = on
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AmberCore)
                .clickable(onClick = onOpenTranslate)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI Translate  ·  starts a pill", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
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
            Text("Close", color = TextMuted, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onDismiss))
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
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = TextBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(hint, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun RoomCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceDeep.copy(alpha = 0.62f))
            .padding(12.dp)
    ) { content() }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else TextBright,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun AmberChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else TextBright,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AmberCore else SpaceDeep.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun StepCircle(add: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(GlassSurface)
            .border(1.dp, AmberCore.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (add) Icons.Filled.Add else Icons.Filled.Remove, null, tint = AmberCore, modifier = Modifier.size(14.dp))
    }
}
