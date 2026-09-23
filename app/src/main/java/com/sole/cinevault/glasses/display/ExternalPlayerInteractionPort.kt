package com.sole.cinevault.glasses.display

import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * D7-5 — shared-renderer interaction contract.
 *
 * The legacy ExternalPresentationHandle adapter has been removed from this
 * boundary. Player code now depends only on capabilities that a shared
 * CineVault external renderer may provide.
 *
 * This port owns no playback engine, subtitle state, navigation state,
 * FFmpeg state, or device-specific UI tree.
 */
@UnstableApi
interface ExternalPlayerInteractionPort {
    val playerView: PlayerView?
    val controlsVisible: State<Boolean>

    fun showControls()
    fun hideControls()
    fun movePointer(deltaX: Float, deltaY: Float)
    fun clickPointer(): Boolean
    fun updateSeekPreview(bitmap: Bitmap?, positionMs: Long, visible: Boolean)
    fun applyViewportTransform(zoom: Float, panX: Float, panY: Float)
    fun updateResizeMode(resizeMode: Int)
    fun showGestureHud(label: String, value: String? = null, progress: Int? = null)
    fun showTouchPulse()
    fun enterTabletStandby()
}
