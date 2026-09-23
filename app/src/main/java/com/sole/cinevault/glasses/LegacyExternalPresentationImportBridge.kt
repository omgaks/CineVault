package com.sole.cinevault.glasses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player

/**
 * D8-1 temporary source-compatibility bridge.
 *
 * PlayerVisualComponents still carries one stale import for the removed
 * legacy presentation API. Code search confirms that alias is never invoked.
 * This declaration exists only to keep that import source-compatible until
 * PlayerVisualComponents is replaced as a complete file in the next slice.
 *
 * It does not create a Presentation, PlayerView, renderer, second player,
 * controls, subtitles, or any external-display UI.
 */
@Deprecated(
    message = "Legacy external presentation removed; shared CineVault renderer is authoritative.",
)
@Composable
fun rememberExternalVideoPresentation(
    player: Player,
    externalDisplay: ExternalDisplayInfo,
    title: String,
    ratingText: String?,
    onBack: () -> Unit,
    cinemaVoidEnabled: Boolean = false,
    initialSubtitleContentLocked: Boolean = false,
): State<Nothing?> {
    @Suppress("UNUSED_VARIABLE")
    val ignored = arrayOf(
        player,
        externalDisplay,
        title,
        ratingText,
        onBack,
        cinemaVoidEnabled,
        initialSubtitleContentLocked,
    )
    return remember { mutableStateOf(null) }
}
