package com.sole.cinevault.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager

/**
 * Pure timing rule for the CPU keep-awake, kept separate so it can be unit tested.
 *
 * The host is asked for media in many short range requests. Between requests the
 * receiver is simply playing from its buffer, so the host may idle. A request (or
 * bytes still being streamed) refreshes a short "tail" of wake time; once nothing
 * has happened for [tailMs] the wake lock expires by itself and the phone can sleep.
 */
internal class SharingKeepAlivePolicy(
    val tailMs: Long = DEFAULT_TAIL_MS,
    val refreshEveryMs: Long = DEFAULT_REFRESH_MS,
) {
    private var lastRefreshAt = NEVER

    /** True when the wake lock should be (re)acquired for another [tailMs] now. */
    @Synchronized
    fun shouldRefresh(nowMs: Long): Boolean {
        if (lastRefreshAt != NEVER && nowMs - lastRefreshAt < refreshEveryMs) return false
        lastRefreshAt = nowMs
        return true
    }

    @Synchronized
    fun reset() {
        lastRefreshAt = NEVER
    }

    companion object {
        const val DEFAULT_TAIL_MS = 2 * 60 * 1000L
        const val DEFAULT_REFRESH_MS = 10 * 1000L
        private const val NEVER = Long.MIN_VALUE
    }
}

/**
 * Keeps a CineVault Nearby host reachable while its screen is off.
 *
 * - Wi-Fi lock: held for the whole time sharing is on, so the radio is not parked in
 *   deep power-save. Best effort: newer Android versions limit what this can do.
 * - Partial wake lock: NOT held continuously. It is refreshed only while the receiver
 *   is actually requesting or streaming, and expires on its own shortly after, so an
 *   idle host does not drain its battery.
 *
 * Every call is wrapped so a lock failure can never break sharing itself.
 */
class CineVaultSharingLocks(context: Context) {
    private val app = context.applicationContext
    private val policy = SharingKeepAlivePolicy()

    private val wifiLock: WifiManager.WifiLock? by lazy {
        runCatching {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifi?.createWifiLock(mode, WIFI_TAG)?.apply { setReferenceCounted(false) }
        }.getOrNull()
    }

    private val wakeLock: PowerManager.WakeLock? by lazy {
        runCatching {
            val power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG)
                ?.apply { setReferenceCounted(false) }
        }.getOrNull()
    }

    /** Sharing turned on: hold the Wi-Fi lock and cover the pairing/first-play window. */
    fun onSharingStarted() {
        runCatching { wifiLock?.takeIf { !it.isHeld }?.acquire() }
        onActivity()
    }

    /** A request arrived or bytes are still flowing: keep the CPU awake a little longer. */
    fun onActivity() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!policy.shouldRefresh(now)) return
        runCatching { wakeLock?.acquire(policy.tailMs) }
    }

    /** Sharing turned off (or the service was destroyed): let everything go. */
    fun releaseAll() {
        policy.reset()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    private companion object {
        const val WIFI_TAG = "CineVault:NearbyWifi"
        const val WAKE_TAG = "CineVault:NearbyStreaming"
    }
}
