package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleJobPresentationTest {

    @Test
    fun speechDownloadingModel_mapsLabelAndProgress() {
        val result = speechSubtitleJobPresentation(
            SpeechSubtitleStatus.DownloadingModel(percent = 42)
        )

        assertEquals("Whisper model", result.label)
        assertEquals(42, result.progress)
    }

    @Test
    fun speechGenerating_mapsLabelAndProgress() {
        val result = speechSubtitleJobPresentation(
            SpeechSubtitleStatus.Generating(
                phase = "Transcribing",
                percent = 73,
            )
        )

        assertEquals("Speech → Subs", result.label)
        assertEquals(73, result.progress)
    }

    @Test
    fun speechIdle_hasNoPresentation() {
        val result = speechSubtitleJobPresentation(
            SpeechSubtitleStatus.Idle
        )

        assertNull(result.label)
        assertNull(result.progress)
    }

    @Test
    fun translationInProgress_mapsLabelAndProgress() {
        val result = subtitleTranslationJobPresentation(
            SubtitleTranslationStatus.Translating(
                phase = "Hindi",
                percent = 61,
            )
        )

        assertEquals("AI Translate", result.label)
        assertEquals(61, result.progress)
    }

    @Test
    fun translationIdle_hasNoPresentation() {
        val result = subtitleTranslationJobPresentation(
            SubtitleTranslationStatus.Idle
        )

        assertNull(result.label)
        assertNull(result.progress)
    }
}
