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
    content: @Composable (dragHandleModifier: Modifier) -> Unit
) {
    var offset by remember(initialOffset) { mutableStateOf(initialOffset) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }

    val dragModifier = Modifier.pointerInput(containerSize, windowSize) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            val maxX = (containerSize.width - windowSize.width).coerceAtLeast(0).toFloat()
            val maxY = (containerSize.height - windowSize.height).coerceAtLeast(0).toFloat()
            offset = Offset(
                x = (offset.x + dragAmount.x).coerceIn(0f, maxX),
                y = (offset.y + dragAmount.y).coerceIn(0f, maxY)
            )
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { windowSize = it.size }
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
    ) {
        content(dragModifier)
    }
}
