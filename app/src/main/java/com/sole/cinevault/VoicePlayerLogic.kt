package com.sole.cinevault

import kotlin.math.roundToInt

/** Voice 2c-2: the sums behind player voice commands. Pure so they can be tested. */

/** Skip forward or back, always staying inside the film. */
internal fun seekTargetMs(currentMs: Long, durationMs: Long, deltaMs: Long): Long {
    val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return (currentMs + deltaMs).coerceIn(0L, upper)
}

/** Jump to a time. Past the end goes to the end. */
internal fun jumpTargetMs(positionMs: Long, durationMs: Long): Long {
    val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return positionMs.coerceIn(0L, upper)
}

internal fun volumeTargetPercent(current: Int, command: VoiceCommand): Int? = when (command) {
    is VoiceCommand.VolumeBy -> (current + command.percent).coerceIn(0, 100)
    is VoiceCommand.VolumeTo -> command.percent.coerceIn(0, 100)
    else -> null
}

/** Brightness never goes fully black by voice, so the screen can't be lost. */
internal const val MIN_VOICE_BRIGHTNESS = 5

internal fun brightnessTargetPercent(current: Int, command: VoiceCommand): Int? = when (command) {
    is VoiceCommand.BrightnessBy -> (current + command.percent).coerceIn(MIN_VOICE_BRIGHTNESS, 100)
    is VoiceCommand.BrightnessTo -> command.percent.coerceIn(MIN_VOICE_BRIGHTNESS, 100)
    else -> null
}

internal fun speedTarget(current: Float, command: VoiceCommand): Float? {
    val raw = when (command) {
        is VoiceCommand.SpeedTo -> command.speed
        is VoiceCommand.SpeedBy -> current + command.delta
        else -> return null
    }
    return ((raw.coerceIn(0.25f, 3.0f)) * 100f).roundToInt() / 100f
}

/** Level the film's sound drops to while the microphone is listening. */
internal const val DUCK_VOLUME = 0.2f

