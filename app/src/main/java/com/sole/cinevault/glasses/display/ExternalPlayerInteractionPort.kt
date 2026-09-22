package com.sole.cinevault.glasses.display

import android.graphics.Bitmap
import androidx.compose.runtime.State
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.sole.cinevault.glasses.ExternalPresentationHandle

/**
 * D7-2 — live migration seam between the real CineVault player and whichever
 * external presentation implementation currently owns the external surface.
 *
 * CineVault player code depends on this small capability port, not on the
 * legacy ExternalDisplayPresentation implementation. D7 can therefore replace
 * the legacy renderer without rewriting player feature logic.
 *
 * This port contains interaction/presentation capabilities only. It owns no
 * subtitles, playback engine, navigation, FFmpeg state, or feature state.
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

/**
 * Temporary adapter for the legacy presentation.
 *
 * Delete this adapter together with the legacy presentation once the shared
 * CineVault external renderer supplies the port directly.
 */
@UnstableApi
class LegacyExternalPlayerInteractionAdapter(
    private val legacy: ExternalPresentationHandle,
) : ExternalPlayerInteractionPort {

    override val playerView: PlayerView
        get() = legacy.playerView

    override val controlsVisible: State<Boolean>
        get() = legacy.controlsVisible

    override fun showControls() = legacy.showControls()

    override fun hideControls() = legacy.hideControls()

    override fun movePointer(deltaX: Float, deltaY: Float) =
        legacy.movePointer(deltaX, deltaY)

    override fun clickPointer(): Boolean =
        legacy.clickPointer()

    override fun updateSeekPreview(
        bitmap: Bitmap?,
        positionMs: Long,
        visible: Boolean,
    ) = legacy.updateSeekPreview(bitmap, positionMs, visible)

    override fun applyViewportTransform(
        zoom: Float,
        panX: Float,
        panY: Float,
    ) = legacy.applyViewportTransform(zoom, panX, panY)

    override fun updateResizeMode(resizeMode: Int) =
        legacy.updateResizeMode(resizeMode)

    override fun showGestureHud(
        label: String,
        value: String?,
        progress: Int?,
    ) = legacy.showGestureHud(label, value, progress)

    override fun showTouchPulse() =
        legacy.showTouchPulse()

    override fun enterTabletStandby() =
        legacy.enterTabletStandby()
}
