package com.sole.cinevault.subtitles

// ── Dual Subtitles ────────────────────────────────────────────────────────
// Dual mode merges primary + secondary into ONE valid SRT cue stream.
//
// Important:
// A genuinely blank line terminates an SRT cue. Visual separation between
// primary and secondary therefore uses a zero-width, non-blank line.
//
// This file is intentionally Android-free so the merge contract can be
// protected by fast JVM regression tests.
private val TIMING_REGEX =
    Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

private const val NEAREST_FALLBACK_MS = 2_500L
private const val INVISIBLE_GAP_LINE = "\u200B"

private fun parseTimingRangeMs(timing: String): Pair<Long, Long>? {
    val m = TIMING_REGEX.find(timing) ?: return null

    fun toMs(h: String, mi: String, s: String, ms: String) =
        h.toLong() * 3_600_000L +
            mi.toLong() * 60_000L +
            s.toLong() * 1_000L +
            ms.toLong()

    return toMs(
        m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4]
    ) to toMs(
        m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8]
    )
}

private fun rangesOverlap(
    a: Pair<Long, Long>,
    b: Pair<Long, Long>,
): Boolean = a.first < b.second && b.first < a.second

private fun midpoint(range: Pair<Long, Long>): Long =
    range.first + ((range.second - range.first) / 2L)

private data class TimedSecondary(
    val range: Pair<Long, Long>,
    val lines: List<String>,
)

private fun secondaryForPrimary(
    primaryRange: Pair<Long, Long>,
    secondary: List<TimedSecondary>,
): List<String> {
    val overlapping = secondary
        .filter { rangesOverlap(primaryRange, it.range) }
        .flatMap { it.lines }
        .filter { it.isNotBlank() }

    if (overlapping.isNotEmpty()) return overlapping

    // Releases often split the same dialogue at slightly different cue
    // boundaries. A tightly bounded nearest-cue fallback keeps a legitimate
    // second language visible without pairing obviously unrelated dialogue.
    val primaryMid = midpoint(primaryRange)
    return secondary
        .minByOrNull { kotlin.math.abs(midpoint(it.range) - primaryMid) }
        ?.takeIf {
            kotlin.math.abs(midpoint(it.range) - primaryMid) <= NEAREST_FALLBACK_MS
        }
        ?.lines
        ?.filter { it.isNotBlank() }
        .orEmpty()
}

/**
 * Build one Media3/SubRip-friendly cue stream containing both languages.
 *
 * The primary text is left untouched. Secondary text is flattened only within
 * its matched cue and receives a per-line HTML colour span understood by the
 * SubRip styling path. Exactly one real blank line terminates every cue.
 */
fun mergeDualSubtitles(
    primaryText: String,
    secondaryText: String,
    secondaryColorHex: String,
    gapLines: Int,
): String {
    val primaryBlocks = parseSrtBlocks(primaryText)
    val secondaryBlocks = parseSrtBlocks(secondaryText)

    if (primaryBlocks.isEmpty()) return primaryText

    val secondaryTimed = secondaryBlocks.mapNotNull { block ->
        parseTimingRangeMs(block.timing)?.let { range ->
            TimedSecondary(range, block.lines)
        }
    }

    val safeColor = normalizeDualSubtitleColor(secondaryColorHex)
    val output = StringBuilder()

    primaryBlocks.forEachIndexed { index, block ->
        val range = parseTimingRangeMs(block.timing)

        output.append(index + 1).append('\n')
        output.append(normalizeTimingLine(block.timing)).append('\n')

        block.lines
            .filter { it.isNotBlank() }
            .forEach { output.append(it).append('\n') }

        val secondaryLines =
            if (range != null) secondaryForPrimary(range, secondaryTimed)
            else emptyList()

        if (secondaryLines.isNotEmpty()) {
            repeat(gapLines.coerceIn(0, 2)) {
                output.append(INVISIBLE_GAP_LINE).append('\n')
            }

            val secondaryLine = secondaryLines
                .joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (secondaryLine.isNotBlank()) {
                output
                    .append("<font color=\"")
                    .append(safeColor)
                    .append("\">")
                    .append(secondaryLine)
                    .append("</font>")
                    .append('\n')
            }
        }

        // One and only one genuinely blank line terminates this cue.
        output.append('\n')
    }

    return output.toString().trim()
}

/**
 * Prevent malformed/user-restored colour strings from producing invalid
 * subtitle markup. CineVault stores #RRGGBB; fall back to readable amber.
 */
internal fun normalizeDualSubtitleColor(value: String): String {
    val trimmed = value.trim()
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(trimmed)) {
        trimmed.uppercase()
    } else {
        "#FFC107"
    }
}

/**
 * A successful dual render must contain an actual styled secondary payload,
 * not merely a font tag accidentally left empty.
 */
fun dualMergeContainsSecondary(mergedSrt: String): Boolean =
    Regex(
        "<font\\s+color=\"#[0-9A-Fa-f]{6}\">\\s*\\S.+?</font>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).containsMatchIn(mergedSrt)
