package com.sole.cinevault.glasses.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * D1-15: Compose root boundary for one logical CineVault session.
 *
 * Both the tablet root and the external-display root will enter CineVault
 * through this provider. The provider exposes ONE session-owned observable
 * app-state bridge without putting display-specific code into CineVault UI.
 */
object CineVaultDisplaySessionEnvironment {
    val sessionId = CineVaultRenderSessionId(1L)

    private val composeRegistry = CineVaultComposeAppStateRegistry()

    fun appState(): CineVaultComposeAppState =
        composeRegistry.stateFor(sessionId)

    fun reset() {
        composeRegistry.release(sessionId)
    }
}

val LocalCineVaultComposeAppState =
    staticCompositionLocalOf<CineVaultComposeAppState> {
        error("CineVaultSessionRoot is missing from this composition.")
    }

@Composable
fun CineVaultSessionRoot(
    content: @Composable () -> Unit,
) {
    val appState = CineVaultDisplaySessionEnvironment.appState()

    CompositionLocalProvider(
        LocalCineVaultComposeAppState provides appState,
        content = content,
    )
}
