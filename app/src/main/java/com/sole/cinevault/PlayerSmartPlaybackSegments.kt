package com.sole.cinevault

import com.sole.cinevault.segments.SegmentType
import com.sole.cinevault.segments.SmartSegment
import com.sole.cinevault.segments.SmartSegmentResult

internal data class PlayerSmartPlaybackSegments(
    val activeSegment: SmartSegment?,
    val exactSceneSegment: SmartSegment?,
    val creditsSegment: SmartSegment?,
)

internal fun deriveSmartPlaybackSegments(
    result: SmartSegmentResult,
    position: Long,
): PlayerSmartPlaybackSegments {
    val activeSegment = result.segments
        .asSequence()
        .filter {
            it.type == SegmentType.RECAP ||
                it.type == SegmentType.INTRO ||
                it.type == SegmentType.PREVIEW ||
                it.type == SegmentType.COMMERCIAL ||
                it.type == SegmentType.CREDITS
        }
        .firstOrNull { it.contains(position) }

    // The next scene still ahead of the viewer; after the last one has started, that last one,
    // so "all scenes passed" is not mistaken for "no scene time known".
    val scenes = result.segments
        .filter { it.type == SegmentType.MID_CREDITS_SCENE || it.type == SegmentType.POST_CREDITS_SCENE }
        .sortedBy { it.startMs }
    val exactSceneSegment = scenes.firstOrNull { it.startMs > position } ?: scenes.lastOrNull()

    val creditsSegment = result.segments.firstOrNull {
        it.type == SegmentType.CREDITS
    }

    return PlayerSmartPlaybackSegments(
        activeSegment = activeSegment,
        exactSceneSegment = exactSceneSegment,
        creditsSegment = creditsSegment
    )
}
