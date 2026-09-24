package com.sole.cinevault.glasses.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

internal data class ExternalViewportTransform(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)


internal data class ExternalVisibleFrame(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val scale: Float = 1f,
) {
    val horizontalInsetFraction: Float
        get() = ((1f - scale.coerceAtMost(1f)) / 2f).coerceAtLeast(0f)

    val verticalInsetFraction: Float
        get() = horizontalInsetFraction
}

internal object ExternalViewportSessionState {
    internal const val MIN_SCALE = 0.75f
    internal const val MAX_SCALE = 3f

    private const val PRESET_SNAP_TOLERANCE = 0.008f
    private val usefulScalePresets = floatArrayOf(0.90f, 0.95f, 1.00f)

    private var viewportWidthPx: Int = 0
    private var viewportHeightPx: Int = 0
    private var boundProfileName: String? = null
    private var persistTransform: ((ExternalViewportTransform) -> Unit)? = null

    var transform by mutableStateOf(ExternalViewportTransform())
        private set

    val visibleFrame: ExternalVisibleFrame
        get() =
            ExternalVisibleFrame(
                widthPx = viewportWidthPx,
                heightPx = viewportHeightPx,
                scale = transform.scale,
            )

    fun bindProfile(
        profileName: String?,
        restoredTransform: ExternalViewportTransform?,
        onTransformChanged: ((ExternalViewportTransform) -> Unit)?,
    ) {
        if (profileName == null) {
            boundProfileName = null
            persistTransform = null
            transform = ExternalViewportTransform()
            return
        }

        if (boundProfileName == profileName) {
            persistTransform = onTransformChanged
            return
        }

        boundProfileName = profileName
        persistTransform = onTransformChanged
        transform = clampTransform(restoredTransform ?: ExternalViewportTransform())
    }

    fun unbindProfile(profileName: String?) {
        if (profileName != null && boundProfileName != profileName) return
        boundProfileName = null
        persistTransform = null
    }

    fun updateViewportSize(
        widthPx: Int,
        heightPx: Int,
    ) {
        viewportWidthPx = widthPx.coerceAtLeast(0)
        viewportHeightPx = heightPx.coerceAtLeast(0)
        transform = clampTransform(transform)
    }

    fun applyGesture(
        zoom: Float,
        panX: Float,
        panY: Float,
    ) {
        val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
        val rawScale = (transform.scale * safeZoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val nextScale = snapUsefulScale(rawScale)

        publishTransform(
            clampTransform(
                ExternalViewportTransform(
                    scale = nextScale,
                    panX = transform.panX + panX.takeIf(Float::isFinite).orZero(),
                    panY = transform.panY + panY.takeIf(Float::isFinite).orZero(),
                )
            )
        )
    }

    fun reset() {
        publishTransform(ExternalViewportTransform())
    }

    private fun publishTransform(value: ExternalViewportTransform) {
        transform = value
        persistTransform?.invoke(value)
    }

    internal fun clampTransform(
        candidate: ExternalViewportTransform,
    ): ExternalViewportTransform {
        val safeScale =
            candidate.scale
                .takeIf { it.isFinite() }
                ?.coerceIn(MIN_SCALE, MAX_SCALE)
                ?: 1f

        // A reduced viewport is intentionally centred. There is no hidden
        // content to recover by panning when the whole movie is smaller than
        // the available display.
        if (safeScale <= 1f) {
            return ExternalViewportTransform(scale = safeScale)
        }

        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return ExternalViewportTransform(
                scale = safeScale,
                panX = candidate.panX.takeIf(Float::isFinite).orZero(),
                panY = candidate.panY.takeIf(Float::isFinite).orZero(),
            )
        }

        // graphicsLayer scales around the surface centre. These bounds are the
        // exact extra half-width/half-height created by the scale, so clamping
        // here prevents panning the movie completely away from the display.
        val maxPanX = viewportWidthPx * (safeScale - 1f) / 2f
        val maxPanY = viewportHeightPx * (safeScale - 1f) / 2f

        return ExternalViewportTransform(
            scale = safeScale,
            panX = candidate.panX.coerceIn(-maxPanX, maxPanX),
            panY = candidate.panY.coerceIn(-maxPanY, maxPanY),
        )
    }

    private fun snapUsefulScale(scale: Float): Float {
        val preset =
            usefulScalePresets.minByOrNull { candidate ->
                abs(candidate - scale)
            }

        return if (
            preset != null &&
            abs(preset - scale) <= PRESET_SNAP_TOLERANCE
        ) {
            preset
        } else {
            scale
        }
    }

    private fun Float?.orZero(): Float = this ?: 0f
}
