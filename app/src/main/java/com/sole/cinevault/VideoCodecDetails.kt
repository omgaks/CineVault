package com.sole.cinevault

data class VideoCodecDetails(
    val codecLabel: String?,
    val profileLabel: String?,
    val levelLabel: String?,
    val inferredBitDepth: Int?,
)

fun parseVideoCodecDetails(
    mimeType: String?,
    codecString: String?,
): VideoCodecDetails {
    val codec = codecString
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val lower = codec?.lowercase()

    return when {
        lower?.startsWith("hvc1.") == true ||
            lower?.startsWith("hev1.") == true ->
            parseHevcCodecDetails(codec)

        lower?.startsWith("avc1.") == true ||
            lower?.startsWith("avc3.") == true ->
            parseAvcCodecDetails(codec)

        lower?.startsWith("av01.") == true ->
            parseAv1CodecDetails(codec)

        lower?.startsWith("vp09.") == true ->
            parseVp9CodecDetails(codec)

        else ->
            VideoCodecDetails(
                codecLabel = friendlyVideoCodecLabel(
                    mimeType = mimeType,
                    codecString = codec,
                ),
                profileLabel = null,
                levelLabel = null,
                inferredBitDepth = null,
            )
    }
}

private fun parseHevcCodecDetails(
    codecString: String,
): VideoCodecDetails {
    val parts = codecString.split('.')
    val profileId = parts.getOrNull(1)?.toIntOrNull()

    val profile = when (profileId) {
        1 -> "Main"
        2 -> "Main 10"
        3 -> "Main Still Picture"
        else -> null
    }

    val level = parts
        .firstOrNull { part ->
            part.length >= 2 &&
                (part.startsWith("L", ignoreCase = true) ||
                    part.startsWith("H", ignoreCase = true)) &&
                part.drop(1).all(Char::isDigit)
        }
        ?.let { token ->
            val value = token.drop(1).toIntOrNull()
            value?.let { formatHevcLevel(it) }
        }

    return VideoCodecDetails(
        codecLabel = "HEVC",
        profileLabel = profile,
        levelLabel = level,
        inferredBitDepth = if (profileId == 2) 10 else null,
    )
}

private fun formatHevcLevel(levelIdc: Int): String {
    val level = levelIdc / 30.0
    return if (level % 1.0 == 0.0) {
        "L${level.toInt()}.0"
    } else {
        "L${String.format(java.util.Locale.US, "%.1f", level)}"
    }
}

private fun parseAvcCodecDetails(
    codecString: String,
): VideoCodecDetails {
    val hex = codecString
        .substringAfter('.', "")
        .take(6)

    val profileIdc = hex
        .takeIf { it.length >= 2 }
        ?.substring(0, 2)
        ?.toIntOrNull(16)

    val levelIdc = hex
        .takeIf { it.length >= 6 }
        ?.substring(4, 6)
        ?.toIntOrNull(16)

    val profile = when (profileIdc) {
        66 -> "Baseline"
        77 -> "Main"
        88 -> "Extended"
        100 -> "High"
        110 -> "High 10"
        122 -> "High 4:2:2"
        244 -> "High 4:4:4"
        else -> null
    }

    val level = levelIdc?.let {
        "L${it / 10}.${it % 10}"
    }

    return VideoCodecDetails(
        codecLabel = "H.264",
        profileLabel = profile,
        levelLabel = level,
        inferredBitDepth = if (profileIdc == 110) 10 else null,
    )
}

private fun parseAv1CodecDetails(
    codecString: String,
): VideoCodecDetails {
    val parts = codecString.split('.')

    val profile = when (parts.getOrNull(1)?.toIntOrNull()) {
        0 -> "Main"
        1 -> "High"
        2 -> "Professional"
        else -> null
    }

    val levelToken = parts.getOrNull(2)
    val levelIndex = levelToken
        ?.takeWhile(Char::isDigit)
        ?.toIntOrNull()

    val bitDepth = parts
        .getOrNull(3)
        ?.takeWhile(Char::isDigit)
        ?.toIntOrNull()
        ?.takeIf { it in setOf(8, 10, 12) }

    return VideoCodecDetails(
        codecLabel = "AV1",
        profileLabel = profile,
        levelLabel = levelIndex?.let { "L$it" },
        inferredBitDepth = bitDepth,
    )
}

private fun parseVp9CodecDetails(
    codecString: String,
): VideoCodecDetails {
    val parts = codecString.split('.')

    val profile = when (parts.getOrNull(1)?.toIntOrNull()) {
        0 -> "Profile 0"
        1 -> "Profile 1"
        2 -> "Profile 2"
        3 -> "Profile 3"
        else -> null
    }

    val level = parts
        .getOrNull(2)
        ?.toIntOrNull()
        ?.let { "L${it / 10}.${it % 10}" }

    val bitDepth = parts
        .getOrNull(3)
        ?.toIntOrNull()
        ?.takeIf { it in setOf(8, 10, 12) }

    return VideoCodecDetails(
        codecLabel = "VP9",
        profileLabel = profile,
        levelLabel = level,
        inferredBitDepth = bitDepth,
    )
}

fun formatVideoCodecDetails(
    details: VideoCodecDetails,
): String? {
    val parts = buildList {
        details.codecLabel?.let(::add)
        details.profileLabel?.let(::add)
        details.inferredBitDepth?.let { add("${it}-bit") }
        details.levelLabel?.let(::add)
    }

    return parts
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")
}
