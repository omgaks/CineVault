package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable

/**
 * Android refuses a pop-up Dialog / bottom sheet inside the glasses window ("Window type
 * mismatch") and that killed the app, e.g. opening Network play. These pop-ups only ever
 * open on the tablet; the glasses copy simply skips them. Wrap any Dialog-style window.
 */
@Composable
internal inline fun HostWindowOnly(content: @Composable () -> Unit) {
    if (LocalCineVaultRenderDestination.current != CineVaultRenderDestination.EXTERNAL_DISPLAY) {
        content()
    }
}
