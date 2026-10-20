package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import java.io.File

/*
 * PlayerSrtAudioPopups.kt
 *
 * Second slice of the overlay-wiring extraction out of VideoPlayerScreen.kt
 * (first was PlayerTransportPopups.kt — Speed/Sleep menus). These two are
 * next because they're self-contained: no shared remember()-derived state
 * with anything else in the block, unlike the subtitle-sheet cluster
 * (Track Selector / Studio / Search all share embeddedTrackChoices,
 * downloadedTrackChoice, localFileChoices) — that cluster is deliberately
 * being left for its own separate pass rather than bundled in here.
 *
 * Pure move: srtFiles and the built audio-track rows are still computed in
 * VideoPlayerScreen.kt exactly as before (same non-remembered recompute-
 * every-recomposition behavior — no memoization added that wasn't already
 * there) and passed in as plain values. anchoredX/anchoredY are local
 * closures in VideoPlayerScreen.kt (over `density`/`maxWidth`) that can't
 * be called from a separate file, so their resolved Dp/Int results are
 * passed in directly instead — same values, just resolved one call site
 * earlier than before.
 */
@androidx.compose.runtime.Composable
fun BoxScope.SrtAndAudioTrackPopups(
    showSrtBrowser: Boolean,
    srtFiles: List<File>,
    srtPopupWidth: Dp,
    srtPopupMaxHeight: Dp,
    srtBottomPadding: Dp,
    srtOffsetX: Int,
    onPickSrt: (File) -> Unit,
    onDeleteSrt: (File) -> Unit,
    onSystemPicker: () -> Unit,
    onCloseSrtBrowser: () -> Unit,

    showAudioSelector: Boolean,
    audioTracks: List<TrackPopupRowData>,
    audioPopupWidth: Dp,
    audioBottomPadding: Dp,
    audioOffsetX: Int,
    audioSyncMs: Int,
    onAudioSyncChange: (Int) -> Unit,
    onAudioMenuInteraction: () -> Unit,
    onCloseAudioSelector: () -> Unit,
    isLandscape: Boolean = false,
    railBottomInset: Dp = 150.dp,
) {
    AnimatedVisibility(
        visible = showSrtBrowser,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = srtBottomPadding).offset { IntOffset(srtOffsetX, 0) }
    ) {
        SrtBrowserPopup(
            files = srtFiles,
            modifier = Modifier,
            popupWidth = srtPopupWidth,
            popupMaxHeight = srtPopupMaxHeight,
            onPick = onPickSrt,
            onDelete = onDeleteSrt,
            onSystemPicker = onSystemPicker,
            onClose = onCloseSrtBrowser
        )
    }

    // Audio now uses the shared Lumen window on the right rail (sky blue, same as the AUDIO pill).
    LumenWindow(
        visible = showAudioSelector,
        accent = LumenSky,
        title = "Audio",
        icon = androidx.compose.material.icons.Icons.Rounded.MusicNote,
        size = LumenSize.M,
        isLandscape = isLandscape,
        bottomInset = railBottomInset,
    ) {
        LumenSectionLabel("Tracks", LumenSky)
        audioTracks.forEach { row ->
            LumenOptionRow(
                label = row.title,
                selected = row.selected,
                accent = LumenSky,
                trailing = row.subtitle.takeIf { it.isNotBlank() },
                onClick = { onAudioMenuInteraction(); row.onClick() },
            )
        }
        LumenSectionLabel("Audio delay", LumenSky)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            LumenChip("−50", false, LumenSky) {
                onAudioMenuInteraction(); onAudioSyncChange((audioSyncMs - 50).coerceAtLeast(-2000))
            }
            androidx.compose.material3.Text(
                text = if (audioSyncMs == 0) "0 ms" else "${if (audioSyncMs > 0) "+" else ""}$audioSyncMs ms",
                color = if (audioSyncMs == 0) com.sole.cinevault.ui.theme.TextBright else LumenSky,
                fontSize = LumenType.value,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            LumenChip("+50", false, LumenSky) {
                onAudioMenuInteraction(); onAudioSyncChange((audioSyncMs + 50).coerceAtMost(2000))
            }
        }
    }
}
