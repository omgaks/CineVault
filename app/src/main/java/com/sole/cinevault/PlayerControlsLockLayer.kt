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

@Composable
internal fun BoxScope.PlayerControlsLockLayer(
    controlsLocked: Boolean,
    lockButtonVisible: Boolean,
    isLandscape: Boolean,
    onLockedSurfaceTap: () -> Unit,
    onToggleLock: () -> Unit,
) {
    if (controlsLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        onLockedSurfaceTap()
                    }
                }
        )
    }

    AnimatedVisibility(
        visible = lockButtonVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd)
    ) {
        Box(
            modifier = Modifier
                .padding(top = if (isLandscape) 10.dp else 14.dp, end = 14.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(AmberCore)
                .clickable(onClick = onToggleLock)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (controlsLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = if (controlsLocked) "Unlock controls" else "Lock controls",
                tint = Color.Black,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
