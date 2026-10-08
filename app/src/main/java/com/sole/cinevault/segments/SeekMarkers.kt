package com.sole.cinevault.segments

/**
 * What the seek bar may draw. Only segments that really exist (cached from
 * IntroDB, or a scene time the provider supplied) become markers. Nothing is
 * ever invented, so a film with no data shows a clean bar.
 */
enum class SeekMarkerKind { SKIPPABLE, CREDITS, SCENE }

data class SeekMarker(
    val kind: SeekMarkerKind,
    val startFraction: Float,
    val endFraction: Float,
)

fun seekMarkersFor(result: SmartSegmentResult, durationMs: Long): List<SeekMarker> {
    if (durationMs <= 0L) return emptyList()
    val total = durationMs.toFloat()
    return result.segments.mapNotNull { seg ->
        val start = (seg.startMs / total).coerceIn(0f, 1f)
        val end = (seg.endMs / total).coerceIn(0f, 1f)
        when (seg.type) {
            SegmentType.RECAP, SegmentType.INTRO, SegmentType.PREVIEW, SegmentType.COMMERCIAL ->
                if (end > start) SeekMarker(SeekMarkerKind.SKIPPABLE, start, end) else null
            SegmentType.CREDITS ->
                if (end > start) SeekMarker(SeekMarkerKind.CREDITS, start, end) else null
            SegmentType.MID_CREDITS_SCENE, SegmentType.POST_CREDITS_SCENE ->
                if (seg.startMs < durationMs) SeekMarker(SeekMarkerKind.SCENE, start, start) else null
        }
    }.sortedBy { it.startFraction }
}
