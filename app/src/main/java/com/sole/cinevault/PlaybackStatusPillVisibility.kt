package com.sole.cinevault

/**
 * D14-S8:
 * The compact playback-status pill is exceptional-state UI, not a permanent
 * hardware-playback badge.
 */
internal fun shouldShowPlaybackStatusPill(
    snapshot: PlaybackDiagnosticsSnapshot,
): Boolean {
    val pipeline = classifyPlaybackSnapshotPipeline(snapshot)

    return snapshot.lastFailureDiagnostic != null ||
        snapshot.audioFfmpegRescueOutcome != AudioFfmpegRescueOutcome.NOT_ATTEMPTED ||
        pipeline == PlaybackPipelineKind.VIDEO_SOFTWARE_RESCUE ||
        pipeline == PlaybackPipelineKind.AUDIO_FFMPEG_RESCUE ||
        pipeline == PlaybackPipelineKind.MIXED_VIDEO_AUDIO_RESCUE
}
