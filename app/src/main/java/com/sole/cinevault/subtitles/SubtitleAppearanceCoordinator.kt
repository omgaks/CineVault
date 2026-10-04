package com.sole.cinevault.subtitles

import android.graphics.Color
import android.util.TypedValue
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.sole.cinevault.SubtitleAppearanceUiState
import com.sole.cinevault.glasses.display.ExternalSubtitleFramePolicy
import com.sole.cinevault.glasses.display.ExternalVisibleFrame

/**
 * Owns applying CineVault subtitle appearance to Media3 SubtitleView.
 *
 * Position is sanitized at the final rendering boundary so every source of
 * subtitle state (Studio, Quick HUD, gestures, restored movie memory) remains
 * inside the visible-safe range.
 */
@OptIn(UnstableApi::class)
internal class SubtitleAppearanceCoordinator {

    fun apply(
        playerView: PlayerView?,
        appearanceUi: SubtitleAppearanceUiState,
        dualSubtitlesEnabled: Boolean,
        isAssOrSsaFormat: Boolean,
        availableWidthDp: Float,
        availableHeightDp: Float,
        externalVisibleFrame: ExternalVisibleFrame? = null,
        videoAspect: Float = 0f,
    ) {
        val subtitleView = playerView?.subtitleView ?: return

        subtitleView.setUserDefaultStyle()

        val useEmbeddedStyles =
            dualSubtitlesEnabled ||
                (appearanceUi.preserveOriginalStyling && isAssOrSsaFormat)

        subtitleView.setApplyEmbeddedStyles(useEmbeddedStyles)
        subtitleView.setApplyEmbeddedFontSizes(false)

        // The glasses / external display keeps its own fixed profile; every other screen gets
        // a size that follows the device and orientation.
        val renderedTextSizeSp = if (externalVisibleFrame != null) {
            adaptiveSubtitleRenderSizeSp(
                requestedSp = appearanceUi.textSizeSp,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )
        } else {
            autoSubtitleSizeSp(
                requestedSp = appearanceUi.textSizeSp,
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )
        }

        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            renderedTextSizeSp,
        )

        val renderedBottomPadding =
            externalVisibleFrame?.let { visibleFrame ->
                ExternalSubtitleFramePolicy.bottomPaddingForVisibleFrame(
                    requestedBottomPadding = appearanceUi.bottomPadding,
                    textSizeSp = appearanceUi.textSizeSp,
                    visibleFrame = visibleFrame,
                )
            } ?: run {
                val manual = SubtitlePositionPolicy.sanitize(
                    bottomPadding = appearanceUi.bottomPadding,
                    textSizeSp = appearanceUi.textSizeSp,
                )
                // Until the user moves the subtitles, place them next to the picture instead of
                // at a fixed screen position.
                if (!appearanceUi.autoPosition || playerView == null) {
                    manual
                } else {
                    val metrics = playerView.resources.displayMetrics
                    val density = metrics.density
                    val textPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        renderedTextSizeSp,
                        metrics,
                    )
                    // Preferred: the real on-screen position of the picture and the subtitle view.
                    val measured = measuredFraction(playerView, subtitleView, textPx)
                    // The layout may not be final yet (just rotated / just opened): measure again
                    // right after the next layout pass.
                    scheduleRemeasure(playerView, subtitleView, textPx)
                    (measured ?: SubtitleAutoPlacement.bottomPaddingFraction(
                        viewWidthPx = availableWidthDp * density,
                        viewHeightPx = availableHeightDp * density,
                        videoAspect = videoAspect,
                        textPx = textPx,
                        lines = 2,
                        fitMode = playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    ))?.coerceIn(
                        SubtitlePositionPolicy.MIN_BOTTOM_PADDING,
                        SubtitlePositionPolicy.maxBottomPadding(appearanceUi.textSizeSp),
                    ) ?: manual
                }
            }

        subtitleView.setBottomPaddingFraction(renderedBottomPadding)

        subtitleView.setStyle(
            CaptionStyleCompat(
                appearanceUi.appearance.foregroundColor,
                appearanceUi.appearance.backgroundColor,
                Color.TRANSPARENT,
                appearanceUi.appearance.edgeType,
                appearanceUi.appearance.edgeColor,
                null,
            ),
        )
    }

    private fun measuredFraction(
        playerView: androidx.media3.ui.PlayerView,
        subtitleView: androidx.media3.ui.SubtitleView,
        textPx: Float,
    ): Float? {
        val surface = playerView.videoSurfaceView ?: return null
        if (subtitleView.height <= 0 || surface.height <= 0 || surface.width <= 0) return null
        val svLoc = IntArray(2)
        val picLoc = IntArray(2)
        subtitleView.getLocationInWindow(svLoc)
        surface.getLocationInWindow(picLoc)
        val svTop = svLoc[1].toFloat()
        val svBottom = svTop + subtitleView.height
        // Zoomed / cropped video can extend beyond the screen: only the visible part counts.
        val picTop = maxOf(picLoc[1].toFloat(), svTop)
        val picBottom = minOf(picLoc[1].toFloat() + surface.height, svBottom)
        return SubtitleAutoPlacement.bottomPaddingFractionFromRects(
            subtitleViewBottomPx = svBottom,
            subtitleViewHeightPx = subtitleView.height.toFloat(),
            pictureTopPx = picTop,
            pictureBottomPx = picBottom,
            textPx = textPx,
            lines = 2,
        )
    }

    private var pendingRemeasure: android.view.View.OnLayoutChangeListener? = null

    private fun scheduleRemeasure(
        playerView: androidx.media3.ui.PlayerView,
        subtitleView: androidx.media3.ui.SubtitleView,
        textPx: Float,
    ) {
        pendingRemeasure?.let { playerView.removeOnLayoutChangeListener(it) }
        val listener = object : android.view.View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: android.view.View?, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or: Int, ob: Int,
            ) {
                playerView.removeOnLayoutChangeListener(this)
                if (pendingRemeasure === this) pendingRemeasure = null
                playerView.post {
                    measuredFraction(playerView, subtitleView, textPx)?.let {
                        subtitleView.setBottomPaddingFraction(
                            it.coerceIn(SubtitlePositionPolicy.MIN_BOTTOM_PADDING, 0.9f)
                        )
                    }
                }
            }
        }
        pendingRemeasure = listener
        playerView.addOnLayoutChangeListener(listener)
        // Also once after a short delay: video size/surface settle a moment after opening.
        playerView.postDelayed({
            measuredFraction(playerView, subtitleView, textPx)?.let {
                subtitleView.setBottomPaddingFraction(
                    it.coerceIn(SubtitlePositionPolicy.MIN_BOTTOM_PADDING, 0.9f)
                )
            }
        }, 450L)
    }
}
