package com.sole.cinevault

/**
 * Voice stage 1: turns what was heard ("Vault, skip back thirty seconds")
 * into a command. Pure text in, command out. It has no microphone, no
 * permission and no Android types, so it is safe to test on its own.
 *
 * Rules baked in here:
 *  - A command only counts if it starts with the wake word.
 *  - Risky words (delete, remove, erase) are never turned into a command.
 *    The answer is "do that on screen".
 */
internal sealed interface VoiceCommand {
    object Play : VoiceCommand
    object Pause : VoiceCommand
    object Mute : VoiceCommand
    object Unmute : VoiceCommand
    object NextEpisode : VoiceCommand
    object PreviousEpisode : VoiceCommand
    object SubtitlesOn : VoiceCommand
    object SubtitlesOff : VoiceCommand
    object FitScreen : VoiceCommand
    object FillScreen : VoiceCommand
    object ExitPlayer : VoiceCommand

    /** Positive skips forward, negative skips back. */
    /** "play Avengers Endgame": the words after "play", to be matched against the library. */
    data class PlayTitle(val query: String) : VoiceCommand
    data class SkipSeconds(val seconds: Int) : VoiceCommand
    data class JumpToMs(val positionMs: Long) : VoiceCommand
    data class VolumeBy(val percent: Int) : VoiceCommand
    data class VolumeTo(val percent: Int) : VoiceCommand
    data class BrightnessBy(val percent: Int) : VoiceCommand
    data class BrightnessTo(val percent: Int) : VoiceCommand
    data class SpeedTo(val speed: Float) : VoiceCommand
    data class SpeedBy(val delta: Float) : VoiceCommand
}

internal sealed interface VoiceResult {
    data class Command(val command: VoiceCommand) : VoiceResult
    /** A risky request. It is never run by voice alone. */
    data class NeedsScreen(val action: String) : VoiceResult
    object NoWakeWord : VoiceResult
    data class NotUnderstood(val heard: String) : VoiceResult
}

internal const val VOICE_WAKE_WORD = "hey cinevault"

/**
 * Every spoken form of a wake phrase we accept, so changing the chosen wake
 * word never breaks parsing. Recognisers often split or mishear the name, so
 * common variants are listed. Only valid at the very start.
 */
private val WAKE_PHRASES: List<List<String>> = listOf(
    listOf("cinevault"), listOf("cine", "vault"), listOf("sinevault"), listOf("sine", "vault"),
    listOf("cinema", "vault"), listOf("vault"), listOf("fault"), listOf("volt"), listOf("bolt")
)
private val WAKE_PREFIXES = setOf("hey", "ok", "okay")
/** Words that only ever mean "go to the next/previous one". Anything else means it is part of a title. */
private val NAVIGATION_WORDS = setOf(
    "next", "previous", "episode", "one", "track", "video", "file", "please", "play", "skip", "go", "to", "the", "movie"
)
private val TITLE_LEAD_WORDS = setOf("the", "movie", "film", "show", "series", "me", "some")
private val PLAY_CONTROL_WORDS = setOf("from", "again", "it", "this", "that")
private val LEADING_FILLER = listOf(
    listOf("please"), listOf("can", "you"), listOf("could", "you"), listOf("would", "you"), listOf("just")
)
private val TRAILING_FILLER = setOf("please", "now")
private val RISKY_WORDS = setOf("delete", "remove", "erase", "wipe", "uninstall")

private const val DEFAULT_SKIP_SECONDS = 10
private const val MAX_SKIP_SECONDS = 4 * 60 * 60
private const val DEFAULT_LEVEL_STEP = 10
private const val SPEED_STEP = 0.25f
private const val MIN_SPEED = 0.5f
private const val MAX_SPEED = 2.0f

// ── Public entry point ────────────────────────────────────────────────────

/**
 * [wakeAlreadyHeard] is true when the wake-word listener already caught the
 * wake phrase, so the transcript that follows may not repeat it.
 */
internal fun parseVoiceCommand(heard: String, wakeAlreadyHeard: Boolean = false): VoiceResult {
    val all = tokenize(heard)
    val afterWake = stripWakeWord(all) ?: if (wakeAlreadyHeard) all else return VoiceResult.NoWakeWord
    val t = stripFiller(afterWake)
    if (t.isEmpty()) return VoiceResult.NotUnderstood(heard.trim())

    t.firstOrNull { it in RISKY_WORDS }?.let { return VoiceResult.NeedsScreen(it) }

    val command = matchCommand(t) ?: return VoiceResult.NotUnderstood(heard.trim())
    return VoiceResult.Command(command)
}

// ── Cleaning ──────────────────────────────────────────────────────────────

private fun tokenize(raw: String): List<String> =
    raw.lowercase()
        .replace("%", " percent")
        .replace(Regex("[-_]"), " ")
        .replace(Regex("[^a-z0-9.: ]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim('.', ':') }
        .filter { it.isNotEmpty() }

private fun stripWakeWord(t: List<String>): List<String>? {
    if (t.isEmpty()) return null
    val body = if (t[0] in WAKE_PREFIXES) t.drop(1) else t
    for (phrase in WAKE_PHRASES) {
        if (body.size >= phrase.size && body.take(phrase.size) == phrase) return body.drop(phrase.size)
    }
    return null
}

private fun stripFiller(t: List<String>): List<String> {
    var out = t
    var changed = true
    while (changed) {
        changed = false
        for (f in LEADING_FILLER) {
            if (out.size > f.size && out.take(f.size) == f) { out = out.drop(f.size); changed = true }
        }
    }
    while (out.isNotEmpty() && out.last() in TRAILING_FILLER) out = out.dropLast(1)
    return out
}

// ── Numbers and durations ─────────────────────────────────────────────────

private val ONES = mapOf(
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
    "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
    "eighteen" to 18, "nineteen" to 19
)
private val TENS = mapOf(
    "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
)
private val DIGITS = Regex("\\d+(\\.\\d+)?")

/** Reads "45", "forty five", "one hundred and five" or "one point five". Returns value and next index. */
private fun readNumber(t: List<String>, start: Int): Pair<Double, Int>? {
    if (start >= t.size) return null
    val first = t[start]
    if (DIGITS.matches(first)) return first.toDouble() to (start + 1)

    var i = start
    var total = 0
    var current = 0
    var sawAny = false
    while (i < t.size) {
        val w = t[i]
        when {
            w in ONES -> { current += ONES.getValue(w); sawAny = true }
            w in TENS -> { current += TENS.getValue(w); sawAny = true }
            w == "hundred" && sawAny -> { current = (if (current == 0) 1 else current) * 100 }
            w == "and" && sawAny && i + 1 < t.size && (t[i + 1] in ONES || t[i + 1] in TENS) -> Unit
            else -> break
        }
        i++
    }
    if (!sawAny) return null
    total += current
    var value = total.toDouble()
    // "one point five"
    if (i + 1 < t.size && t[i] == "point") {
        var j = i + 1
        val digits = StringBuilder()
        while (j < t.size && (t[j] in ONES && ONES.getValue(t[j]) < 10 || (t[j].length == 1 && t[j][0].isDigit()))) {
            digits.append(ONES[t[j]] ?: t[j]); j++
        }
        if (digits.isNotEmpty()) { value = "$total.$digits".toDouble(); i = j }
    }
    return value to i
}

private fun unitMs(word: String): Long? = when (word) {
    "hour", "hours", "hr", "hrs", "h" -> 3_600_000L
    "minute", "minutes", "min", "mins" -> 60_000L
    "second", "seconds", "sec", "secs" -> 1_000L
    else -> null
}

private val CLOCK = Regex("(\\d+):(\\d{2})(?::(\\d{2}))?")

/**
 * Reads a length of time: "30 seconds", "an hour and a half", "1 hour 5 minutes",
 * "1:05:30". A bare number uses [bareUnitMs]. Returns milliseconds and the next index.
 */
private fun readDuration(t: List<String>, start: Int, bareUnitMs: Long): Pair<Long, Int>? {
    if (start >= t.size) return null
    CLOCK.matchEntire(t[start])?.let { m ->
        val a = m.groupValues[1].toLong()
        val b = m.groupValues[2].toLong()
        val c = m.groupValues[3]
        val ms = if (c.isEmpty()) (a * 60 + b) * 1000 else ((a * 60 + b) * 60 + c.toLong()) * 1000
        return ms to (start + 1)
    }
    var i = start
    var totalMs = 0L
    var found = false
    var lastUnit = 0L
    while (i < t.size) {
        // "half an hour"
        if (t[i] == "half") {
            var j = i + 1
            if (j < t.size && (t[j] == "an" || t[j] == "a")) j++
            val u = if (j < t.size) unitMs(t[j]) else null
            if (u != null) { totalMs += u / 2; found = true; i = j + 1; lastUnit = u; continue }
            break
        }
        // "and a half" after a unit
        if (found && t[i] == "and" && i + 2 < t.size && t[i + 1] == "a" && t[i + 2] == "half" && lastUnit > 0) {
            totalMs += lastUnit / 2; i += 3; lastUnit = 0; continue
        }
        if (found && t[i] == "and") { i++; continue }

        val (amount, afterNum) = when {
            t[i] == "a" || t[i] == "an" -> 1.0 to (i + 1)
            else -> readNumber(t, i) ?: break
        }
        val u = if (afterNum < t.size) unitMs(t[afterNum]) else null
        if (u != null) {
            totalMs += (amount * u).toLong(); found = true; i = afterNum + 1; lastUnit = u
        } else if (t[i] != "a" && t[i] != "an" && !found) {
            totalMs += (amount * bareUnitMs).toLong(); found = true; i = afterNum; lastUnit = bareUnitMs
            break
        } else break
    }
    return if (found) totalMs to i else null
}

private fun firstNumber(t: List<String>, from: Int = 0): Double? {
    for (i in from until t.size) readNumber(t, i)?.let { return it.first }
    return null
}

// ── Matching ──────────────────────────────────────────────────────────────

private fun matchCommand(t: List<String>): VoiceCommand? {
    val words = t.toSet()
    val first = t.first()

    if ("unmute" in words) return VoiceCommand.Unmute
    if ("mute" in words) return VoiceCommand.Mute

    if (words.any { it == "subtitles" || it == "subtitle" || it == "captions" || it == "caption" }) {
        return when {
            words.any { it == "off" || it == "hide" || it == "disable" } -> VoiceCommand.SubtitlesOff
            words.any { it == "on" || it == "show" || it == "enable" } -> VoiceCommand.SubtitlesOn
            else -> null
        }
    }

    if (words.all { it in NAVIGATION_WORDS }) {
        if ("next" in words) return VoiceCommand.NextEpisode
        if ("previous" in words) return VoiceCommand.PreviousEpisode
    }

    // "go to 45 minutes", "jump to one hour five"
    if (first in setOf("go", "jump", "skip", "seek") && t.getOrNull(1) == "to") {
        val idx = (2 until t.size).firstOrNull { it >= 2 && readDuration(t, it, 60_000L) != null }
        if (idx != null) {
            val (ms, _) = readDuration(t, idx, 60_000L)!!
            return VoiceCommand.JumpToMs(ms)
        }
        return null
    }

    levelCommand(t, words, "volume", louder = setOf("louder"), quieter = setOf("quieter", "softer"),
        by = { VoiceCommand.VolumeBy(it) }, to = { VoiceCommand.VolumeTo(it) })?.let { return it }
    levelCommand(t, words, "brightness", louder = setOf("brighter"), quieter = setOf("dimmer", "darker"),
        by = { VoiceCommand.BrightnessBy(it) }, to = { VoiceCommand.BrightnessTo(it) })?.let { return it }
    // "turn it up / down" means volume
    if ("turn" in words && "up" in words) return VoiceCommand.VolumeBy(DEFAULT_LEVEL_STEP)
    if ("turn" in words && "down" in words) return VoiceCommand.VolumeBy(-DEFAULT_LEVEL_STEP)

    speedCommand(t, words)?.let { return it }

    if ("fit" in words) return VoiceCommand.FitScreen
    if ("fill" in words || "zoom" in words) return VoiceCommand.FillScreen

    skipCommand(t, first, words)?.let { return it }

    return when (first) {
        "play", "watch" -> playOrTitle(t.drop(1))
        "put" -> if (t.getOrNull(1) == "on") playOrTitle(t.drop(2)).takeIf { it != VoiceCommand.Play } else null
        "resume", "continue", "start", "unpause" -> VoiceCommand.Play
        "pause", "stop", "freeze" -> VoiceCommand.Pause
        "hold" -> if (t.getOrNull(1) == "on") VoiceCommand.Pause else null
        "exit", "close", "leave", "quit" -> VoiceCommand.ExitPlayer
        else -> null
    }
}

private fun levelCommand(
    t: List<String>,
    words: Set<String>,
    noun: String,
    louder: Set<String>,
    quieter: Set<String>,
    by: (Int) -> VoiceCommand,
    to: (Int) -> VoiceCommand
): VoiceCommand? {
    val up = words.any { it in louder } || (noun in words && "up" in words)
    val down = words.any { it in quieter } || (noun in words && "down" in words)
    val number = firstNumber(t)?.toInt()?.coerceIn(0, 100)
    return when {
        up -> by(number ?: DEFAULT_LEVEL_STEP)
        down -> by(-(number ?: DEFAULT_LEVEL_STEP))
        noun in words && number != null -> to(number)
        else -> null
    }
}

private fun speedCommand(t: List<String>, words: Set<String>): VoiceCommand? {
    val sawSpeedWord = "speed" in words || "faster" in words || "slower" in words
    val xToken = t.firstOrNull { it.length > 1 && it.endsWith("x") && DIGITS.matches(it.dropLast(1)) }
    if (!sawSpeedWord && xToken == null) return null
    // "play Speed 2" is a film, "play at 2x" is a speed.
    if ((t.first() == "play" || t.first() == "watch") && "at" !in words && xToken == null) return null
    if ("normal" in words) return VoiceCommand.SpeedTo(1.0f)
    if ("faster" in words) return VoiceCommand.SpeedBy(SPEED_STEP)
    if ("slower" in words) return VoiceCommand.SpeedBy(-SPEED_STEP)
    val n = xToken?.dropLast(1)?.toDouble() ?: firstNumber(t)
    return n?.let { VoiceCommand.SpeedTo(it.toFloat().coerceIn(MIN_SPEED, MAX_SPEED)) }
}

private fun skipCommand(t: List<String>, first: String, words: Set<String>): VoiceCommand? {
    val backward = words.any { it == "back" || it == "backward" || it == "backwards" || it == "rewind" }
    val forward = words.any { it == "forward" || it == "forwards" || it == "ahead" || it == "skip" || it == "fast" }
    val startsLikeSkip = first in setOf("skip", "forward", "forwards", "ahead", "rewind", "back", "backward", "backwards", "fast", "jump", "go")
    if (!startsLikeSkip) return null
    if (!backward && !forward) return null

    val amountMs = (1 until t.size).firstNotNullOfOrNull { readDuration(t, it, 1_000L)?.first }
    val seconds = if (amountMs != null) (amountMs / 1000).toInt() else DEFAULT_SKIP_SECONDS
    val clamped = seconds.coerceIn(1, MAX_SKIP_SECONDS)
    return VoiceCommand.SkipSeconds(if (backward) -clamped else clamped)
}

/** Bare "play" resumes. "play <something>" asks for a film by name. */
private fun playOrTitle(after: List<String>): VoiceCommand {
    var rest = after
    while (rest.isNotEmpty() && rest.first() in TITLE_LEAD_WORDS) rest = rest.drop(1)
    if (rest.isEmpty() || rest.first() in PLAY_CONTROL_WORDS) return VoiceCommand.Play
    return VoiceCommand.PlayTitle(rest.joinToString(" "))
}
