package com.sole.cinevault

private const val COMPATIBILITY_STORE_VERSION = "CVCOMPAT3"
private const val LEGACY_COMPATIBILITY_STORE_VERSION_V2 = "CVCOMPAT2"
private const val LEGACY_COMPATIBILITY_STORE_VERSION_V1 = "CVCOMPAT1"
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
                entry.observation.terminalFailureStream?.name.orEmpty(),
                entry.observation.terminalFailureErrorCode?.toString().orEmpty(),
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
    if (encoded.isNullOrBlank()) return emptyList()

    val lines = encoded.lineSequence().toList()
    val version = lines.firstOrNull()
    if (
        version != COMPATIBILITY_STORE_VERSION &&
        version != LEGACY_COMPATIBILITY_STORE_VERSION_V2 &&
        version != LEGACY_COMPATIBILITY_STORE_VERSION_V1
    ) {
        return emptyList()
    }

    return lines.drop(1).mapNotNull { line ->
        decodeCompatibilityEntry(line, version)
    }
}

private fun decodeCompatibilityEntry(
    line: String,
    version: String?,
): PlaybackCompatibilityMatrixEntry? {
    val fields = splitCompatibilityFields(line)
    val expectedFields = when (version) {
        LEGACY_COMPATIBILITY_STORE_VERSION_V1 -> 25
        LEGACY_COMPATIBILITY_STORE_VERSION_V2 -> 32
        COMPATIBILITY_STORE_VERSION -> 34
        else -> return null
    }
    if (fields.size != expectedFields) return null

    return runCatching {
        val isV1 = version == LEGACY_COMPATIBILITY_STORE_VERSION_V1
        val isV3 = version == COMPATIBILITY_STORE_VERSION

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
            outcome = enumValueOf<PlaybackCompatibilityOutcome>(fields[13]),
            decoderName = fields[14].ifEmpty { null },
            decoderKind = enumValueOf<ActiveVideoDecoderKind>(fields[15]),
            compatibilityRisk = enumValueOf<VideoCompatibilityRisk>(fields[16]),
            recommendation = enumValueOf<VideoDecoderRecommendation>(fields[17]),
            nativeReadiness = enumValueOf<NativeVideoPlaybackReadiness>(fields[18]),
            softwareFallbackAvailable = parseStrictBoolean(fields[19]),
            fallbackOccurred = parseStrictBoolean(fields[20]),
            fallbackReason = fields[21]
                .takeIf { it.isNotEmpty() }
                ?.let { enumValueOf<PlaybackFallbackReason>(it) },
            totalDroppedVideoFrames = fields[22].toInt(),
            unhealthyDroppedFrameWindows = fields[23].toInt(),
            audioMimeType = if (!isV1) fields[24].ifEmpty { null } else null,
            audioCodecString = if (!isV1) fields[25].ifEmpty { null } else null,
            audioLanguage = if (!isV1) fields[26].ifEmpty { null } else null,
            audioDecoderName = if (!isV1) fields[27].ifEmpty { null } else null,
            audioDecoderKind = if (!isV1) {
                enumValueOf<ActiveAudioDecoderKind>(fields[28])
            } else {
                ActiveAudioDecoderKind.UNKNOWN
            },
            audioRoute = if (!isV1) {
                fields[29].takeIf { it.isNotEmpty() }
                    ?.let { enumValueOf<PlaybackStreamRoute>(it) }
            } else {
                null
            },
            mixedPipeline = if (!isV1) parseStrictBoolean(fields[30]) else false,
            terminalFailureStream = if (isV3) {
                fields[31].takeIf { it.isNotEmpty() }
                    ?.let { enumValueOf<PlaybackFailureStreamKind>(it) }
            } else {
                null
            },
            terminalFailureErrorCode = if (isV3) fields[32].toIntOrNull() else null,
        )

        val verdictField = when (version) {
            LEGACY_COMPATIBILITY_STORE_VERSION_V1 -> fields[24]
            LEGACY_COMPATIBILITY_STORE_VERSION_V2 -> fields[31]
            else -> fields[33]
        }

        PlaybackCompatibilityMatrixEntry(
            testCase = testCase,
            device = device,
            observation = observation,
            verdict = enumValueOf<PlaybackCompatibilityVerdict>(verdictField),
        )
    }.getOrNull()
}

private fun parseStrictBoolean(value: String): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> error("Invalid boolean: $value")
}

private fun escapeCompatibilityField(value: String): String =
    buildString(value.length) {
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

private fun splitCompatibilityFields(line: String): List<String> {
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
