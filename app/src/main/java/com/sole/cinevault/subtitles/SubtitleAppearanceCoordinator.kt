package com.sole.cinevault.subtitles

import android.graphics.Color
import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sole.cinevault.SubtitleAppearanceUiState

/**
 * Owns applying CineVault subtitle appearance to Media3 SubtitleView.
 *
 * Position is sanitized at the final rendering boundary so every source of
 * subtitle state (Studio, Quick HUD, gestures, restored movie memory) remains
 * inside the visible-safe range.
 */
class SubtitleAppearanceCoordinator {

    fun apply(
        playerView: PlayerView?,
        appearanceUi: SubtitleAppearanceUiState,
        dualSubtitlesEnabled: Boolean,
        isAssOrSsaFormat: Boolean,
        availableWidthDp: Float,
        availableHeightDp: Float,
    ) {
        val subtitleView = playerView?.subtitleView ?: return

        subtitleView.setUserDefaultStyle()

        val useEmbeddedStyles =
            dualSubtitlesEnabled ||
                (appearanceUi.preserveOriginalStyling && isAssOrSsaFormat)

        subtitleView.setApplyEmbeddedStyles(useEmbeddedStyles)
        subtitleView.setApplyEmbeddedFontSizes(false)

        val renderedTextSizeSp = adaptiveSubtitleRenderSizeSp(
            requestedSp = appearanceUi.textSizeSp,
            availableWidthDp = availableWidthDp,
            availableHeightDp = availableHeightDp,
        )

        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            renderedTextSizeSp,
        )

        subtitleView.setBottomPaddingFraction(
            SubtitlePositionPolicy.sanitize(
                bottomPadding = appearanceUi.bottomPadding,
                textSizeSp = appearanceUi.textSizeSp,
            ),
        )

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
