package com.sole.cinevault

data class PlaybackDiagnosticsLiveStatus(
    val readiness: String,
    val recovery: String,
    val health: String,
)

fun buildPlaybackDiagnosticsLiveStatus(
    snapshot: PlaybackDiagnosticsSnapshot,
): PlaybackDiagnosticsLiveStatus {
    val readiness = when (snapshot.nativeReadiness) {
        NativeVideoPlaybackReadiness.READY ->
            "Native decoder ready"

        NativeVideoPlaybackReadiness.MARGINAL ->
            "Native decoder marginal"

        NativeVideoPlaybackReadiness.SOFTWARE_FALLBACK_NEEDED ->
            "Software rescue preferred"

        NativeVideoPlaybackReadiness.UNKNOWN ->
            "Decoder readiness unknown"
    }

    val recovery = when {
        snapshot.fallbackOccurred ->
            snapshot.fallbackReason
                ?.let(::playbackFallbackReasonLabel)
                ?.let { "SW active · $it" }
                ?: "Software decoding active"

        snapshot.softwareFallbackAvailable ->
            "SW rescue available"

        else ->
            "No SW rescue for this stream"
    }

    val health = when {
        snapshot.unhealthyDroppedFrameWindows > 0 ->
            "${snapshot.totalDroppedVideoFrames} dropped · " +
                "${snapshot.unhealthyDroppedFrameWindows} unhealthy window" +
                if (snapshot.unhealthyDroppedFrameWindows == 1) "" else "s"

        snapshot.totalDroppedVideoFrames > 0 ->
            "${snapshot.totalDroppedVideoFrames} dropped · healthy"

        snapshot.firstVideoFrameRendered ->
            "First frame rendered · healthy"

        snapshot.startupPlaybackConfirmed ->
            "Playback started · awaiting first-frame signal"

        else ->
            "Starting / no health sample yet"
    }

    return PlaybackDiagnosticsLiveStatus(
        readiness = readiness,
        recovery = recovery,
        health = health,
    )
}
