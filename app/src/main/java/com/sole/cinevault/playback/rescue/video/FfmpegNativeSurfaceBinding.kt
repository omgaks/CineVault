package com.sole.cinevault.playback.rescue.video

import android.view.Surface
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer

/**
 * Observes the actual PlayerView video surface without taking it away from Media3.
 * Rescue detaches Media3 before starting native presentation.
 * Surface destruction stops native decoding before the underlying Surface is reused.
 */
@OptIn(UnstableApi::class)
internal class FfmpegNativeSurfaceBinding : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var holder: SurfaceHolder? = null
    private var media3SurfaceReleased = false
    private var surfaceGeneration = 0L
    private var sessionGeneration = 0L
    var onSurfaceUnavailable: (() -> Unit)? = null
    private val stoppedCallbacks = mutableListOf<() -> Unit>()
    @Volatile private var playback: FfmpegNativeVideoPlayback? = null
    @Volatile private var worker: Thread? = null
    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            synchronized(this@FfmpegNativeSurfaceBinding) {
                if (this@FfmpegNativeSurfaceBinding.holder !== holder) return
                surfaceGeneration++
                stop()
                onSurfaceUnavailable?.invoke()
            }
        }
    }

    @Synchronized
    fun bind(playerView: PlayerView?) {
        val next = (playerView?.videoSurfaceView as? SurfaceView)?.holder
        if (holder === next) return
        val wasRunning = isRunning()
        stop()
        if (wasRunning) onSurfaceUnavailable?.invoke()
        surfaceGeneration++
        media3SurfaceReleased = false
        holder?.removeCallback(callback)
        holder = next
        next?.addCallback(callback)
    }

    /**
     * Explicitly release Media3's video output before native presentation.
     * Call from the player/main thread, never from the decoder worker.
     * The player owner remains responsible for audio and control transfer.
     */
    @Synchronized
    fun releaseMedia3VideoSurface(player: ExoPlayer, playerView: PlayerView): Boolean {
        val surfaceView = playerView.videoSurfaceView as? SurfaceView ?: return false
        if (holder !== surfaceView.holder || worker != null) return false
        if (!surfaceView.holder.surface.isValid) return false
        player.clearVideoSurfaceView(surfaceView)
        // PlayerView waits for a Media3 first-frame event that native output never sends.
        playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        playerView.hideController()
        media3SurfaceReleased = true
        return true
    }

    /** Reattach Media3 only after the native worker has fully exited. */
    @Synchronized
    fun restoreMedia3VideoSurface(player: ExoPlayer, playerView: PlayerView): Boolean {
        val surfaceView = playerView.videoSurfaceView as? SurfaceView ?: return false
        if (holder !== surfaceView.holder || worker != null || !media3SurfaceReleased) return false
        playerView.setShutterBackgroundColor(android.graphics.Color.BLACK)
        player.setVideoSurfaceView(surfaceView)
        media3SurfaceReleased = false
        return true
    }

    /**
     * A failed handoff must not leave Media3 permanently disconnected.
     * This rollback is safe only before a native worker starts.
     */
    @Synchronized
    fun rollbackMedia3VideoSurface(player: ExoPlayer, playerView: PlayerView): Boolean {
        if (worker != null) return false
        return restoreMedia3VideoSurface(player, playerView)
    }

    /**
     * Called only after Media3 relinquishes the surface during a verified rescue
     * handoff. Does not automatically change player ownership or audio routing.
     */
    @Synchronized
    fun start(
        path: String,
        playback: FfmpegNativeVideoPlayback,
        onFailure: (Throwable) -> Unit,
        resumePositionMs: Long = 0L,
        playWhenReady: Boolean = true,
        onCompleted: (FfmpegNativeVideoPlayback.Stats) -> Unit = {},
    ): Boolean {
        val surface: Surface = holder?.surface ?: return false
        if (!surface.isValid || worker != null || !media3SurfaceReleased || path.isBlank() || resumePositionMs < 0L) return false
        // Prime the decoder worker before it starts so the first frame is
        // sought to the previous Media3 position and pause state is preserved.
        if (resumePositionMs > 0L) playback.seekTo(resumePositionMs)
        if (!playWhenReady) playback.pause()
        this.playback = playback
        val generation = surfaceGeneration
        val session = ++sessionGeneration
        worker = Thread({
            var stats: FfmpegNativeVideoPlayback.Stats? = null
            var failure: Throwable? = null
            try {
                if (playback === this.playback && !playback.isStopRequested()) {
                    stats = playback.play(path, surface)
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                // Release worker ownership before posting completion. This lets
                // the main-thread coordinator restore the Media3 surface safely.
                val callbacks = synchronized(this@FfmpegNativeSurfaceBinding) {
                    if (worker === Thread.currentThread()) {
                        this.playback = null
                        worker = null
                    }
                    stoppedCallbacks.toList().also { stoppedCallbacks.clear() }
                }
                // Shutdown callbacks run even when surface/session invalidation
                // suppresses a normal completion event.
                mainHandler.post { callbacks.forEach { it() } }
                val result = stats
                val error = failure
                if (result != null || error != null) {
                    mainHandler.post {
                        val stillCurrent = synchronized(this@FfmpegNativeSurfaceBinding) {
                            generation == surfaceGeneration && session == sessionGeneration
                        }
                        if (stillCurrent && !playback.isStopRequested()) {
                            if (error != null) onFailure(error)
                            else if (result != null) onCompleted(result)
                        }
                    }
                }
            }
        }, "CineVault-FFmpeg-Video").apply { start() }
        return true
    }

    /**
     * Atomic preflight for video-surface ownership. Audio/control takeover
     * must be arranged by the caller before accepting the rescue request.
     * If the worker cannot start, restore Media3 immediately.
     */
    @Synchronized
    fun startAfterMedia3Release(
        player: ExoPlayer,
        playerView: PlayerView,
        path: String,
        playback: FfmpegNativeVideoPlayback,
        resumePositionMs: Long,
        playWhenReady: Boolean,
        onFailure: (Throwable) -> Unit,
        onCompleted: (FfmpegNativeVideoPlayback.Stats) -> Unit = {},
    ): Boolean {
        if (!releaseMedia3VideoSurface(player, playerView)) return false
        return try {
            val started = start(
                path = path,
                playback = playback,
                onFailure = onFailure,
                resumePositionMs = resumePositionMs,
                playWhenReady = playWhenReady,
                onCompleted = onCompleted,
            )
            if (!started) rollbackMedia3VideoSurface(player, playerView)
            started
        } catch (error: Throwable) {
            rollbackMedia3VideoSurface(player, playerView)
            throw error
        }
    }

    /** Refuse a second handoff while a previous worker is still shutting down. */
    fun isRunning(): Boolean = worker != null

    @Synchronized fun isSurfaceReady(): Boolean = holder?.surface?.isValid == true && worker == null
    fun frameStats(): FfmpegNativeVideoPlayback.Stats? = playback?.let { FfmpegNativeVideoPlayback.Stats(it.presentedFrames, it.droppedFrames) }

    fun pause() { playback?.pause() }
    fun resume() { playback?.resume() }
    fun seekTo(positionMs: Long) { playback?.seekTo(positionMs) }
    @Synchronized
    fun stop(onStopped: (() -> Unit)? = null) {
        sessionGeneration++
        playback?.stop()
        if (onStopped != null) {
            if (worker == null) mainHandler.post { onStopped() }
            else stoppedCallbacks += onStopped
        }
    }

    @Synchronized
    override fun close() {
        stop()
        surfaceGeneration++
        holder?.removeCallback(callback)
        holder = null
        onSurfaceUnavailable = null
        media3SurfaceReleased = false
    }
}
