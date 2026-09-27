package com.sole.cinevault.network

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class NetworkHubRuntime(
    context: Context,
    scope: CoroutineScope,
    onState: (NetworkHubState) -> Unit,
) {
    private val controller = NetworkHubController(
        scope = scope,
        discoveryProviders = listOf(
            CineVaultLanDiscoveryProvider(context.applicationContext),
            DlnaDiscoveryProvider(),
        ),
        onState = onState,
    )

    fun findDevices() = controller.findDevices()
    fun stop() = controller.stop()
}
