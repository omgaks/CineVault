package com.sole.cinevault.subtitles

/**
 * Slice 36: pure decision for Android back while subtitle AI panels are open.
 *
 * Translation has priority because it is the more specific panel layered over
 * the subtitle AI workflow. If it is not open, Speech → Subs closes next.
 */
enum class SubtitlePanelBackAction {
    CLOSE_TRANSLATION,
    CLOSE_SPEECH,
    NONE,
}

fun subtitlePanelBackAction(
    showSpeechSubtitlePanel: Boolean,
    showSubtitleTranslationPanel: Boolean,
): SubtitlePanelBackAction =
    when {
        showSubtitleTranslationPanel ->
            SubtitlePanelBackAction.CLOSE_TRANSLATION

        showSpeechSubtitlePanel ->
            SubtitlePanelBackAction.CLOSE_SPEECH

        else ->
            SubtitlePanelBackAction.NONE
    }
