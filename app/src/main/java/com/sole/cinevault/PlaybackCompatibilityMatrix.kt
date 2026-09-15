package com.sole.cinevault

data class PlaybackCompatibilityDevice(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
)

data class PlaybackCompatibilityTestCase(
    val testId: String,
    val sourceLabel: String? = null,
)

enum class PlaybackCompatibilityVerdict {
    PENDING,
    PASS_NATIVE,
    PASS_SOFTWARE_RESCUE,
    FAIL_UNSTABLE,
}

data class PlaybackCompatibilityMatrixEntry(
    val testCase: PlaybackCompatibilityTestCase,
    val device: PlaybackCompatibilityDevice,
    val observation: PlaybackCompatibilityObservation,
    val verdict: PlaybackCompatibilityVerdict,
)

fun buildPlaybackCompatibilityMatrixEntry(
    testCase: PlaybackCompatibilityTestCase,
    device: PlaybackCompatibilityDevice,
    observation: PlaybackCompatibilityObservation,
): PlaybackCompatibilityMatrixEntry {
    val verdict = when (observation.outcome) {
        PlaybackCompatibilityOutcome.STARTING ->
            PlaybackCompatibilityVerdict.PENDING
        PlaybackCompatibilityOutcome.NATIVE_HEALTHY ->
            PlaybackCompatibilityVerdict.PASS_NATIVE
        PlaybackCompatibilityOutcome.SOFTWARE_RESCUED ->
            PlaybackCompatibilityVerdict.PASS_SOFTWARE_RESCUE
        PlaybackCompatibilityOutcome.NATIVE_UNSTABLE ->
            PlaybackCompatibilityVerdict.FAIL_UNSTABLE
    }

    return PlaybackCompatibilityMatrixEntry(
        testCase = testCase,
        device = device,
        observation = observation,
        verdict = verdict,
    )
}

private val matrixColumns = listOf(
    "test_id",
    "source",
    "device",
    "sdk",
    "codec",
    "profile",
    "bit_depth",
    "level",
    "resolution",
    "fps",
    "dynamic_range",
    "decoder",
    "decoder_kind",
    "risk",
    "readiness",
    "software_rescue",
    "dropped_frames",
    "unhealthy_windows",
    "fallback_reason",
    "audio_codec",
    "audio_language",
    "audio_decoder",
    "audio_decoder_kind",
    "audio_route",
    "mixed_pipeline",
    "terminal_failure_stream",
    "terminal_failure_error",
    "verdict",
)

fun playbackCompatibilityMatrixHeader(): String =
    matrixColumns.joinToString("\t")

fun formatPlaybackCompatibilityMatrixRow(
    entry: PlaybackCompatibilityMatrixEntry,
): String {
    val observation = entry.observation
    val key = observation.key

    return listOf(
        sanitizeMatrixCell(entry.testCase.testId),
        sanitizeMatrixCell(entry.testCase.sourceLabel),
        sanitizeMatrixCell(
            "${entry.device.manufacturer} ${entry.device.model}".trim()
        ),
        entry.device.sdkInt.toString(),
        sanitizeMatrixCell(key.codecLabel),
        sanitizeMatrixCell(key.profileLabel),
        key.bitDepth?.toString().orEmpty(),
        sanitizeMatrixCell(key.levelLabel),
        sanitizeMatrixCell(key.resolution),
        key.frameRate?.let(::formatMatrixFrameRate).orEmpty(),
        key.dynamicRange.name,
        sanitizeMatrixCell(observation.decoderName),
        observation.decoderKind.name,
        observation.compatibilityRisk.name,
        observation.nativeReadiness.name,
        observation.softwareFallbackAvailable.toString(),
        observation.totalDroppedVideoFrames.toString(),
        observation.unhealthyDroppedFrameWindows.toString(),
        observation.fallbackReason?.name.orEmpty(),
        sanitizeMatrixCell(
            assessAudioStreamCapability(
                observation.audioMimeType?.let {
                    PlaybackStreamDescriptor(
                        kind = PlaybackStreamKind.AUDIO,
                        mimeType = observation.audioMimeType,
                        codecString = observation.audioCodecString,
                        language = observation.audioLanguage,
                        selected = true,
                    )
                }
            )?.codecLabel
        ),
        sanitizeMatrixCell(observation.audioLanguage),
        sanitizeMatrixCell(observation.audioDecoderName),
        observation.audioDecoderKind.name,
        observation.audioRoute?.name.orEmpty(),
        observation.mixedPipeline.toString(),
        observation.terminalFailureStream?.name.orEmpty(),
        observation.terminalFailureErrorCode?.toString().orEmpty(),
        entry.verdict.name,
    ).joinToString("\t")
}

fun formatPlaybackCompatibilityMatrixReport(
    entries: List<PlaybackCompatibilityMatrixEntry>,
): String = buildString {
    append(playbackCompatibilityMatrixHeader())
    entries.forEach { entry ->
        append('\n')
        append(formatPlaybackCompatibilityMatrixRow(entry))
    }
}

private fun sanitizeMatrixCell(value: String?): String =
    value.orEmpty()
        .replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')

private fun formatMatrixFrameRate(frameRate: Float): String =
    String.format(
        java.util.Locale.US,
        "%.3f",
        frameRate,
    ).trimEnd('0').trimEnd('.')
