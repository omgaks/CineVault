package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import com.sole.cinevault.CineVaultRoot

/**
 * D1-16: canonical tablet-side entry into the same CineVault session boundary
 * already used by the external display.
 *
 * This intentionally does not create a tablet-specific CineVault tree.
 */
@Composable
fun CineVaultTabletSessionHost() {
    CineVaultSessionRoot {
        CineVaultRoot()
    }
}
