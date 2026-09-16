package com.sole.cinevault

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * One full-player dismissal surface for transient menus/studios.
 *
 * It is deliberately placed above the video gesture layer and below the
 * actual popup/studio content. This means a tap on unused video space closes
 * the active transient UI, while taps inside the popup continue to belong to
 * that popup. Keeping this behaviour in one layer prevents each menu from
 * inventing a different empty-space contract.
 */
@Composable
internal fun PlayerTransientDismissLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    )
}
