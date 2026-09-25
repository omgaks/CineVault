package com.sole.cinevault

internal data class PlaybackRescueContinuityContract(
    val preservesPosition: Boolean,
    val preservesPlayState: Boolean,
    val preservesPlaybackSpeed: Boolean,
    val preservesVolume: Boolean,
    val preservesAudioTrack: Boolean,
    val preservesSubtitle: Boolean,
)

internal object PlaybackRescueContinuityPolicy {
    fun forLane(lane: PlaybackRescueLane): PlaybackRescueContinuityContract =
        when (lane) {
            PlaybackRescueLane.NONE ->
                PlaybackRescueContinuityContract(
                    preservesPosition = true,
                    preservesPlayState = true,
                    preservesPlaybackSpeed = true,
                    preservesVolume = true,
                    preservesAudioTrack = true,
                    preservesSubtitle = true,
                )

            PlaybackRescueLane.SOFTWARE_VIDEO ->
                PlaybackRescueContinuityContract(
                    preservesPosition = true,
                    preservesPlayState = true,
                    preservesPlaybackSpeed = true,
                    preservesVolume = true,
                    preservesAudioTrack = true,
                    preservesSubtitle = true,
                )

            PlaybackRescueLane.FFMPEG_AUDIO,
            PlaybackRescueLane.MIXED ->
                PlaybackRescueContinuityContract(
                    preservesPosition = true,
                    preservesPlayState = true,
                    preservesPlaybackSpeed = true,
                    preservesVolume = true,
                    preservesAudioTrack = true,
                    preservesSubtitle = true,
                )
        }
}
