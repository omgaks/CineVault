package com.sole.cinevault

internal data class PlaybackRescueExecutionPlan(
    val executeSoftwareVideo: Boolean,
    val executeFfmpegAudio: Boolean,
)

internal object PlaybackRescueExecutionPolicy {
    fun forLane(lane: PlaybackRescueLane): PlaybackRescueExecutionPlan =
        when (lane) {
            PlaybackRescueLane.NONE ->
                PlaybackRescueExecutionPlan(false, false)
            PlaybackRescueLane.SOFTWARE_VIDEO ->
                PlaybackRescueExecutionPlan(true, false)
            PlaybackRescueLane.FFMPEG_AUDIO ->
                PlaybackRescueExecutionPlan(false, true)
            PlaybackRescueLane.MIXED ->
                PlaybackRescueExecutionPlan(true, true)
        }
}
