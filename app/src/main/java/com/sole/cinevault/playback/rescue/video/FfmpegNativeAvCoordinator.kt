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
    private var sessionGeneration = 0L
    private var owningPlayer: ExoPlayer? = null
    private var owningView: PlayerView? = null

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
        // Silence Media3 before the native audio worker can begin output.
        // Preserve the prior play intent for rollback if video handoff fails.
        val previousPlayWhenReady = player.playWhenReady
        player.pause()
        val terminalDelivered = AtomicBoolean(false)
        val session = ++sessionGeneration
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
                if (session == sessionGeneration && terminalDelivered.compareAndSet(false, true)) {
                    finishSession()
                    onFailure(error)
                }
            },
            onCompleted = { stats ->
                if (session == sessionGeneration && terminalDelivered.compareAndSet(false, true)) {
                    finishSession()
                    onCompleted(stats)
                }
            },
        )
        if (!started) {
            player.playWhenReady = previousPlayWhenReady
            return false
        }
        owningPlayer = player
        owningView = playerView
        audio = output
        audioWorker = Thread({
            try {
                output.play(path)
            } catch (error: Throwable) {
                binding.stop()
                // Audio worker callbacks must never manipulate Compose/player
                // state off the main thread. Only one terminal event wins.
                mainHandler.post {
                    if (session == sessionGeneration && terminalDelivered.compareAndSet(false, true)) {
                        finishSession()
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

    /**
     * Terminal callbacks arrive on the main thread. The native video worker
     * has released its Surface before completion/failure is delivered.
     * Restore Media3 output before the screen attempts recovery.
     */
    @Synchronized
    private fun finishSession() {
        stop()
        val player = owningPlayer
        val view = owningView
        owningPlayer = null
        owningView = null
        if (player != null && view != null && !binding.isRunning()) {
            binding.restoreMedia3VideoSurface(player, view)
        }
    }

    @Synchronized fun positionMs(): Long? = audio?.positionMs()
    @Synchronized fun pause() { audio?.pause(); binding.pause() }
    @Synchronized fun resume() { audio?.resume(); binding.resume() }
    @Synchronized fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        audio?.seekTo(positionMs)
        binding.seekTo(positionMs)
    }
    @Synchronized fun stop() {
        sessionGeneration++
        audio?.stop()
        binding.stop()
    }
    override fun close() { stop() }
}
