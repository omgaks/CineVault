package com.sole.cinevault.glasses.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.PlayerView

/**
 * The video view currently drawn ON the glasses (null when no film is showing there).
 *
 * The subtitle system lives in the phone's player screen, which only knows its own hidden
 * view. This hands it the glasses view as well so subtitles (embedded, downloaded, generated
 * and dual) and their styling reach the glasses.
 */
internal object ExternalPlayerViewRegistry {
    var playerView: PlayerView? by mutableStateOf(null)
}
