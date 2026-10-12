package com.sole.cinevault.playback.rescue.video

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Owns the two FFmpeg workers as a single rescue session.
 * Caller must still transfer UI/Media3 control ownership before activation.
 */
internal class FfmpegNativeAvCoordinator(
    private val binding: FfmpegNativeSurfaceBinding,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
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
        val terminalDelivered = AtomicBoolean(false)
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
            onFailure = { error ->
                if (terminalDelivered.compareAndSet(false, true)) {
                    stop()
                    onFailure(error)
                }
            },
            onCompleted = { stats ->
                if (terminalDelivered.compareAndSet(false, true)) {
                    stop()
                    onCompleted(stats)
                }
            },
        )
        if (!started) return false
        audio = output
        audioWorker = Thread({
            try {
                output.play(path)
            } catch (error: Throwable) {
                binding.stop()
                // Audio worker callbacks must never manipulate Compose/player
                // state off the main thread. Only one terminal event wins.
                mainHandler.post {
                    if (terminalDelivered.compareAndSet(false, true)) {
                        stop()
                        onFailure(error)
                    }
                }
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
