package com.sole.cinevault

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

internal data class AudioRescueTrackIdentity(
    val language: String?,
    val sampleMimeType: String?,
    val codecs: String?,
    val channelCount: Int,
    val sampleRate: Int,
)

internal fun ExoPlayer.selectedAudioTrackIdentityForRescue():
    AudioRescueTrackIdentity? =
    currentTracks.groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            (0 until group.length).asSequence()
                .filter { group.isTrackSelected(it) }
                .map { group.getTrackFormat(it).toAudioRescueTrackIdentity() }
        }
        .firstOrNull()

internal fun Format.toAudioRescueTrackIdentity() =
    AudioRescueTrackIdentity(
        language = language,
        sampleMimeType = sampleMimeType,
        codecs = codecs,
        channelCount = channelCount,
        sampleRate = sampleRate,
    )

internal fun AudioRescueTrackIdentity.matches(format: Format): Boolean =
    matches(format.toAudioRescueTrackIdentity())

internal fun AudioRescueTrackIdentity.matches(candidate: AudioRescueTrackIdentity): Boolean {
    if (
        !language.isNullOrBlank() &&
        !candidate.language.isNullOrBlank() &&
        !language.equals(candidate.language, ignoreCase = true)
    ) return false

    if (
        !sampleMimeType.isNullOrBlank() &&
        !candidate.sampleMimeType.isNullOrBlank() &&
        sampleMimeType != candidate.sampleMimeType
    ) return false

    if (
        !codecs.isNullOrBlank() &&
        !candidate.codecs.isNullOrBlank() &&
        codecs != candidate.codecs
    ) return false

    if (channelCount > 0 && candidate.channelCount > 0 &&
        channelCount != candidate.channelCount
    ) return false

    if (sampleRate > 0 && candidate.sampleRate > 0 &&
        sampleRate != candidate.sampleRate
    ) return false

    return true
}
