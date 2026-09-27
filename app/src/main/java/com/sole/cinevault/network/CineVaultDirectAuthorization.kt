package com.sole.cinevault.network

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime-only session registry. Nothing is silently trusted after restart.
 * Stop Sharing calls revokeAll() and immediately invalidates every bearer token.
 */
class CineVaultDirectAuthorization(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val sessions = ConcurrentHashMap<String, NearbyPairingSession>()

    fun grant(session: NearbyPairingSession) {
        if (session.expiresAtEpochMs > nowMs()) sessions[tokenKey(session.sessionToken)] = session
    }

    fun authorize(authorizationHeader: String?): NearbyPairingSession? {
        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            ?.takeIf { it.length >= 32 }
            ?: return null

        val key = tokenKey(token)
        val session = sessions[key] ?: return null
        if (session.expiresAtEpochMs <= nowMs()) {
            sessions.remove(key)
            return null
        }
        return session
    }

    fun revoke(sessionToken: String) {
        sessions.remove(tokenKey(sessionToken))
    }

    fun revokeAll() = sessions.clear()

    fun activeCount(): Int {
        sessions.entries.removeIf { it.value.expiresAtEpochMs <= nowMs() }
        return sessions.size
    }

    private fun tokenKey(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
