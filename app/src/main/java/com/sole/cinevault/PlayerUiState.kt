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
import com.sole.cinevault.subtitles.SubtitlePresets
import com.sole.cinevault.subtitles.SubtitleSearchResult
import com.sole.cinevault.subtitles.SubtitleImportResult
import com.sole.cinevault.subtitles.loadSubtitleBehaviorPrefs
import com.sole.cinevault.subtitles.loadSubtitleCleaningOptions

class AutoSubtitleFetchState {
    var attemptedForPath by mutableStateOf<String?>(null)
    var status by mutableStateOf("")
    var downloadInProgress by mutableStateOf(false)
}

class DriftCorrectionState {
    var showDialog by mutableStateOf(false)
    var pointA by mutableStateOf<DriftPoint?>(null)
    var pointB by mutableStateOf<DriftPoint?>(null)
    var scale by mutableFloatStateOf(1.0f)
    var appliedScale by mutableFloatStateOf(1.0f)
}

class DualSubtitleState {
    var enabled by mutableStateOf(false)
    var secondaryLanguage by mutableStateOf("hi")
    // Zero is the CineVault standard: primary and secondary render on
    // adjacent lines unless the user explicitly asks for extra separation.
    var gapLines by mutableStateOf(0)
    var statusText by mutableStateOf("")
    var secondarySourceLabel by mutableStateOf("")
}

class SubtitleStudioUiState {
    var menuTouchKey by mutableIntStateOf(0)
    var gestureFeedback by mutableStateOf("")
    var playerView by mutableStateOf<PlayerView?>(null)
}

class SubtitleAcquisitionUiState {
    var showSearch by mutableStateOf(false)
    var searchResults by mutableStateOf<List<SubtitleSearchResult>>(emptyList())
    var searchLoading by mutableStateOf(false)
    var searchStatus by mutableStateOf("")
    var showFallback by mutableStateOf(false)
    var showEmbeddedBrowser by mutableStateOf(false)
    var pendingImportCandidates by mutableStateOf<SubtitleImportResult.Success?>(null)
}

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

class SubtitleAppearanceUiState {
    var textSizeSp by mutableFloatStateOf(22f)
    var bottomPadding by mutableFloatStateOf(0.02f)
    var preset by mutableStateOf("CineVault")
    var appearance by mutableStateOf(SubtitlePresets.CineVault)
    var preserveOriginalStyling by mutableStateOf(false)
}

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
