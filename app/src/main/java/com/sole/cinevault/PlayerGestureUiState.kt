package com.sole.cinevault

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sole.cinevault.library.VideoThumbnailHelper

/**
 * Slice 66: stable state holder for playback gesture presentation.
 *
 * It owns only mutable UI state: zoom/pan transform, seek-preview state and
 * the transient edge-swipe hint. Gesture behavior remains in the existing
 * PlayerPlaybackGestureLayer and PlayerTransportAndSmartControls.
 */
internal class PlayerGestureUiState {
    var isZoomMode by mutableStateOf(false)
    var videoScale by mutableFloatStateOf(1f)
    var videoOffsetX by mutableFloatStateOf(0f)
    var videoOffsetY by mutableFloatStateOf(0f)

    var showSeekPreview by mutableStateOf(false)
    var previewPosition by mutableLongStateOf(0L)
    var previewBitmap by mutableStateOf<Bitmap?>(null)
    var isSeekPreviewLarge by mutableStateOf(false)
    var previewFrames by mutableStateOf<List<VideoThumbnailHelper.PreviewFrame>>(emptyList())

    // Bumping this reruns preview generation after Auto-Sync deliberately
    // releases preview memory during analysis.
    var previewReloadKey by mutableIntStateOf(0)

    var edgeSwipeHint by mutableStateOf("")
}
