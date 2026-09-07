package com.sole.cinevault.subtitles

// ── Dual Subtitles ────────────────────────────────────────────────────────
// Dual mode merges primary + secondary into ONE valid SRT cue stream.
// Important: a truly blank line inside an SRT cue terminates that cue.
// Therefore visual "gap lines" must use an invisible non-blank character
// rather than "\n\n", otherwise the secondary language is parsed as orphan
// text and never rendered.

private val TIMING_REGEX =
    Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

private const val NEAREST_FALLBACK_MS = 2_500L
private const val INVISIBLE_GAP_LINE = "\u200B" // zero-width space: visually blank, not an SRT separator

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
    b: Pair<Long, Long>
): Boolean = a.first < b.second && b.first < a.second

private fun midpoint(range: Pair<Long, Long>): Long =
    range.first + ((range.second - range.first) / 2L)

private data class TimedSecondary(
    val range: Pair<Long, Long>,
    val lines: List<String>
)

private fun secondaryForPrimary(
    primaryRange: Pair<Long, Long>,
    secondary: List<TimedSecondary>
): List<String> {
    val overlapping = secondary
        .filter { rangesOverlap(primaryRange, it.range) }
        .flatMap { it.lines }
        .filter { it.isNotBlank() }

    if (overlapping.isNotEmpty()) return overlapping

    // Different subtitle releases can be split a little differently even
    // when they are correctly synced. If no cue overlaps, accept only the
    // nearest cue within a small tolerance. This avoids silently dropping
    // the second language for harmless cue-boundary differences without
    // pairing obviously unrelated dialogue.
    val primaryMid = midpoint(primaryRange)
    return secondary
        .minByOrNull { kotlin.math.abs(midpoint(it.range) - primaryMid) }
        ?.takeIf { kotlin.math.abs(midpoint(it.range) - primaryMid) <= NEAREST_FALLBACK_MS }
        ?.lines
        ?.filter { it.isNotBlank() }
        .orEmpty()
}

fun mergeDualSubtitles(
    primaryText: String,
    secondaryText: String,
    secondaryColorHex: String,
    gapLines: Int
): String {
    val primaryBlocks = parseSrtBlocks(primaryText)
    val secondaryBlocks = parseSrtBlocks(secondaryText)

    if (primaryBlocks.isEmpty()) return primaryText

    val secondaryTimed = secondaryBlocks.mapNotNull { block ->
        parseTimingRangeMs(block.timing)?.let { range ->
            TimedSecondary(range, block.lines)
        }
    }

    val output = StringBuilder()
    var newIndex = 1

    for (block in primaryBlocks) {
        val range = parseTimingRangeMs(block.timing)

        output.append(newIndex).append('\n')
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
                    .append(secondaryColorHex)
                    .append("\">")
                    .append(secondaryLine)
                    .append("</font>")
                    .append('\n')
            }
        }

        // Exactly ONE genuinely blank line terminates the completed cue.
        output.append('\n')
        newIndex++
    }

    return output.toString().trim()
}

/**
 * Used by the coordinator to avoid reporting "Dual subtitles ready" when a
 * downloaded secondary file produced zero usable secondary lines.
 */
fun dualMergeContainsSecondary(mergedSrt: String): Boolean =
    mergedSrt.contains("<font color=", ignoreCase = true)
