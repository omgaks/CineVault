package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.PlayerView
import com.sole.cinevault.subtitles.DriftPoint
import com.sole.cinevault.subtitles.SubtitleImportResult
import com.sole.cinevault.subtitles.SubtitleSearchResult
import com.sole.cinevault.subtitles.SubtitleStudioTab

/*
 * PlayerUiState.kt
 *
 * State holders extracted out of VideoPlayerScreen.kt's top-of-composable
 * "wall" of ~100 `by remember { mutableStateOf(...) }` declarations. Each
 * holder groups one cohesive slice of state into its own small class —
 * see conversation history for why this is being done incrementally
 * (smallest/most self-contained cluster first) rather than all at once:
 * every read and write of every one of these variables throughout the
 * file needs updating to go through the holder, which is a much larger
 * and more error-prone surface than the earlier package-move and
 * overlay-wiring work, where the underlying code never changed at all.
 *
 * Usage in VideoPlayerScreen.kt: `val x = remember { XState() }`, then
 * every former `someVar` read/write becomes `x.someVar`.
 */

// First slice: the auto-subtitle-fetch flow (searching for and applying a
// subtitle automatically when a video with no existing match is opened).
// Genuinely self-contained — used from two LaunchedEffects and one status
// banner, nothing else in the file touches it.
class AutoSubtitleFetchState {
    var attemptedForPath by mutableStateOf<String?>(null)
    var status by mutableStateOf("")
    var downloadInProgress by mutableStateOf(false)
}

// Second slice: drift correction (the "mark two points, compute a linear
// timescale fix" flow). scale/appliedScale are compared against each
// other in the shift-rebuild LaunchedEffect, so they stay together here
// rather than splitting further.
class DriftCorrectionState {
    var showDialog by mutableStateOf(false)
    var pointA by mutableStateOf<DriftPoint?>(null)
    var pointB by mutableStateOf<DriftPoint?>(null)
    var scale by mutableFloatStateOf(1.0f)
    var appliedScale by mutableFloatStateOf(1.0f)
}

// Third slice: dual subtitles (primary + secondary language merged into
// one track). All four genuinely only make sense together — enabling
// without a language, or a status message with nothing driving it, isn't
// a meaningful state on its own.
class DualSubtitleState {
    var enabled by mutableStateOf(false)
    var secondaryLanguage by mutableStateOf("hi")
    var gapLines by mutableStateOf(1)
    var statusText by mutableStateOf("")
}

// Fourth slice: Subtitle Studio's own visibility/tab state, the shared
// "any menu just got touched" key that resets every popup's auto-hide
// timer, the gesture-feedback pill text, and the PlayerView reference
// subtitle styling gets applied through. Grouped together because they're
// all "Studio-adjacent plumbing" rather than one specific feature's data —
// unlike the previous three slices, which were each one self-contained
// flow.
class SubtitleStudioUiState {
    var showStudio by mutableStateOf(false)
    var initialTab by mutableStateOf<SubtitleStudioTab?>(null)
    var menuTouchKey by mutableIntStateOf(0)
    var gestureFeedback by mutableStateOf("")
    var playerView by mutableStateOf<PlayerView?>(null)
}

// Fifth slice: the "getting a subtitle onto the device" chain — search,
// then (on failure) the website fallback, then (from there) the embedded
// browser, which can surface multiple candidates for the person to pick
// from. Grouped together because they're genuinely one flow, not five
// independent toggles — each step's state only matters in the context of
// how the person got there.
class SubtitleAcquisitionUiState {
    var showSearch by mutableStateOf(false)
    var searchResults by mutableStateOf<List<SubtitleSearchResult>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchStatus by mutableStateOf("")
    var showFallback by mutableStateOf(false)
    var showEmbeddedBrowser by mutableStateOf(false)
    var pendingImportCandidates by mutableStateOf<SubtitleImportResult.Success?>(null)
}
