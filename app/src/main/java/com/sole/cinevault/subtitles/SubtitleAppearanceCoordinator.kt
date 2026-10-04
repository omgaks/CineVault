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
                // Still at the default position (the user hasn't dragged or nudged it):
                // place the text next to the picture instead of at the far screen edge.
                val atDefault =
                    appearanceUi.bottomPadding <= SubtitlePositionPolicy.MIN_BOTTOM_PADDING + 0.004f
                if (!atDefault || playerView == null) {
                    manual
                } else {
                    val metrics = playerView.resources.displayMetrics
                    val density = metrics.density
                    val textPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        renderedTextSizeSp,
                        metrics,
                    )
                    SubtitleAutoPlacement.bottomPaddingFraction(
                        viewWidthPx = availableWidthDp * density,
                        viewHeightPx = availableHeightDp * density,
                        videoAspect = videoAspect,
                        textPx = textPx,
                        lines = 2,
                        fitMode = playerView.resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    )?.coerceIn(
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
}
