package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitlePanelBackActionTest {

    @Test
    fun noPanelsOpen_returnsNone() {
        assertEquals(
            SubtitlePanelBackAction.NONE,
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = false,
                showSubtitleTranslationPanel = false,
            ),
        )
    }

    @Test
    fun speechOnly_closesSpeech() {
        assertEquals(
            SubtitlePanelBackAction.CLOSE_SPEECH,
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = true,
                showSubtitleTranslationPanel = false,
            ),
        )
    }

    @Test
    fun translationOnly_closesTranslation() {
        assertEquals(
            SubtitlePanelBackAction.CLOSE_TRANSLATION,
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = false,
                showSubtitleTranslationPanel = true,
            ),
        )
    }

    @Test
    fun bothOpen_translationHasPriority() {
        assertEquals(
            SubtitlePanelBackAction.CLOSE_TRANSLATION,
            subtitlePanelBackAction(
                showSpeechSubtitlePanel = true,
                showSubtitleTranslationPanel = true,
            ),
        )
    }
}
