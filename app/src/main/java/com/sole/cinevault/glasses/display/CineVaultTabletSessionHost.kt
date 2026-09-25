package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import com.sole.cinevault.CineVaultRoot

/**
 * R1.1: the phone/tablet owns the canonical CineVault host UI only.
 *
 * Halo is external-display-only. The host must not wrap CineVaultRoot in
 * HaloCanonicalCineVaultSurface, even when no glasses are connected.
 * Cinema Void remote input remains handled by its player-specific host surface.
 */
@Composable
fun CineVaultTabletSessionHost() {
    CineVaultSessionRoot {
        CineVaultRoot()
    }
}
