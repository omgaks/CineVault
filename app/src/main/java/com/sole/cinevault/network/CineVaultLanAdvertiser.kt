package com.sole.cinevault.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Advertises only while the user has explicitly enabled Share My Library.
 * Stopping sharing unregisters the mDNS record immediately.
 */
class CineVaultLanAdvertiser(context: Context) {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null
    private val active = AtomicBoolean(false)

    fun start(
        deviceId: String,
        deviceName: String,
        port: Int,
        onRegistered: (NsdServiceInfo) -> Unit = {},
        onFailure: (Int) -> Unit = {},
    ) {
        require(isSafeLanDeviceId(deviceId))
        require(deviceName.isNotBlank())
        require(port in 1..65535)
        stop()

        val service = NsdServiceInfo().apply {
            serviceType = "_cinevault._tcp."
            serviceName = sanitizeServiceName(deviceName)
            this.port = port
            setAttribute("id", deviceId)
            setAttribute("name", deviceName.trim().take(80))
            setAttribute("v", "1")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                active.set(true)
                onRegistered(serviceInfo)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                active.set(false)
                onFailure(errorCode)
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                active.set(false)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                active.set(false)
            }
        }
        registration = listener
        nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        val listener = registration ?: return
        registration = null
        active.set(false)
        runCatching { nsd.unregisterService(listener) }
    }

    fun isAdvertising(): Boolean = active.get()
}

internal fun sanitizeServiceName(value: String): String =
    value.trim()
        .replace(Regex("""[\r\n\t]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .take(63)
        .ifBlank { "CineVault" }
