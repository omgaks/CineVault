package com.sole.cinevault

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.PlayerView
import com.sole.cinevault.subtitles.DriftPoint
import com.sole.cinevault.subtitles.SubtitleAppearance
import com.sole.cinevault.subtitles.SubtitleBehaviorPrefs
import com.sole.cinevault.subtitles.SubtitleCleaningOptions
import com.sole.cinevault.subtitles.SubtitleImportResult
import com.sole.cinevault.subtitles.SubtitlePresets
import com.sole.cinevault.subtitles.SubtitleSearchResult
import com.sole.cinevault.subtitles.SubtitleStudioTab
import com.sole.cinevault.subtitles.loadSubtitleBehaviorPrefs
import com.sole.cinevault.subtitles.loadSubtitleCleaningOptions

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

// Sixth slice: which subtitle track is currently active, and the offset/
// scale it's been shifted by. originalUri/appliedOffsetMs stay together
// with primaryUri/selectedKey-label-source rather than splitting further,
// since the shift-rebuild LaunchedEffect and every "a subtitle just got
// applied" call site touch several of these at once as one unit.
class SubtitleTrackSelectionState {
    var showSelector by mutableStateOf(false)
    var primaryUri by mutableStateOf<Uri?>(null)
    var primaryLanguage by mutableStateOf<String?>(null)
    var originalUri by mutableStateOf<Uri?>(null)
    var appliedOffsetMs by mutableLongStateOf(0L)
    var selectedKey by mutableStateOf<String?>(null)
    var selectedLabel by mutableStateOf("")
    var selectedSource by mutableStateOf("")
}

// Seventh slice: visual styling — font size, position, color/edge preset,
// and the "keep the file's own embedded ASS/SSA styling" toggle. All read
// together in the same style-application LaunchedEffect and profile-save
// LaunchedEffect, so they're one unit in practice even though they're
// conceptually "size/position" vs "color scheme" vs "override toggle".
class SubtitleAppearanceUiState {
    var textSizeSp by mutableFloatStateOf(22f)
    var bottomPadding by mutableFloatStateOf(0.02f)
    var preset by mutableStateOf("CineVault")
    var appearance by mutableStateOf(SubtitlePresets.CineVault)
    var preserveOriginalStyling by mutableStateOf(false)
}

// Eighth and final slice: the core on/off toggles, the sync-offset value
// itself, the dialogue-tap-sync flow's two fields (kept together since
// they're one feature), and the two persisted preference blobs. This is
// the biggest single slice (12 variables) because it's genuinely the most
// central — read from nearly every overlay function rather than one
// specific flow, which is also why it was done last: by this point every
// OTHER cluster's own state is already isolated, so this one's the
// "everything else" bucket rather than a feature in its own right.
// Needs Context at construction time since behaviorPrefs/cleaningOptions
// load their saved values from SharedPreferences immediately.
class SubtitleCoreUiState(context: Context) {
    var subtitlesEnabled by mutableStateOf(true)
    var showSettings by mutableStateOf(false)
    var showAppearanceStudio by mutableStateOf(false)
    var syncOffset by mutableFloatStateOf(0.0f)
    var dialogueSyncArmed by mutableStateOf(false)
    var dialogueSyncReferenceMs by mutableStateOf<Long?>(null)
    var behaviorPrefs by mutableStateOf(loadSubtitleBehaviorPrefs(context))
    var cleaningOptions by mutableStateOf(loadSubtitleCleaningOptions(context))
}
