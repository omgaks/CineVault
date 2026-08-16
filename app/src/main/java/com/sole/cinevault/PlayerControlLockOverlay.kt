package com.sole.cinevault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sole.cinevault.ui.theme.AmberCore

/*
 * PlayerControlLockOverlay.kt
 *
 * Owns the touch absorber and the visible Lock/Unlock button. The lock state,
 * haptic feedback and auto-hide timing remain owned by VideoPlayerScreen; this
 * composable only renders the supplied state and reports user actions.
 */

@Composable
internal fun BoxScope.PlayerControlLockOverlay(
    controlsLocked: Boolean,
    lockButtonVisibleWhileLocked: Boolean,
    showControls: Boolean,
    externalPlayerActive: Boolean,
    isInPipMode: Boolean,
    isLandscape: Boolean,
    onRevealLockButton: () -> Unit,
    onToggleLock: () -> Unit,
) {
    // Kept at the same point in the outer player Box as the original inline
    // block. When locked, this full-screen layer consumes touches before they
    // can reach the transport controls; tapping it reveals only Unlock.
    if (controlsLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onRevealLockButton() }
                },
        )
    }

    AnimatedVisibility(
        visible = externalPlayerActive.not() &&
            (if (controlsLocked) lockButtonVisibleWhileLocked else showControls) &&
            isInPipMode.not(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        Box(
            modifier = Modifier
                .padding(top = if (isLandscape) 10.dp else 14.dp, end = 14.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(AmberCore)
                .clickable(onClick = onToggleLock)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (controlsLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (controlsLocked) "Unlock controls" else "Lock controls",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
