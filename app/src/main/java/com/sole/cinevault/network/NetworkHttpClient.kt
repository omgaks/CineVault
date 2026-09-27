package com.sole.cinevault.network

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal fun createNetworkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

internal fun Request.Builder.applyBasicCredential(
    credential: NetworkCredential?,
): Request.Builder {
    if (credential == null || credential.username.isBlank()) return this
    return header(
        "Authorization",
        Credentials.basic(credential.username, credential.secret),
    )
}

internal fun safeNetworkFailure(displayName: String): NetworkConnectionResult.Failed =
    NetworkConnectionResult.Failed(
        "Couldn't connect to $displayName. Check the network and sign-in details."
    )
