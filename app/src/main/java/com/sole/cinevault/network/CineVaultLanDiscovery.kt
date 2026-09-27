package com.sole.cinevault.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

private const val CINEVAULT_SERVICE_TYPE = "_cinevault._tcp."
private const val DEFAULT_DISCOVERY_WINDOW_MS = 4_000L

data class CineVaultLanEndpoint(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
) {
    fun baseUrl(): String = "http://${formatHostForUrl(host)}:$port"
}

/**
 * Android NSD/mDNS provider for CineVault peers.
 *
 * Discovery is intentionally not trust. It only returns an address hint and
 * opaque device identity. Pairing/session approval remains owned by S5's
 * NearbyPairingPolicy.
 */
class CineVaultLanDiscoveryProvider(
    context: Context,
    private val discoveryWindowMs: Long = DEFAULT_DISCOVERY_WINDOW_MS,
) : NetworkDiscoveryProvider {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)

    override suspend fun discover(): List<DiscoveredNetworkDevice> =
        discoverEndpoints().map { endpoint ->
            DiscoveredNetworkDevice(
                id = endpoint.deviceId,
                displayName = endpoint.deviceName,
                kind = NetworkDiscoveryKind.CINEVAULT,
                addressHint = endpoint.baseUrl(),
            )
        }

    suspend fun discoverEndpoints(): List<CineVaultLanEndpoint> =
        withTimeoutOrNull(discoveryWindowMs + 1_000L) {
            suspendCancellableCoroutine { continuation ->
                val resolved = ConcurrentHashMap<String, CineVaultLanEndpoint>()
                var stopped = false

                fun finish(listener: NsdManager.DiscoveryListener) {
                    if (stopped) return
                    stopped = true
                    runCatching { nsd.stopServiceDiscovery(listener) }
                    if (continuation.isActive) {
                        continuation.resume(resolved.values.sortedBy { it.deviceName.lowercase() })
                    }
                }

                lateinit var listener: NsdManager.DiscoveryListener
                listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (!serviceInfo.serviceType.equals(CINEVAULT_SERVICE_TYPE, ignoreCase = true)) return
                        resolve(serviceInfo) { endpoint ->
                            if (endpoint != null) resolved[endpoint.deviceId] = endpoint
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        resolved.entries.removeAll { it.value.deviceName == serviceInfo.serviceName }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        if (!stopped && continuation.isActive) {
                            stopped = true
                            continuation.resume(resolved.values.sortedBy { it.deviceName.lowercase() })
                        }
                    }

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        finish(this)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (!stopped && continuation.isActive) {
                            stopped = true
                            continuation.resume(resolved.values.sortedBy { it.deviceName.lowercase() })
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { nsd.stopServiceDiscovery(listener) }
                }

                nsd.discoverServices(
                    CINEVAULT_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener,
                )

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { finish(listener) },
                    discoveryWindowMs,
                )
            }
        } ?: emptyList()

    @Suppress("DEPRECATION")
    private fun resolve(
        serviceInfo: NsdServiceInfo,
        callback: (CineVaultLanEndpoint?) -> Unit,
    ) {
        nsd.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    callback(null)
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = if (Build.VERSION.SDK_INT >= 34) {
                        resolved.hostAddresses.firstOrNull()?.hostAddress
                    } else {
                        resolved.host?.hostAddress
                    } ?: return callback(null)

                    val attrs = resolved.attributes
                    val deviceId = attrs["id"]?.toString(Charsets.UTF_8)
                        ?.takeIf { isSafeLanDeviceId(it) }
                        ?: stableLanDeviceId(resolved.serviceName, host, resolved.port)

                    val deviceName = attrs["name"]?.toString(Charsets.UTF_8)
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.take(80)
                        ?: resolved.serviceName.take(80)

                    callback(
                        CineVaultLanEndpoint(
                            deviceId = deviceId,
                            deviceName = deviceName,
                            host = host,
                            port = resolved.port,
                        )
                    )
                }
            },
        )
    }
}

internal fun isSafeLanDeviceId(value: String): Boolean =
    value.length in 8..96 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

internal fun stableLanDeviceId(name: String, host: String, port: Int): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest("$name|$host|$port".toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }

internal fun formatHostForUrl(host: String): String =
    if (':' in host && !host.startsWith("[")) "[$host]" else host
