package com.sole.cinevault.subtitles

import android.graphics.Color
import android.util.TypedValue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.sole.cinevault.SubtitleAppearanceUiState

/**
 * Slice 31: owns applying CineVault subtitle appearance to Media3 SubtitleView.
 *
 * VideoPlayerScreen still owns the Compose effect keys so this is reapplied
 * whenever the player view, style, size, position, dual-sub mode, or embedded
 * styling preference changes.
 */
class SubtitleAppearanceCoordinator {

    fun apply(
        playerView: PlayerView?,
        appearanceUi: SubtitleAppearanceUiState,
        dualSubtitlesEnabled: Boolean,
        isAssOrSsaFormat: Boolean,
    ) {
        val subtitleView = playerView?.subtitleView ?: return

        subtitleView.setUserDefaultStyle()

        // Dual subtitles need the injected <font color> tag to remain active.
        // ASS/SSA embedded styling is only honoured when the user explicitly
        // asks to preserve it.
        val useEmbeddedStyles =
            dualSubtitlesEnabled ||
                (appearanceUi.preserveOriginalStyling && isAssOrSsaFormat)

        subtitleView.setApplyEmbeddedStyles(useEmbeddedStyles)
        subtitleView.setApplyEmbeddedFontSizes(false)

        subtitleView.setFixedTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            appearanceUi.textSizeSp,
        )

        subtitleView.setBottomPaddingFraction(
            appearanceUi.bottomPadding,
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
