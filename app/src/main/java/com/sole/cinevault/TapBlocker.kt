package com.sole.cinevault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Makes a floating window swallow taps on its empty areas. Without it a tap on the window's
 * padding falls through to the full-screen "tap outside to close" layer underneath and the
 * window disappears while it is being used. Buttons and scrolling inside still work: child
 * controls get the touch first.
 */
fun Modifier.blockTapThrough(): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
    )
}
