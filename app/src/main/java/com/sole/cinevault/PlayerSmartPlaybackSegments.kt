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

    val exactSceneSegment = result.segments.firstOrNull {
        it.type == SegmentType.MID_CREDITS_SCENE ||
            it.type == SegmentType.POST_CREDITS_SCENE
    }

    val creditsSegment = result.segments.firstOrNull {
        it.type == SegmentType.CREDITS
    }

    return PlayerSmartPlaybackSegments(
        activeSegment = activeSegment,
        exactSceneSegment = exactSceneSegment,
        creditsSegment = creditsSegment
    )
}
