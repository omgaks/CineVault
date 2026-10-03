package com.sole.cinevault.subtitles

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Wraps any Studio window content in free-drag behavior, reachable to any
 * part of the screen — the shared requirement across Quick HUD, the
 * Studio pill, and every category window (Download, Power Tools, Style,
 * Settings, Dual Subs). One implementation instead of seven copies of the
 * same clamp-to-bounds math.
 *
 * `dragHandle` content (a grip icon + title row, typically) is where the
 * drag gesture is attached — not the whole window — so sliders, buttons,
 * and other interactive content inside the window still receive their own
 * touch events instead of being swallowed by a drag listener covering the
 * entire surface. Pass `Modifier` through to whatever you want to be the
 * grabbable region.
 *
 * `containerSize` is the bounds the window is clamped within — pass the
 * player's own measured size, not the device screen, so a window can
 * never be dragged out under the system status bar or nav gesture area.
 *
 * Position starts at `initialOffset` and is remembered across
 * recompositions but NOT across the window being closed and reopened —
 * each open starts fresh at `initialOffset`, since a window that
 * "remembers where you left it" across sessions was not part of what was
 * asked for, and silently persisting position is a bigger behavior change
 * than this component should decide on its own.
 */
@Composable
fun DraggableStudioWindow(
    initialOffset: Offset,
    containerSize: IntSize,
    modifier: Modifier = Modifier,
    // When true the window sits at the right edge, vertically centred in the available
    // space, in any orientation, until the user drags it somewhere else.
    anchorCenterEnd: Boolean = false,
    anchorMarginPx: Float = 0f,
    content: @Composable (dragHandleModifier: Modifier) -> Unit
) {
    var offset by remember(initialOffset) { mutableStateOf(initialOffset) }
    var userMoved by remember(initialOffset) { mutableStateOf(false) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    val measured = windowSize != IntSize.Zero
    val anchored = anchorCenterEnd && !userMoved && measured
    val effective = if (anchored) {
        Offset(
            x = (containerSize.width - windowSize.width - anchorMarginPx).coerceAtLeast(0f),
            y = ((containerSize.height - windowSize.height) / 2f).coerceAtLeast(0f),
        )
    } else {
        offset
    }

    val dragModifier = Modifier.pointerInput(containerSize, windowSize, anchored) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val maxX = (containerSize.width - windowSize.width).coerceAtLeast(0).toFloat()
            val maxY = (containerSize.height - windowSize.height).coerceAtLeast(0).toFloat()
            val base = if (anchored) effective else offset
            userMoved = true
            offset = Offset(
                x = (base.x + dragAmount.x).coerceIn(0f, maxX),
                y = (base.y + dragAmount.y).coerceIn(0f, maxY)
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { windowSize = it.size }
            // Stay invisible for the one frame before the window has been measured, so an
            // anchored window never flashes at the wrong spot.
            .graphicsLayer(alpha = if (anchorCenterEnd && !measured) 0f else 1f)
            .offset { IntOffset(effective.x.roundToInt(), effective.y.roundToInt()) }
    ) {
        content(dragModifier)
    }
}
