package com.sole.cinevault.network

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Produces a deterministic, non-secret identifier for a saved network source.
 * Credentials/tokens are intentionally not accepted by this API.
 */
fun stableNetworkSourceId(
    type: NetworkType,
    host: String,
    resource: String = "",
): String {
    val normalizedHost = host.trim().lowercase()
    val normalizedResource = resource.trim().trim('/').lowercase()
    val canonical = "${type.name}|$normalizedHost|$normalizedResource"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(20)
    return "${type.name.lowercase()}-$digest"
}

/**
 * User-visible/log-safe address. Strips URI user-info such as
 * user:password@host before anything is surfaced.
 */
fun redactNetworkAddress(value: String): String =
    value.replace(Regex("""(?i)([a-z][a-z0-9+.-]*://)[^/@\s]+@"""), "$1")
