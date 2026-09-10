package com.sole.cinevault

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Slice 41: clears any system back-gesture exclusion rectangles the player
 * may have installed when the player leaves composition.
 *
 * This is Compose/Android lifecycle glue rather than decision logic, so it
 * intentionally has no JVM unit test.
 */
@Composable
fun PlayerGestureExclusionCleanupEffect(
    view: View,
) {
    DisposableEffect(view) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }
}
