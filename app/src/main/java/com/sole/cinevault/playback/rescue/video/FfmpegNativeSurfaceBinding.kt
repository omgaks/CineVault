package com.sole.cinevault.playback.rescue.video

import android.view.Surface
import androidx.annotation.OptIn
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * Observes the actual PlayerView video surface without taking it away from Media3.
 * A future rescue handoff must detach Media3 before starting native presentation.
 * Surface destruction stops native decoding before the underlying Surface is reused.
 */
@OptIn(UnstableApi::class)
internal class FfmpegNativeSurfaceBinding : AutoCloseable {
    private var holder: SurfaceHolder? = null
    @Volatile private var playback: FfmpegNativeVideoPlayback? = null
    @Volatile private var worker: Thread? = null
    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
        override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }
    }

    @Synchronized
    fun bind(playerView: PlayerView?) {
        val next = (playerView?.videoSurfaceView as? SurfaceView)?.holder
        if (holder === next) return
        stop()
        holder?.removeCallback(callback)
        holder = next
        next?.addCallback(callback)
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
        if (!surface.isValid || worker != null || path.isBlank() || resumePositionMs < 0L) return false
        // Prime the decoder worker before it starts so the first frame is
        // sought to the previous Media3 position and pause state is preserved.
        if (resumePositionMs > 0L) playback.seekTo(resumePositionMs)
        if (!playWhenReady) playback.pause()
        this.playback = playback
        worker = Thread({
            try {
                if (playback !== this.playback) return@Thread
                val stats = playback.play(path, surface)
                // Completion can mean EOF or a cooperative stop; the owner
                // decides whether to advance playback or discard the session.
                if (!playback.isStopRequested()) onCompleted(stats)
            } catch (error: Throwable) {
                if (!playback.isStopRequested()) onFailure(error)
            } finally {
                // An older worker must never erase a newer rescue session.
                synchronized(this@FfmpegNativeSurfaceBinding) {
                    if (worker === Thread.currentThread()) {
                        this.playback = null
                        worker = null
                    }
                }
            }
        }, "CineVault-FFmpeg-Video").apply { start() }
        return true
    }

    /** Refuse a second handoff while a previous worker is still shutting down. */
    fun isRunning(): Boolean = worker != null

    fun pause() { playback?.pause() }
    fun resume() { playback?.resume() }
    fun seekTo(positionMs: Long) { playback?.seekTo(positionMs) }
    fun stop() { playback?.stop() }

    @Synchronized
    override fun close() {
        stop()
        holder?.removeCallback(callback)
        holder = null
    }
}
