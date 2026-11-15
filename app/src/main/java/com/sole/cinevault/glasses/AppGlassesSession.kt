package com.sole.cinevault.glasses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.sole.cinevault.findCineActivity
import com.sole.cinevault.glasses.display.CineVaultRenderDestination
import com.sole.cinevault.glasses.display.LocalCineVaultRenderDestination
import com.sole.cinevault.glasses.display.rememberSharedCineVaultExternalRuntime

/**
 * The glasses session for the whole app, not just the player.
 *
 * It starts when glasses are plugged in, wherever the person is in CineVault, and
 * lives until they are unplugged. The player (and anything else) reads this instead of
 * starting its own copy.
 */
@Stable
internal class AppGlassesSession(
    val display: ExternalDisplayInfo,
    val active: Boolean,
    val disable: () -> Unit,
)

internal val LocalAppGlassesSession = staticCompositionLocalOf<AppGlassesSession?> { null }

@Composable
internal fun ProvideAppGlassesSession(content: @Composable () -> Unit) {
    val destination = LocalCineVaultRenderDestination.current
    val context = LocalContext.current

    // The copy of CineVault drawn ON the glasses must never start another glasses
    // session of its own; only the phone's copy owns it.
    if (destination != CineVaultRenderDestination.HOST_DISPLAY) {
        LaunchedEffect(Unit) { GlassesTrace.log(context, "external copy of CineVault composed") }
        content()
        return
    }

    val activity = context.findCineActivity()
    val externalDisplay by rememberExternalDisplayState()
    var lifecycle by remember {
        mutableStateOf(ExternalDisplayLifecycleState(connectedDisplayId = externalDisplay.displayId))
    }
    LaunchedEffect(externalDisplay.displayId) {
        lifecycle = ExternalDisplayLifecyclePolicy.onDisplayChanged(
            previous = lifecycle,
            newDisplayId = externalDisplay.displayId,
        )
    }

    val shared = rememberSharedCineVaultExternalRuntime(
        activity = activity,
        externalDisplay = externalDisplay,
        enabled = lifecycle.isSessionEnabled,
    )

    LaunchedEffect(externalDisplay.displayId, shared.active) {
        GlassesTrace.log(
            context,
            "session: display=${externalDisplay.displayId} connected=${externalDisplay.isConnected} " +
                "enabled=${lifecycle.isSessionEnabled} externalActive=${shared.active}"
        )
    }
    DisposableEffect(Unit) {
        onDispose { GlassesTrace.log(context, "session ended (app root left composition)") }
    }

    val session = remember(externalDisplay, shared.active, lifecycle) {
        AppGlassesSession(
            display = externalDisplay,
            active = shared.active,
            disable = { lifecycle = ExternalDisplayLifecyclePolicy.disableCurrentSession(lifecycle) },
        )
    }
    CompositionLocalProvider(LocalAppGlassesSession provides session, content = content)
}
