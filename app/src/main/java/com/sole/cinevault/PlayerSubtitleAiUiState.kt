package com.sole.cinevault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sole.cinevault.subtitles.GeneratedSubtitleFile
import com.sole.cinevault.subtitles.SpeechSubtitleStatus
import com.sole.cinevault.subtitles.SubtitleTranslationStatus

/**
 * Slice 68: stable per-video UI state for CineVault subtitle AI features.
 *
 * Speech-to-Subs and translation remain independent jobs. This holder only
 * groups their visible state plus the generated-subtitle refresh/list state.
 */
internal class PlayerSubtitleAiUiState {
    var speechSubtitleStatus by mutableStateOf<SpeechSubtitleStatus>(SpeechSubtitleStatus.Idle)
    var subtitleTranslationStatus by mutableStateOf<SubtitleTranslationStatus>(SubtitleTranslationStatus.Idle)

    var translationSuccessLanguage by mutableStateOf<String?>(null)
    var showSpeechSubtitlePanel by mutableStateOf(false)
    var showSubtitleTranslationPanel by mutableStateOf(false)

    var generatedSubtitleRefreshKey by mutableIntStateOf(0)
    var generatedSubtitleFiles by mutableStateOf<List<GeneratedSubtitleFile>>(emptyList())
}
