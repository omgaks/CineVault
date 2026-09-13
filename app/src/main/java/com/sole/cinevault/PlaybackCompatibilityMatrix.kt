package com.sole.cinevault

/**
 * One device identity for CineVault compatibility-matrix results.
 *
 * Values are passed in by the caller rather than read directly from
 * android.os.Build so the matrix model stays deterministic and unit-testable.
 */
data class PlaybackCompatibilityDevice(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
)

/**
 * Stable identity for one torture-file / compatibility test case.
 *
 * The optional sourceLabel can be a friendly filename or suite label, while
 * testId should stay stable across devices so results can be compared.
 */
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
    "verdict",
)

/**
 * TSV is intentional: filenames and decoder names frequently contain commas,
 * so tab-separated rows are easier to inspect in logs and paste into Excel.
 */
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
    value
        .orEmpty()
        .replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')

private fun formatMatrixFrameRate(frameRate: Float): String =
    String.format(
        java.util.Locale.US,
        "%.3f",
        frameRate,
    ).trimEnd('0').trimEnd('.')
