package com.sole.cinevault

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 1B.3 — permanent outside-dismiss surface.
 *
 * IMPORTANT: this composable must be emitted BEFORE every transient popup
 * surface in VideoPlayerScreen. Compose draws later siblings above earlier
 * siblings, so popup content receives scroll/click/drag first while taps on
 * uncovered video space reach this layer and dismiss the transient UI.
 */
@Composable
internal fun PlayerTransientDismissLayer(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            },
    )
}
