package com.sole.cinevault.glasses.halo

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * D2-8 — canonical CineVault/Halo integration boundary.
 *
 * This is the first D2 slice that wraps the REAL CineVault composition.
 * It does not render a replacement library, detail page, player or menu.
 *
 * The same wrapper can be used by both the tablet and external-display
 * CineVault roots. D2-9 can add visible cursor/hit-target dispatch here without
 * spreading Halo plumbing through every CineVault screen.
 */
@Composable
fun HaloCanonicalCineVaultSurface(
    enabled: Boolean = true,
    onActivity: (HaloActivityEvent) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coordinator = remember { HaloInteractionCoordinator() }
    val clock = remember { HaloEventClock() }

    HaloInputSurface(
        enabled = enabled,
        modifier = modifier,
        onSample = { sample ->
            val now = SystemClock.uptimeMillis()
            val frame = coordinator.update(
                sample = sample,
                eventTimeMillis = now,
                deltaTimeMillis = clock.deltaMillis(now),
            )
            frame.activity?.let(onActivity)
        },
        content = content,
    )
}

/**
 * Keeps event timing local to the Halo surface and avoids a frame-rate
 * assumption in HaloMotionEngine.
 */
internal class HaloEventClock {
    private var previousMillis: Long? = null

    fun deltaMillis(nowMillis: Long): Long {
        val previous = previousMillis
        previousMillis = nowMillis
        return if (previous == null) 16L
        else (nowMillis - previous).coerceAtLeast(1L)
    }

    fun reset() {
        previousMillis = null
    }
}
