package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEngineLogicTest {
    @Test fun autoPrefersAndroidThenWhisper() {
        assertEquals(EngineChoice.UseAndroid, chooseEngine(SpeechEngine.Auto, true, true))
        assertEquals(EngineChoice.UseAndroid, chooseEngine(SpeechEngine.Auto, true, false))
        assertEquals(EngineChoice.UseWhisper, chooseEngine(SpeechEngine.Auto, false, true))
        assertTrue(chooseEngine(SpeechEngine.Auto, false, false) is EngineChoice.Unavailable)
    }

    @Test fun manualChoicesAreRespected() {
        assertEquals(EngineChoice.UseWhisper, chooseEngine(SpeechEngine.Whisper, true, true))
        assertEquals(EngineChoice.UseAndroid, chooseEngine(SpeechEngine.Android, true, true))
        assertTrue(chooseEngine(SpeechEngine.Android, false, true) is EngineChoice.Unavailable)
        assertEquals(EngineChoice.Unavailable("NO_WHISPER_MODEL"), chooseEngine(SpeechEngine.Whisper, true, false))
    }

    @Test fun unknownSavedValueFallsBackToAuto() {
        assertEquals(SpeechEngine.Auto, SpeechEngine.fromId(null))
        assertEquals(SpeechEngine.Auto, SpeechEngine.fromId("nonsense"))
        assertEquals(SpeechEngine.Whisper, SpeechEngine.fromId("whisper"))
    }

    @Test fun errorsAreInPlainWords() {
        assertTrue(describeAndroidSpeechError(7).startsWith("I didn't hear"))
        assertTrue(describeAndroidSpeechError(12).contains("Whisper"))
        assertTrue(describeAndroidSpeechError(99).contains("99"))
    }
}
