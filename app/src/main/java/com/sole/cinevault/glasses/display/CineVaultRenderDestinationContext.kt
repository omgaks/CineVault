package com.sole.cinevault.glasses.display

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Identifies which physical render destination is currently composing the
 * canonical CineVault tree.
 *
 * The normal app tree defaults to HOST_DISPLAY. The external Presentation
 * overrides this to EXTERNAL_DISPLAY while still rendering the same
 * CineVaultRoot.
 */
val LocalCineVaultRenderDestination =
    staticCompositionLocalOf { CineVaultRenderDestination.HOST_DISPLAY }
