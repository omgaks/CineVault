package com.sole.cinevault.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class RemoteLibraryState(
    val sourceName: String = "",
    val sourceType: NetworkType? = null,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val videos: List<NetworkVideo> = emptyList(),
    val errorMessage: String? = null,
)

class RemoteLibraryController(
    private val scope: CoroutineScope,
    private val onState: (RemoteLibraryState) -> Unit,
) {
    private var job: Job? = null
    private var source: NetworkSource? = null
    private var state = RemoteLibraryState()

    fun open(networkSource: NetworkSource) {
        source = networkSource
        job?.cancel()
        state = RemoteLibraryState(
            sourceName = networkSource.displayName,
            sourceType = networkSource.type,
            loading = true,
        )
        onState(state)
        job = scope.launch {
            state = try {
                when (val result = networkSource.testConnection()) {
                    NetworkConnectionResult.Connected -> {
                        val videos = networkSource.scan()
                            .filter { it.path.isNotBlank() && it.name.isNotBlank() }
                            .distinctBy { it.path }
                            .sortedBy { it.name.lowercase() }
                        state.copy(loading = false, connected = true, videos = videos, errorMessage = null)
                    }
                    is NetworkConnectionResult.Failed ->
                        state.copy(loading = false, connected = false, errorMessage = result.userMessage)
                }
            } catch (t: Throwable) {
                state.copy(
                    loading = false,
                    connected = false,
                    errorMessage = friendlyNetworkError(t),
                )
            }
            onState(state)
        }
    }

    fun retry() = source?.let(::open)
    fun close() { job?.cancel(); job = null; source = null }

    private fun friendlyNetworkError(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("401") || message.contains("403") ->
                "Sign-in was rejected. Check the saved credentials and try again."
            message.contains("timeout", ignoreCase = true) ->
                "The source took too long to respond. Check that it is online and on the same network."
            message.contains("host", ignoreCase = true) && message.contains("resolve", ignoreCase = true) ->
                "CineVault couldn't find that device on the network."
            else -> message.takeIf { it.isNotBlank() } ?: "CineVault couldn't open this network library."
        }
    }
}
