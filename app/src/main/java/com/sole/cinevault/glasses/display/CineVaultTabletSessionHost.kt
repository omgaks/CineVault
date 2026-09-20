package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import com.sole.cinevault.CineVaultRoot
import com.sole.cinevault.glasses.halo.HaloCanonicalCineVaultSurface

/**
 * D2-8: the tablet enters the canonical CineVault session through the same
 * transparent Halo integration boundary used by the external display.
 *
 * CineVaultRoot remains the real UI. Halo is additive.
 */
@Composable
fun CineVaultTabletSessionHost() {
    CineVaultSessionRoot {
        HaloCanonicalCineVaultSurface {
            CineVaultRoot()
        }
    }
}
