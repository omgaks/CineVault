package com.sole.cinevault.tvmode

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore

/**
 * Wraps a single piece of touch UI (a button, a chip, a poster) in a
 * focusable, D-pad-activatable slot with a visible amber focus ring — WITHOUT
 * modifying the wrapped composable itself. First built for the player's
 * bottom transport row (phase 4 of TV support) and pulled out here so
 * DetailScreen (phase 5) and any future screen reuse the exact same
 * focus/key-handling/visual behavior instead of each growing its own
 * near-identical copy.
 *
 * On phone (isTelevision == false) this is a verified no-op: content()
 * renders directly, nothing extra is added to the tree.
 *
 * OK/Enter calls onActivate directly rather than depending on the wrapped
 * content's own onClick firing from a focus it doesn't actually receive —
 * this outer Box is what actually holds focus, so it has to be the one that
 * responds to the key press. This means the wrapped content's own onClick
 * (for touch) and this onActivate (for D-pad) should normally be the exact
 * same lambda, passed twice — once to the content, once here.
 */
@Composable
internal fun TvFocusableSlot(
    isTelevision: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    onActivate: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!isTelevision) {
        // Every existing call site omits `modifier`, so this stays the exact
        // same zero-overhead no-op it always was: content() renders directly
        // with nothing added to the tree. Only a call site that explicitly
        // passes a modifier (e.g. a BoxScope.align() a caller needs to reach
        // its parent) gets the extra wrapping Box — and needs it on phone
        // too, or that modifier would just silently do nothing there.
        if (modifier === Modifier) {
            content()
        } else {
            Box(modifier = modifier) { content() }
        }
        return
    }

    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .let { base -> if (focusRequester != null) base.focusRequester(focusRequester) else base }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                val isActivate = keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter
                if (isActivate && keyEvent.type == KeyEventType.KeyUp) {
                    onActivate()
                    true
                } else {
                    false
                }
            }
            .then(
                if (focused) {
                    Modifier
                        .clip(shape)
                        .border(2.dp, AmberCore, shape)
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
