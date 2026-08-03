package com.sole.cinevault.subtitles

// ── Dual Subtitles ────────────────────────────────────────────────────────
// True machine translation isn't wired into CineVault (no translation API
// key/service exists anywhere in this codebase) — so "dual subtitles" here
// means finding a genuine, separately-authored subtitle in a second
// language via OpenSubtitles and displaying it alongside the primary one,
// rather than machine-translating the primary. Better quality when a
// release exists in that language; simply unavailable when it doesn't.
// That tradeoff is surfaced in the Studio UI, not hidden.
//
// The two tracks almost never share identical cue boundaries (different
// uploaders split lines differently), so this does an OVERLAP JOIN: for
// each PRIMARY cue, any secondary cues whose time range overlaps it get
// appended underneath. This is a best-effort alignment, not a guaranteed
// perfect one — most professionally-timed subtitle pairs for the same
// release line up close enough for this to read naturally; poorly-timed
// or wildly different releases may show slight mismatches.
// FIX: was comma-only ("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})..."), which
// meant a VTT track's dot-separated timestamps ("00:00:01.000") never
// matched this regex at all — parseTimingRangeMs() would return null for
// every single cue, so EVERY primary cue would show zero overlapping
// secondary cues. Not a crash, just dual mode silently merging in nothing,
// which looks identical to "dual subtitles isn't working" with no error
// to explain why. Now accepts either separator.
private val TIMING_REGEX = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

private fun parseTimingRangeMs(timing: String): Pair<Long, Long>? {
    val m = TIMING_REGEX.find(timing) ?: return null
    fun toMs(h: String, mi: String, s: String, ms: String) =
        h.toLong() * 3_600_000L + mi.toLong() * 60_000L + s.toLong() * 1_000L + ms.toLong()
    val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
    val end = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
    return start to end
}

private fun rangesOverlap(a: Pair<Long, Long>, b: Pair<Long, Long>): Boolean = a.first < b.second && b.first < a.second

fun mergeDualSubtitles(primaryText: String, secondaryText: String, secondaryColorHex: String, gapLines: Int): String {
    val primaryBlocks = parseSrtBlocks(primaryText)
    val secondaryBlocks = parseSrtBlocks(secondaryText)
    if (primaryBlocks.isEmpty()) return primaryText

    val secondaryTimed = secondaryBlocks.mapNotNull { block -> parseTimingRangeMs(block.timing)?.let { it to block.lines } }

    val output = StringBuilder()
    var newIndex = 1
    for (block in primaryBlocks) {
        val range = parseTimingRangeMs(block.timing)
        val overlapping = if (range != null) secondaryTimed.filter { (secRange, _) -> rangesOverlap(range, secRange) } else emptyList()

        output.append(newIndex).append("\n")
        output.append(normalizeTimingLine(block.timing)).append("\n")
        block.lines.forEach { output.append(it).append("\n") }

        if (overlapping.isNotEmpty()) {
            repeat(gapLines.coerceIn(0, 2)) { output.append("\n") }
            val secondaryLine = overlapping.flatMap { it.second }.joinToString(" ").trim()
            if (secondaryLine.isNotBlank()) {
                // <font color="..."> is one of the few HTML-ish tags SRT
                // decoders commonly honor — requires embedded styling to be
                // ENABLED for this specific merged file (see
                // VideoPlayerScreen.kt's dual-mode style effect), which is
                // why plain single-language playback deliberately disables
                // embedded styles elsewhere (keeps CineVault's own styling
                // authoritative) but dual mode is the one case that needs
                // per-line color to actually distinguish the two languages.
                output.append("<font color=\"$secondaryColorHex\">").append(secondaryLine).append("</font>\n")
            }
        }

        output.append("\n")
        newIndex++
    }
    return output.toString().trim()
}
