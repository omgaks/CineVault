package com.sole.cinevault.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest

object CineVaultLanProtocol {
    const val VERSION = 1
    const val HEALTH_PATH = "/v1/health"
    const val PAIR_REQUEST_PATH = "/v1/pair/request"
    const val PAIR_APPROVE_PATH = "/v1/pair/approve"
    const val LIBRARY_PATH = "/v1/library"

    fun pairRequestUrl(baseUrl: String): String =
        route(baseUrl, PAIR_REQUEST_PATH).toString()

    fun pairApproveUrl(baseUrl: String): HttpUrl =
        route(baseUrl, PAIR_APPROVE_PATH)

    fun healthUrl(baseUrl: String): String =
        route(baseUrl, HEALTH_PATH).toString()

    fun libraryUrl(baseUrl: String): String =
        route(baseUrl, LIBRARY_PATH).toString()

    fun resolve(baseUrl: String, path: String): String =
        baseUrl.toHttpUrl().resolve(path)?.toString()
            ?: error("Invalid CineVault Direct route")

    fun bearerHeader(session: NearbyPairingSession): String =
        "Bearer ${session.sessionToken}"

    fun fingerprint(endpoint: CineVaultLanEndpoint): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${endpoint.deviceId}|${endpoint.host}|${endpoint.port}".toByteArray())
            .take(8)
            .joinToString(":") { "%02X".format(it) }

    private fun route(baseUrl: String, path: String): HttpUrl =
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path.removePrefix("/"))
            .build()
}
