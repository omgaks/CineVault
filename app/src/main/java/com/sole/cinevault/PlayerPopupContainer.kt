package com.sole.cinevault

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 1B.2R — shared floating popup interaction contract.
 *
 * Important:
 * - ordinary taps/clicks remain owned by child controls
 * - ordinary vertical drags remain owned by child scroll containers
 * - moving the popup requires a long-press followed by drag
 *
 * Do NOT add a root detectTapGestures{} "touch blocker" here. That parent
 * recognizer competes with clickable/verticalScroll and was the cause of
 * Style/Tracks becoming unscrollable or apparently dismissing on touch.
 */
@Composable
fun DraggableFloatingPopup(
    containerWidth: Dp,
    containerHeight: Dp,
    popupWidth: Dp,
    popupMaxHeight: Dp,
    onUserInteraction: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    val maxOffsetXPx = with(density) {
        ((containerWidth - popupWidth) / 2).coerceAtLeast(0.dp).toPx()
    }
    val maxOffsetYPx = with(density) {
        ((containerHeight - popupMaxHeight) / 2).coerceAtLeast(0.dp).toPx()
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    dragOffsetX.roundToInt(),
                    dragOffsetY.roundToInt()
                )
            }
            .pointerInput(maxOffsetXPx, maxOffsetYPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onUserInteraction() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onUserInteraction()
                        dragOffsetX =
                            (dragOffsetX + dragAmount.x)
                                .coerceIn(-maxOffsetXPx, maxOffsetXPx)
                        dragOffsetY =
                            (dragOffsetY + dragAmount.y)
                                .coerceIn(-maxOffsetYPx, maxOffsetYPx)
                    }
                )
            }
    ) {
        content()
    }
}
