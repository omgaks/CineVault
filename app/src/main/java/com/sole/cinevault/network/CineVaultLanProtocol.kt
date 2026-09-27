package com.sole.cinevault.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest

object CineVaultLanProtocol {
    const val VERSION = 1
    const val HEALTH_PATH = "/v1/health"
    const val PAIR_REQUEST_PATH = "/v1/pair/request"
    const val PAIR_APPROVE_PATH = "/v1/pair/approve"
    const val LIBRARY_PATH = "/v1/library"

    fun pairRequestUrl(baseUrl: String): String =
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(PAIR_REQUEST_PATH.removePrefix("/"))
            .build()
            .toString()

    fun healthUrl(baseUrl: String): String =
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(HEALTH_PATH.removePrefix("/"))
            .build()
            .toString()

    fun bearerHeader(session: NearbyPairingSession): String =
        "Bearer ${session.sessionToken}"

    fun fingerprint(endpoint: CineVaultLanEndpoint): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${endpoint.deviceId}|${endpoint.host}|${endpoint.port}".toByteArray())
            .take(8)
            .joinToString(":") { "%02X".format(it) }
}
