package com.sole.cinevault.network

data class SftpEndpoint(
    val host: String,
    val port: Int = 22,
    val rootPath: String = "/",
    val hostKeySha256: String,
) {
    init {
        require(host.isNotBlank())
        require(port in 1..65535)
        require(rootPath.startsWith("/"))
        require(hostKeySha256.startsWith("SHA256:") && hostKeySha256.length > 15)
    }

    val redactedAddress: String get() = "sftp://$host:$port$rootPath"
}

internal fun isNetworkVideoName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf(
        "mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv", "flv"
    )

internal fun normalizeSftpPath(path: String): String {
    val clean = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
    require(clean.none { it == ".." }) { "Parent traversal is not allowed" }
    return "/" + clean.joinToString("/")
}
