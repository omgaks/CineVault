package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * Captures all currently exposed player tracks and which stream in each group
 * is selected. This is intentionally an observation layer only: it does not
 * change track selection or decoder routing.
 */
fun inspectPlaybackStreamInventory(
    tracks: Tracks,
): PlaybackStreamInventory {
    val video = mutableListOf<PlaybackStreamDescriptor>()
    val audio = mutableListOf<PlaybackStreamDescriptor>()
    val text = mutableListOf<PlaybackStreamDescriptor>()

    tracks.groups.forEach { group ->
        val kind = when (group.type) {
            C.TRACK_TYPE_VIDEO -> PlaybackStreamKind.VIDEO
            C.TRACK_TYPE_AUDIO -> PlaybackStreamKind.AUDIO
            C.TRACK_TYPE_TEXT -> PlaybackStreamKind.TEXT
            else -> null
        } ?: return@forEach

        repeat(group.length) { index ->
            val format = group.getTrackFormat(index)
            val descriptor = PlaybackStreamDescriptor(
                kind = kind,
                mimeType = format.sampleMimeType,
                codecString = format.codecs,
                language = format.language,
                selected = group.isTrackSelected(index),
            )

            when (kind) {
                PlaybackStreamKind.VIDEO -> video += descriptor
                PlaybackStreamKind.AUDIO -> audio += descriptor
                PlaybackStreamKind.TEXT -> text += descriptor
            }
        }
    }

    return PlaybackStreamInventory(
        videoStreams = video,
        audioStreams = audio,
        textStreams = text,
    )
}
