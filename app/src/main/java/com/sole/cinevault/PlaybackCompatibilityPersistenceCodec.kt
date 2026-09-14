package com.sole.cinevault

/**
 * Small versioned persistence codec for compatibility-matrix entries.
 *
 * This deliberately avoids Android/JSON dependencies so persistence can be
 * round-trip tested on the JVM. Every text cell is escaped, and malformed
 * rows are ignored instead of breaking player startup.
 */
private const val COMPATIBILITY_STORE_VERSION = "CVCOMPAT2"
private const val LEGACY_COMPATIBILITY_STORE_VERSION = "CVCOMPAT1"
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
                entry.observation.audioMimeType.orEmpty(),
                entry.observation.audioCodecString.orEmpty(),
                entry.observation.audioLanguage.orEmpty(),
                entry.observation.audioDecoderName.orEmpty(),
                entry.observation.audioDecoderKind.name,
                entry.observation.audioRoute?.name.orEmpty(),
                entry.observation.mixedPipeline.toString(),
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
    val version = lines.firstOrNull()
    if (
        version != COMPATIBILITY_STORE_VERSION &&
        version != LEGACY_COMPATIBILITY_STORE_VERSION
    ) {
        return emptyList()
    }

    return lines
        .drop(1)
        .mapNotNull { line ->
            decodeCompatibilityEntry(
                line = line,
                version = version,
            )
        }
}

private fun decodeCompatibilityEntry(
    line: String,
    version: String?,
): PlaybackCompatibilityMatrixEntry? {
    val fields = splitCompatibilityFields(line)
    val expectedFields = if (
        version == LEGACY_COMPATIBILITY_STORE_VERSION
    ) {
        25
    } else {
        32
    }
    if (fields.size != expectedFields) {
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
            audioMimeType =
                if (fields.size > 25) fields[24].ifEmpty { null } else null,
            audioCodecString =
                if (fields.size > 25) fields[25].ifEmpty { null } else null,
            audioLanguage =
                if (fields.size > 25) fields[26].ifEmpty { null } else null,
            audioDecoderName =
                if (fields.size > 25) fields[27].ifEmpty { null } else null,
            audioDecoderKind =
                if (fields.size > 25) {
                    enumValueOf<ActiveAudioDecoderKind>(fields[28])
                } else {
                    ActiveAudioDecoderKind.UNKNOWN
                },
            audioRoute =
                if (fields.size > 25) {
                    fields[29]
                        .takeIf { it.isNotEmpty() }
                        ?.let { enumValueOf<PlaybackStreamRoute>(it) }
                } else {
                    null
                },
            mixedPipeline =
                if (fields.size > 25) parseStrictBoolean(fields[30]) else false,
        )

        PlaybackCompatibilityMatrixEntry(
            testCase = testCase,
            device = device,
            observation = observation,
            verdict =
                enumValueOf<PlaybackCompatibilityVerdict>(
                    if (fields.size > 25) fields[31] else fields[24]
                ),
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
