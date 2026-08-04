package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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

    AnimatedVisibility(
        visible = showAudioSelector,
        enter = fadeIn(animationSpec = tween(150)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = audioBottomPadding).offset { IntOffset(audioOffsetX, 0) }
    ) {
        FloatingTrackPopup(
            title = "Audio",
            modifier = Modifier.width(audioPopupWidth),
            audioSyncMs = audioSyncMs,
            onAudioSyncChange = onAudioSyncChange,
            rows = audioTracks,
            onAnyClick = onAudioMenuInteraction,
            onClose = onCloseAudioSelector
        )
    }
}
