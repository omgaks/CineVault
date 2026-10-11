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
    fun start(path: String, playback: FfmpegNativeVideoPlayback, onFailure: (Throwable) -> Unit): Boolean {
        val surface: Surface = holder?.surface ?: return false
        if (!surface.isValid || worker != null || path.isBlank()) return false
        this.playback = playback
        worker = Thread({
            try {
                playback.play(path, surface)
            } catch (error: Throwable) {
                onFailure(error)
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

    fun pause() { playback?.pause() }
    fun resume() { playback?.resume() }
    fun seekTo(positionMs: Long) { playback?.seekTo(positionMs) }
    fun stop() { playback?.stop() }

    override fun close() {
        stop()
        holder?.removeCallback(callback)
        holder = null
    }
}
