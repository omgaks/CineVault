package com.sole.cinevault

internal enum class PlaybackRescueLane {
    NONE,
    SOFTWARE_VIDEO,
    FFMPEG_AUDIO,
    MIXED,
}

internal data class PlaybackRescueAdmissionInput(
    val softwareVideoRequested: Boolean,
    val softwareVideoAvailable: Boolean,
    val softwareVideoAlreadyActive: Boolean,
    val ffmpegAudioRequested: Boolean,
    val ffmpegAudioAlreadyActive: Boolean,
)

internal object PlaybackRescueAdmissionPolicy {
    fun decide(input: PlaybackRescueAdmissionInput): PlaybackRescueLane {
        val video =
            input.softwareVideoRequested &&
                input.softwareVideoAvailable &&
                !input.softwareVideoAlreadyActive

        val audio =
            input.ffmpegAudioRequested &&
                !input.ffmpegAudioAlreadyActive

        return when {
            video && audio -> PlaybackRescueLane.MIXED
            video -> PlaybackRescueLane.SOFTWARE_VIDEO
            audio -> PlaybackRescueLane.FFMPEG_AUDIO
            else -> PlaybackRescueLane.NONE
        }
    }
}
