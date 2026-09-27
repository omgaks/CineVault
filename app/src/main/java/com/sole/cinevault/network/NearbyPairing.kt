package com.sole.cinevault.network

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

enum class NearbyPairingState { IDLE, ADVERTISING, APPROVAL_REQUIRED, PAIRED, STOPPED }

data class NearbyPairingInvite(
    val deviceId: String,
    val deviceName: String,
    val nonce: String,
    val expiresAtEpochMs: Long,
)

data class NearbyPairingRequest(
    val remoteDeviceId: String,
    val remoteDeviceName: String,
    val inviteNonce: String,
)

data class NearbyPairingSession(
    val remoteDeviceId: String,
    val sessionToken: String,
    val expiresAtEpochMs: Long,
)

/**
 * Pure privacy policy for Nearby pairing. Transport/discovery is separate.
 * Nothing becomes trusted merely because it was discovered on the LAN.
 */
class NearbyPairingPolicy(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = { size ->
        ByteArray(size).also(SecureRandom()::nextBytes)
    },
) {
    fun createInvite(deviceId: String, deviceName: String, ttlMs: Long = 120_000L): NearbyPairingInvite {
        require(deviceId.isNotBlank())
        require(deviceName.isNotBlank())
        require(ttlMs in 30_000L..600_000L)
        return NearbyPairingInvite(
            deviceId = deviceId,
            deviceName = deviceName.trim().take(80),
            nonce = randomBytes(24).toHex(),
            expiresAtEpochMs = nowMs() + ttlMs,
        )
    }

    fun approve(
        invite: NearbyPairingInvite,
        request: NearbyPairingRequest,
        userApproved: Boolean,
        sessionTtlMs: Long = 8 * 60 * 60 * 1000L,
    ): NearbyPairingSession? {
        if (!userApproved) return null
        if (invite.expiresAtEpochMs <= nowMs()) return null
        if (request.inviteNonce != invite.nonce) return null
        if (request.remoteDeviceId.isBlank()) return null
        val material = randomBytes(32) + request.remoteDeviceId.toByteArray(StandardCharsets.UTF_8)
        return NearbyPairingSession(
            remoteDeviceId = request.remoteDeviceId,
            sessionToken = sha256(material).toHex(),
            expiresAtEpochMs = nowMs() + sessionTtlMs,
        )
    }
}

internal fun pairingCode(token: String): String {
    val digits = sha256(token.toByteArray(StandardCharsets.UTF_8))
        .take(4)
        .fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xff) }
    return (digits % 1_000_000L).toString().padStart(6, '0')
}

private fun sha256(value: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(value)

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
