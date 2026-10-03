package com.sole.cinevault.subtitles

/** One timed subtitle line, as read from an SRT or WebVTT file. */
data class RawCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Tolerant SRT / WebVTT reader used by the on-screen subtitle overlay. Pure Kotlin, so it is
 * covered by plain JVM tests. It never throws: unreadable blocks are skipped.
 */
object SubtitleCueParser {

    private val timeRegex =
        Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
    private val alignmentTag = Regex("""\{\\[^}]*\}""")

    fun parse(raw: String): List<RawCue> {
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = ArrayList<RawCue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val arrow = line.indexOf("-->")
            if (arrow < 0) {
                i++
                continue
            }
            val start = parseTime(line.substring(0, arrow))
            val end = parseTime(line.substring(arrow + 3))
            i++
            if (start == null || end == null) continue

            val text = StringBuilder()
            while (i < lines.size) {
                val next = lines[i]
                if (next.isBlank()) break
                // Missing blank line before the next block: "12" then "00:00:05,000 --> ..."
                if (next.trim().all { it.isDigit() } &&
                    i + 1 < lines.size && lines[i + 1].contains("-->")
                ) break
                if (next.contains("-->")) break
                if (text.isNotEmpty()) text.append('\n')
                text.append(next)
                i++
            }
            val cleaned = alignmentTag.replace(text.toString(), "").trim()
            if (cleaned.isEmpty()) continue
            val safeEnd = if (end > start) end else start + 2000L
            cues.add(RawCue(start, safeEnd, cleaned))
        }
        cues.sortBy { it.startMs }
        return cues
    }

    private fun parseTime(part: String): Long? {
        // Only the first token: WebVTT allows cue settings after the end time.
        val token = part.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val m = timeRegex.matchEntire(token) ?: return null
        val hours = m.groupValues[1].ifEmpty { "0" }.toLong()
        val minutes = m.groupValues[2].toLong()
        val seconds = m.groupValues[3].toLong()
        val fraction = m.groupValues[4].padEnd(3, '0').take(3).toLong()
        return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fraction
    }
}
