package com.sole.cinevault

/** Voice 2c-3: which speech-to-text engine turns your voice into words. */
internal enum class SpeechEngine(val id: String, val label: String, val description: String) {
    Auto(
        "auto", "Auto (recommended)",
        "Uses Android's built-in recogniser when this phone has it ready, otherwise Whisper."
    ),
    Whisper(
        "whisper", "Whisper",
        "The speech model you download for subtitles. Behaves the same on every phone and works offline, but it is slower: it loads a ~100 MB model and works out the words after you stop talking."
    ),
    Android(
        "android", "Android built-in",
        "Your phone's own offline recogniser. Usually quicker and better with names and accents, and needs no download from us. It depends on your phone's speech pack, so results differ between phones."
    );

    companion object {
        fun fromId(id: String?): SpeechEngine = values().firstOrNull { it.id == id } ?: Auto
    }
}

internal sealed interface EngineChoice {
    object UseAndroid : EngineChoice
    object UseWhisper : EngineChoice
    /** Nothing can run. [reason] is shown to the person. */
    data class Unavailable(val reason: String) : EngineChoice
}

/** Picks the engine to use right now from the setting and what this phone has ready. */
internal fun chooseEngine(setting: SpeechEngine, androidReady: Boolean, whisperReady: Boolean): EngineChoice =
    when (setting) {
        SpeechEngine.Android ->
            if (androidReady) EngineChoice.UseAndroid
            else EngineChoice.Unavailable("This phone's offline speech recogniser isn't ready. Choose Whisper or Auto in Settings → Voice, or add the offline English speech pack in Android's speech settings.")
        SpeechEngine.Whisper ->
            if (whisperReady) EngineChoice.UseWhisper
            else EngineChoice.Unavailable("NO_WHISPER_MODEL")
        SpeechEngine.Auto -> when {
            androidReady -> EngineChoice.UseAndroid
            whisperReady -> EngineChoice.UseWhisper
            else -> EngineChoice.Unavailable("NO_WHISPER_MODEL")
        }
    }

/** Plain-words message for a failed Android recogniser run. Codes are SpeechRecognizer.ERROR_*. */
internal fun describeAndroidSpeechError(code: Int): String = when (code) {
    1, 2 -> "The speech recogniser couldn't reach its model. Try again, or switch the Speech engine to Whisper."
    3 -> "The microphone could not start."
    4, 5 -> "The speech recogniser had a problem. Try again."
    6, 7 -> "I didn't hear anything. Tap the microphone and try again."
    8 -> "The speech recogniser is busy. Try again in a moment."
    9 -> "Voice needs the microphone. Allow it in Android Settings."
    11, 12, 13 -> "This phone's offline speech pack isn't available. Switch the Speech engine to Whisper."
    else -> "The speech recogniser stopped (error $code). Try again."
}
