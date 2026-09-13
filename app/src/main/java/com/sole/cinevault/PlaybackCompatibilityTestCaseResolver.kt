package com.sole.cinevault

import java.util.Locale

data class PlaybackCompatibilityTestCaseResolution(
    val testCase: PlaybackCompatibilityTestCase,
    val isNamedCase: Boolean,
)

/**
 * Resolves a stable CineVault compatibility-test identity from a media file.
 *
 * Named torture files use this filename convention:
 *
 *   CVTEST__<stable-id>__<friendly label>.<ext>
 *
 * Example:
 *
 *   CVTEST__hevc-main10-4k-hdr__HEVC Main10 4K HDR.mkv
 *
 * The stable id is independent of folder location, so copying the same suite
 * to another device still produces comparable compatibility-matrix rows.
 *
 * Ordinary playback keeps the existing per-path identity.
 */
fun resolvePlaybackCompatibilityTestCase(
    path: String,
    displayName: String?,
): PlaybackCompatibilityTestCaseResolution {
    val sourceName = displayName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: path.substringAfterLast('/')
            .trim()
            .ifBlank { path }

    val filename = sourceName.substringBeforeLast('.', sourceName)

    val named = parseNamedCompatibilityCase(filename)
    if (named != null) {
        return PlaybackCompatibilityTestCaseResolution(
            testCase = named,
            isNamedCase = true,
        )
    }

    return PlaybackCompatibilityTestCaseResolution(
        testCase = PlaybackCompatibilityTestCase(
            testId = path,
            sourceLabel = sourceName,
        ),
        isNamedCase = false,
    )
}

private fun parseNamedCompatibilityCase(
    filenameWithoutExtension: String,
): PlaybackCompatibilityTestCase? {
    val parts = filenameWithoutExtension.split("__")

    if (parts.size < 2) {
        return null
    }

    if (!parts.first().equals("CVTEST", ignoreCase = true)) {
        return null
    }

    val stableId = normalizeCompatibilityTestId(parts[1])
        ?: return null

    val friendlyLabel = parts
        .drop(2)
        .joinToString(" ")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?: stableId

    return PlaybackCompatibilityTestCase(
        testId = stableId,
        sourceLabel = friendlyLabel,
    )
}

/**
 * Keeps ids compact and portable across Android devices, filesystems,
 * spreadsheets and future persistence/export formats.
 */
fun normalizeCompatibilityTestId(
    raw: String,
): String? {
    val normalized = raw
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .replace(Regex("-{2,}"), "-")
        .trim('-', '.', '_')

    return normalized.takeIf { it.isNotEmpty() }
}
