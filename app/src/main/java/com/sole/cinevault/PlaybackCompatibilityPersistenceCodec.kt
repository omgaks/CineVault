package com.sole.cinevault

/**
 * Small versioned persistence codec for compatibility-matrix entries.
 *
 * This deliberately avoids Android/JSON dependencies so persistence can be
 * round-trip tested on the JVM. Every text cell is escaped, and malformed
 * rows are ignored instead of breaking player startup.
 */
private const val COMPATIBILITY_STORE_VERSION = "CVCOMPAT1"
private const val COMPATIBILITY_FIELD_SEPARATOR = '\u001F'

fun encodePlaybackCompatibilityEntries(
    entries: List<PlaybackCompatibilityMatrixEntry>,
): String = buildString {
    append(COMPATIBILITY_STORE_VERSION)

    entries.forEach { entry ->
        append('\n')
        append(
            listOf(
                entry.testCase.testId,
                entry.testCase.sourceLabel.orEmpty(),
                entry.device.manufacturer,
                entry.device.model,
                entry.device.sdkInt.toString(),
                entry.observation.key.mimeType.orEmpty(),
                entry.observation.key.codecLabel.orEmpty(),
                entry.observation.key.profileLabel.orEmpty(),
                entry.observation.key.levelLabel.orEmpty(),
                entry.observation.key.bitDepth?.toString().orEmpty(),
                entry.observation.key.resolution,
                entry.observation.key.frameRate?.toString().orEmpty(),
                entry.observation.key.dynamicRange.name,
                entry.observation.outcome.name,
                entry.observation.decoderName.orEmpty(),
                entry.observation.decoderKind.name,
                entry.observation.compatibilityRisk.name,
                entry.observation.recommendation.name,
                entry.observation.nativeReadiness.name,
                entry.observation.softwareFallbackAvailable.toString(),
                entry.observation.fallbackOccurred.toString(),
                entry.observation.fallbackReason?.name.orEmpty(),
                entry.observation.totalDroppedVideoFrames.toString(),
                entry.observation.unhealthyDroppedFrameWindows.toString(),
                entry.verdict.name,
            ).joinToString(
                separator = COMPATIBILITY_FIELD_SEPARATOR.toString(),
                transform = ::escapeCompatibilityField,
            )
        )
    }
}

fun decodePlaybackCompatibilityEntries(
    encoded: String?,
): List<PlaybackCompatibilityMatrixEntry> {
    if (encoded.isNullOrBlank()) {
        return emptyList()
    }

    val lines = encoded.lineSequence().toList()
    if (lines.firstOrNull() != COMPATIBILITY_STORE_VERSION) {
        return emptyList()
    }

    return lines
        .drop(1)
        .mapNotNull(::decodeCompatibilityEntry)
}

private fun decodeCompatibilityEntry(
    line: String,
): PlaybackCompatibilityMatrixEntry? {
    val fields = splitCompatibilityFields(line)
    if (fields.size != 25) {
        return null
    }

    return runCatching {
        val testCase = PlaybackCompatibilityTestCase(
            testId = fields[0],
            sourceLabel = fields[1].ifEmpty { null },
        )

        val device = PlaybackCompatibilityDevice(
            manufacturer = fields[2],
            model = fields[3],
            sdkInt = fields[4].toInt(),
        )

        val key = PlaybackCompatibilityKey(
            mimeType = fields[5].ifEmpty { null },
            codecLabel = fields[6].ifEmpty { null },
            profileLabel = fields[7].ifEmpty { null },
            levelLabel = fields[8].ifEmpty { null },
            bitDepth = fields[9].toIntOrNull(),
            resolution = fields[10],
            frameRate = fields[11].toFloatOrNull(),
            dynamicRange = enumValueOf<VideoDynamicRange>(fields[12]),
        )

        val observation = PlaybackCompatibilityObservation(
            key = key,
            outcome =
                enumValueOf<PlaybackCompatibilityOutcome>(fields[13]),
            decoderName = fields[14].ifEmpty { null },
            decoderKind =
                enumValueOf<ActiveVideoDecoderKind>(fields[15]),
            compatibilityRisk =
                enumValueOf<VideoCompatibilityRisk>(fields[16]),
            recommendation =
                enumValueOf<VideoDecoderRecommendation>(fields[17]),
            nativeReadiness =
                enumValueOf<NativeVideoPlaybackReadiness>(fields[18]),
            softwareFallbackAvailable =
                parseStrictBoolean(fields[19]),
            fallbackOccurred =
                parseStrictBoolean(fields[20]),
            fallbackReason = fields[21]
                .takeIf { it.isNotEmpty() }
                ?.let { enumValueOf<PlaybackFallbackReason>(it) },
            totalDroppedVideoFrames = fields[22].toInt(),
            unhealthyDroppedFrameWindows = fields[23].toInt(),
        )

        PlaybackCompatibilityMatrixEntry(
            testCase = testCase,
            device = device,
            observation = observation,
            verdict =
                enumValueOf<PlaybackCompatibilityVerdict>(fields[24]),
        )
    }.getOrNull()
}


private fun parseStrictBoolean(
    value: String,
): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> error("Invalid boolean: $value")
}

private fun escapeCompatibilityField(
    value: String,
): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            COMPATIBILITY_FIELD_SEPARATOR -> append("\\u001f")
            else -> append(char)
        }
    }
}

private fun splitCompatibilityFields(
    line: String,
): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var index = 0

    while (index < line.length) {
        val char = line[index]

        if (char == COMPATIBILITY_FIELD_SEPARATOR) {
            result += current.toString()
            current.clear()
            index++
            continue
        }

        if (char == '\\' && index + 1 < line.length) {
            when {
                line.startsWith("\\u001f", index) -> {
                    current.append(COMPATIBILITY_FIELD_SEPARATOR)
                    index += 6
                    continue
                }

                line[index + 1] == '\\' -> {
                    current.append('\\')
                    index += 2
                    continue
                }

                line[index + 1] == 'n' -> {
                    current.append('\n')
                    index += 2
                    continue
                }

                line[index + 1] == 'r' -> {
                    current.append('\r')
                    index += 2
                    continue
                }
            }
        }

        current.append(char)
        index++
    }

    result += current.toString()
    return result
}
