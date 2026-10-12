package com.sole.cinevault.playback.rescue.video

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.IOException

/** Owns native audio/video and returns the surface only after both workers exit. */
internal class FfmpegNativeAvCoordinator(
    private val binding: FfmpegNativeSurfaceBinding,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var audio: FfmpegNativeAudioPlayback? = null
    private var audioWorker: Thread? = null
    private var sessionGeneration = 0L
    private var owningPlayer: ExoPlayer? = null
    private var owningView: PlayerView? = null
    private var shuttingDown = false
    private var closed = false
    private var volume = 1f
    private var onShutdownComplete: (() -> Unit)? = null

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
        if (closed || shuttingDown || audioWorker != null || binding.isRunning()) return false
        val previousPlayWhenReady = player.playWhenReady
        player.pause()
        val session = ++sessionGeneration
        val output = FfmpegNativeAudioPlayback()
        output.setVolume(volume)
        if (positionMs > 0) output.seekTo(positionMs)
        if (!playWhenReady) output.pause()
        val video = FfmpegNativeVideoPlayback(audioPositionMs = output::playbackClockMs)
        val started = try {
            binding.startAfterMedia3Release(
                player = player,
                playerView = playerView,
                path = path,
                playback = video,
                resumePositionMs = positionMs,
                playWhenReady = playWhenReady,
                onFailure = { error -> terminate(session) { onFailure(error) } },
                onCompleted = { stats -> terminate(session) { onCompleted(stats) } },
            )
        } catch (error: Exception) {
            player.playWhenReady = previousPlayWhenReady
            return false
        }
        if (!started) {
            player.playWhenReady = previousPlayWhenReady
            return false
        }
        owningPlayer = player
        owningView = playerView
        audio = output
        binding.onSurfaceUnavailable = {
            // Surface callbacks can arrive while bind() holds its lock. Post
            // instead of taking the coordinator lock in the inverse order.
            mainHandler.post {
                terminate(session) { onFailure(IOException("Native video surface was lost")) }
            }
        }
        val worker = Thread({
            try {
                output.play(path)
            } catch (error: Throwable) {
                mainHandler.post { terminate(session) { onFailure(error) } }
            } finally {
                mainHandler.post {
                    synchronized(this@FfmpegNativeAvCoordinator) {
                        if (audio === output) {
                            audioWorker = null
                            // Retain its last clock until video completes.
                            completeShutdownIfIdle()
                        }
                    }
                }
            }
        }, "CineVault-FFmpeg-Audio")
        audioWorker = worker
        worker.start()
        return true
    }

    @Synchronized
    private fun terminate(session: Long, callback: () -> Unit) {
        if (closed || shuttingDown || session != sessionGeneration) return
        shutdown(restoreSurface = true, callback = callback)
    }

    @Synchronized
    private fun shutdown(restoreSurface: Boolean, callback: (() -> Unit)? = null) {
        sessionGeneration++
        shuttingDown = true
        binding.onSurfaceUnavailable = null
        audio?.stop()
        if (!restoreSurface) {
            owningPlayer = null
            owningView = null
        }
        onShutdownComplete = callback
        binding.stop { synchronized(this) { completeShutdownIfIdle() } }
        completeShutdownIfIdle()
    }

    private fun completeShutdownIfIdle() {
        if (!shuttingDown || audioWorker != null || binding.isRunning()) return
        val player = owningPlayer
        val view = owningView
        owningPlayer = null
        owningView = null
        audio = null
        shuttingDown = false
        if (!closed && player != null && view != null) {
            binding.restoreMedia3VideoSurface(player, view)
        }
        val callback = onShutdownComplete
        onShutdownComplete = null
        if (!closed) callback?.invoke()
    }

    @Synchronized fun positionMs(): Long? = audio?.positionMs()
    @Synchronized fun durationMs(): Long? = audio?.durationMs()
    @Synchronized fun pause() { audio?.pause(); binding.pause() }
    @Synchronized fun resume() { audio?.resume(); binding.resume() }
    @Synchronized fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        audio?.setVolume(volume)
    }
    @Synchronized fun seekTo(positionMs: Long) {
        require(positionMs >= 0L)
        audio?.seekTo(positionMs)
        binding.seekTo(positionMs)
    }
    @Synchronized fun stop() { shutdown(restoreSurface = true) }
    @Synchronized override fun close() {
        closed = true
        // Screen disposal releases ExoPlayer separately; never reattach it later.
        shutdown(restoreSurface = false)
    }
}
