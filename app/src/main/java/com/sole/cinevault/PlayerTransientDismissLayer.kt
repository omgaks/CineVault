package com.sole.cinevault

import androidx.compose.runtime.Composable

/**
 * 1B.2S — interaction-order regression repair.
 *
 * VideoPlayerScreen currently composes PlayerTransientDismissLayer AFTER
 * PlayerSubtitleSelectionAndAcquisitionSurfaces. In Compose, that places this
 * full-screen pointer surface above Style, Tracks and Smart Search, so their
 * first touch is consumed here instead of reaching the popup.
 *
 * Until the host is reordered, this layer must be non-intercepting.
 * Popups retain their own Close/Back controls. This deliberately trades
 * tap-outside-to-dismiss for correct popup interaction.
 */
@Composable
internal fun PlayerTransientDismissLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    // Intentionally no full-screen pointerInput surface.
    //
    // Do not restore detectTapGestures here unless this composable is moved
    // BELOW all transient popup/studio surfaces in VideoPlayerScreen.
    if (!visible) return
}
