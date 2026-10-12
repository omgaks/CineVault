package com.sole.cinevault.playback.rescue.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Owns the two FFmpeg workers as a single rescue session.
 * Caller must still transfer UI/Media3 control ownership before activation.
 */
internal class FfmpegNativeAvCoordinator(
    private val binding: FfmpegNativeSurfaceBinding,
) : AutoCloseable {
    private var audio: FfmpegNativeAudioPlayback? = null
    private var audioWorker: Thread? = null

    @Synchronized
    fun start(
        player: ExoPlayer,
        playerView: PlayerView,
        path: String,
        positionMs: Long,
        playWhenReady: Boolean,
        onFailure: (Throwable) -> Unit,
        onCompleted: (FfmpegNativeVideoPlayback.Stats) -> Unit,
    ): Boolean {
        if (audioWorker != null || binding.isRunning()) return false
        val output = FfmpegNativeAudioPlayback()
        if (positionMs > 0) output.seekTo(positionMs)
        if (!playWhenReady) output.pause()
        val video = FfmpegNativeVideoPlayback(audioPositionMs = output::positionMs)
        val started = binding.startAfterMedia3Release(
            player = player,
            playerView = playerView,
            path = path,
            playback = video,
            resumePositionMs = positionMs,
            playWhenReady = playWhenReady,
            onFailure = { error -> stop(); onFailure(error) },
            onCompleted = { stats -> stop(); onCompleted(stats) },
        )
        if (!started) return false
        audio = output
        audioWorker = Thread({
            try {
                output.play(path)
            } catch (error: Throwable) {
                binding.stop()
                // Native audio failure is fatal for AV rescue. The player owner
                // must marshal UI restoration to the main thread.
                onFailure(error)
            } finally {
                synchronized(this@FfmpegNativeAvCoordinator) {
                    if (audioWorker === Thread.currentThread()) {
                        audioWorker = null
                        audio = null
                    }
                }
            }
        }, "CineVault-FFmpeg-Audio").apply { start() }
        return true
    }

    @Synchronized fun pause() { audio?.pause(); binding.pause() }
    @Synchronized fun resume() { audio?.resume(); binding.resume() }
    @Synchronized fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        audio?.seekTo(positionMs)
        binding.seekTo(positionMs)
    }
    @Synchronized fun stop() { audio?.stop(); binding.stop() }
    override fun close() { stop() }
}
