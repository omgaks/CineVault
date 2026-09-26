package com.sole.cinevault

import androidx.media3.common.C

/**
 * D15-R1: subtitle renderer failures must never be routed into video/audio rescue.
 *
 * A broken/unsupported embedded subtitle can fail Media3's text renderer while
 * the video and audio streams are still healthy. In that case CineVault drops
 * only the text renderer and resumes the same playback session.
 */
internal fun shouldRecoverByDisablingSubtitles(
    attribution: PlaybackFailureAttribution,
): Boolean =
    attribution.rendererFailure &&
        attribution.rendererTrackType == C.TRACK_TYPE_TEXT
