package com.sole.cinevault

/**
 * Voice 2c-1: what happens after a tap-to-talk recording has been turned into
 * text. Pure Kotlin so it can be tested without a phone.
 */
internal sealed interface TalkOutcome {
    data class PlayFilm(val film: TitleCandidate) : TalkOutcome
    /** Several films fit about equally. The person picks. */
    data class PickFilm(val query: String, val options: List<TitleCandidate>) : TalkOutcome
    data class FilmNotFound(val query: String) : TalkOutcome
    /** A risky request. It is never run by voice. */
    data class NeedsScreen(val action: String) : TalkOutcome
    /** Understood, but it controls the player, which arrives in the next update. */
    data class PlayerCommand(val description: String) : TalkOutcome
    data class NotUnderstood(val heard: String) : TalkOutcome
    /** Nothing was said, or only noise. */
    object Silence : TalkOutcome
}

private val SILENCE_HALLUCINATIONS = setOf(
    "you", "thank you", "thanks", "thanks for watching", "thank you for watching", "bye", "bye bye", "okay", "ok"
)

/**
 * Whisper adds things like "[BLANK_AUDIO]" or "(music)" for noise and often
 * invents "Thank you." for silence. Returns null when there is nothing real.
 */
internal fun cleanTranscript(raw: String): String? {
    val stripped = raw
        .replace(Regex("\\[[^\\]]*\\]"), " ")
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\*[^*]*\\*"), " ")
        .replace("♪", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (stripped.isEmpty()) return null
    val plain = stripped.lowercase().replace(Regex("[^a-z ]"), "").trim()
    if (plain.isEmpty() || plain in SILENCE_HALLUCINATIONS) return null
    return stripped
}

/** A short plain-words description of a command, for the "I understood" line. */
internal fun describeCommand(command: VoiceCommand): String = when (command) {
    VoiceCommand.Play -> "play"
    VoiceCommand.Pause -> "pause"
    VoiceCommand.Mute -> "mute"
    VoiceCommand.Unmute -> "unmute"
    VoiceCommand.NextEpisode -> "next episode"
    VoiceCommand.PreviousEpisode -> "previous episode"
    VoiceCommand.SubtitlesOn -> "subtitles on"
    VoiceCommand.SubtitlesOff -> "subtitles off"
    VoiceCommand.FitScreen -> "fit to screen"
    VoiceCommand.FillScreen -> "fill the screen"
    VoiceCommand.ExitPlayer -> "close the player"
    is VoiceCommand.SkipSeconds ->
        if (command.seconds >= 0) "skip forward ${command.seconds} seconds" else "skip back ${-command.seconds} seconds"
    is VoiceCommand.JumpToMs -> "jump to ${formatListened(command.positionMs)}"
    is VoiceCommand.VolumeBy -> if (command.percent >= 0) "volume up ${command.percent}" else "volume down ${-command.percent}"
    is VoiceCommand.VolumeTo -> "volume ${command.percent}"
    is VoiceCommand.BrightnessBy -> if (command.percent >= 0) "brighter" else "dimmer"
    is VoiceCommand.BrightnessTo -> "brightness ${command.percent}"
    is VoiceCommand.SpeedTo -> "speed ${command.speed}x"
    is VoiceCommand.SpeedBy -> if (command.delta >= 0) "faster" else "slower"
    is VoiceCommand.PlayTitle -> "play ${command.query}"
}

/**
 * Turns the words heard into something to do. The wake phrase is not needed
 * because the person tapped the microphone. A bare film name with no "play"
 * is accepted too, but only if it clearly matches a film.
 */
internal fun resolveTalk(transcript: String, films: List<TitleCandidate>): TalkOutcome {
    val cleaned = cleanTranscript(transcript) ?: return TalkOutcome.Silence
    return when (val result = parseVoiceCommand(cleaned, wakeAlreadyHeard = true)) {
        is VoiceResult.NeedsScreen -> TalkOutcome.NeedsScreen(result.action)
        is VoiceResult.Command -> when (val c = result.command) {
            is VoiceCommand.PlayTitle -> titleOutcome(c.query, films)
            else -> TalkOutcome.PlayerCommand(describeCommand(c))
        }
        is VoiceResult.NotUnderstood, VoiceResult.NoWakeWord -> {
            when (val choice = chooseTitle(cleaned, films)) {
                is TitleChoice.Play -> TalkOutcome.PlayFilm(choice.match)
                is TitleChoice.Choose -> TalkOutcome.PickFilm(cleaned, choice.options)
                TitleChoice.NotFound -> TalkOutcome.NotUnderstood(cleaned)
            }
        }
    }
}

private fun titleOutcome(query: String, films: List<TitleCandidate>): TalkOutcome =
    when (val choice = chooseTitle(query, films)) {
        is TitleChoice.Play -> TalkOutcome.PlayFilm(choice.match)
        is TitleChoice.Choose -> TalkOutcome.PickFilm(query, choice.options)
        TitleChoice.NotFound -> TalkOutcome.FilmNotFound(query)
    }

/**
 * Decides when a tap-to-talk recording is finished: shortly after the person
 * stops speaking, or after a maximum time, or if nobody spoke at all.
 */
internal class SpeechEndpoint(
    private val windowMs: Int = 32,
    private val maxWaitForSpeechMs: Int = 5000,
    private val maxTotalMs: Int = 8000,
    private val endSilenceMs: Int = 800,
    private val minSpeechMs: Int = 250,
    private val speechProbability: Float = 0.5f
) {
    enum class Status { WaitingForSpeech, Speaking, Done, NoSpeech }

    private var elapsedMs = 0
    private var speechMs = 0
    private var silenceRunMs = 0
    private var started = false

    fun update(probability: Float): Status {
        elapsedMs += windowMs
        if (probability >= speechProbability) {
            speechMs += windowMs
            silenceRunMs = 0
            if (speechMs >= minSpeechMs) started = true
        } else if (started) {
            silenceRunMs += windowMs
        }
        return when {
            started && silenceRunMs >= endSilenceMs -> Status.Done
            elapsedMs >= maxTotalMs -> if (started) Status.Done else Status.NoSpeech
            !started && elapsedMs >= maxWaitForSpeechMs -> Status.NoSpeech
            started -> Status.Speaking
            else -> Status.WaitingForSpeech
        }
    }
}
