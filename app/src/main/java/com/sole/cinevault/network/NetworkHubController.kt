package com.sole.cinevault.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class NetworkHubState(
    val isDiscovering: Boolean = false,
    val discoveredDevices: List<DiscoveredNetworkDevice> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Small orchestration layer for the Network Hub.
 * Providers discover only; UI/user action decides whether to connect.
 */
class NetworkHubController(
    private val scope: CoroutineScope,
    private val discoveryProviders: List<NetworkDiscoveryProvider>,
    private val onState: (NetworkHubState) -> Unit,
) {
    private var job: Job? = null
    private var state = NetworkHubState()

    fun findDevices() {
        job?.cancel()
        state = state.copy(isDiscovering = true, errorMessage = null)
        onState(state)
        job = scope.launch {
            val discovered = mutableListOf<DiscoveredNetworkDevice>()
            discoveryProviders.forEach { provider ->
                runCatching { provider.discover() }
                    .onSuccess(discovered::addAll)
            }
            state = state.copy(
                isDiscovering = false,
                discoveredDevices = sanitizeDiscoveredDevices(discovered),
            )
            onState(state)
        }
    }

    fun stop() {
        job?.cancel()
        state = state.copy(isDiscovering = false)
        onState(state)
    }
}
