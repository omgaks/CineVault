package com.sole.cinevault.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.GlassBorderBottom
import com.sole.cinevault.ui.theme.SpaceMid
import com.sole.cinevault.ui.theme.glassPanel
import java.io.File
import kotlin.math.roundToInt

private sealed class StudioScreen {
    object Radial : StudioScreen()
    data class Room(val tab: SubtitleStudioTab) : StudioScreen()
}

@Composable
fun SubtitleStudioSheet(
    panelWidth: Dp,
    panelMaxHeight: Dp,
    containerWidth: Dp,
    containerHeight: Dp,
    initialTab: SubtitleStudioTab?,
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
    onUserInteraction: () -> Unit = {},
    onOpenSpeech: () -> Unit = {},
    onOpenTranslate: () -> Unit = {}
) {
    var screen by remember {
        mutableStateOf(
            if (initialTab != null) StudioScreen.Room(initialTab.asRoom())
            else StudioScreen.Radial
        )
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val maxOffsetXPx = with(density) { ((containerWidth - panelWidth) / 2).coerceAtLeast(0.dp).toPx() }
    val maxOffsetYPx = with(density) { ((containerHeight - panelMaxHeight) / 2).coerceAtLeast(0.dp).toPx() }

    if (screen is StudioScreen.Radial) {
        StudioBloom(
            onSelectRoom = { tab ->
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                screen = StudioScreen.Room(tab.asRoom())
            },
            onDismiss = onDismiss
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserInteraction()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                .width(panelWidth)
                .heightIn(max = panelMaxHeight)
                .glassPanel(cornerRadius = 26.dp, fill = SpaceMid.copy(alpha = 0.98f))
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            val currentScreen = screen
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
                AmberPill(icon = Icons.Default.ArrowBack, label = "Back") {
                    if (currentScreen is StudioScreen.Room) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        screen = StudioScreen.Radial
                    } else {
                        onDismiss()
                    }
                }
                Text(
                    text = when (currentScreen) {
                        is StudioScreen.Room -> currentScreen.tab.asRoom().label
                        StudioScreen.Radial -> "Subtitle Studio"
                    },
                    color = AmberCore,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
                AmberPill(icon = Icons.Default.Close, label = "Close", onClick = onDismiss)
            }
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = GlassBorderBottom)
            Spacer(modifier = Modifier.height(6.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    when (val s = currentScreen) {
                        StudioScreen.Radial -> StudioRadial { tab ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            screen = StudioScreen.Room(tab.asRoom())
                        }
                        is StudioScreen.Room -> when (s.tab.asRoom()) {
                            SubtitleStudioTab.SOURCE -> StudioSourceRoom(
                                embeddedTracks = embeddedTracks,
                                downloadedTrack = downloadedTrack,
                                localFiles = localFiles,
                                selectedTrackKey = selectedTrackKey,
                                onSelectTrack = onSelectTrack,
                                onDeleteLocalTrack = onDeleteLocalTrack,
                                onOpenFilePicker = onOpenFilePicker,
                                videoPath = videoPath,
                                dualEnabled = dualSubtitlesEnabled,
                                dualCanEnable = dualCanEnable,
                                dualSecondaryLanguage = dualSecondaryLanguage,
                                dualGapLines = dualGapLines,
                                dualStatusText = dualStatusText,
                                onToggleDual = onToggleDual,
                                onDualSecondaryLanguageChange = onDualSecondaryLanguageChange,
                                onDualGapLinesChange = onDualGapLinesChange,
                                onOpenSearch = onOpenSearch,
                                onOpenManualSearch = onOpenManualSearch,
                                onOpenSpeech = onOpenSpeech,
                                contentWidth = maxWidth,
                                contentHeight = maxHeight
                            )
                            SubtitleStudioTab.TIME -> StudioTimeRoom(
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
                            SubtitleStudioTab.LOOK -> StudioLookRoom(
                                presetName = presetName,
                                appearance = appearance,
                                fontSizeSp = fontSizeSp,
                                onFontSizeChange = onFontSizeChange,
                                popupWidth = maxWidth,
                                popupMaxHeight = maxHeight,
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
                            else -> StudioBrainRoom(
                                prefs = behaviorPrefs,
                                onChange = onBehaviorPrefsChange,
                                cleaningOptions = cleaningOptions,
                                onCleaningOptionsChange = onCleaningOptionsChange,
                                onOpenTranslate = onOpenTranslate
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun StudioSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFFC9A765),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun AmberPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(AmberCore)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.Black, modifier = Modifier.size(18.dp))
    }
}
