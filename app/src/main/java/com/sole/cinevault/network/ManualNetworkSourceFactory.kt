package com.sole.cinevault.network

sealed interface ManualNetworkSourceSpec {
    val displayName: String

    data class WebDav(
        override val displayName: String,
        val url: String,
        val credentialProvider: () -> NetworkCredential? = { null },
    ) : ManualNetworkSourceSpec

    data class HttpDirectory(
        override val displayName: String,
        val url: String,
        val credentialProvider: () -> NetworkCredential? = { null },
    ) : ManualNetworkSourceSpec

    data class M3u(
        override val displayName: String,
        val url: String,
        val credentialProvider: () -> NetworkCredential? = { null },
    ) : ManualNetworkSourceSpec

    data class Sftp(
        override val displayName: String,
        val endpoint: SftpEndpoint,
        val credentialProvider: () -> NetworkCredential?,
    ) : ManualNetworkSourceSpec
}

fun createManualNetworkSource(spec: ManualNetworkSourceSpec): NetworkSource =
    when (spec) {
        is ManualNetworkSourceSpec.WebDav ->
            WebDavNetworkSource(spec.url, spec.displayName, spec.credentialProvider)
        is ManualNetworkSourceSpec.HttpDirectory ->
            HttpNetworkSource(spec.url, spec.displayName, false, spec.credentialProvider)
        is ManualNetworkSourceSpec.M3u ->
            HttpNetworkSource(spec.url, spec.displayName, true, spec.credentialProvider)
        is ManualNetworkSourceSpec.Sftp ->
            SftpNetworkSource(spec.endpoint, spec.displayName, spec.credentialProvider)
    }
