package com.sole.cinevault.network

import com.google.gson.Gson
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LAN-only CineVault Direct server.
 *
 * Discovery is never authorization. Pairing requests are held pending until
 * the owner explicitly approves them. Only then is a temporary bearer session
 * granted for catalogue/media routes.
 */
class CineVaultDirectServer(
    private val authorization: CineVaultDirectAuthorization,
    private val mediaProvider: () -> List<CineVaultDirectMedia>,
    private val selectionProvider: () -> ShareLibrarySelection,
    private val gson: Gson = Gson(),
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private var socket: ServerSocket? = null
    private val pending = ConcurrentHashMap<String, NearbyPairingRequest>()
    private val approvals = ConcurrentHashMap<String, NearbyPairingSession>()

    @Synchronized
    fun start(port: Int = 0): Int {
        if (running.get()) return socket?.localPort ?: error("Server state invalid")
        val server = ServerSocket(port, 16, InetAddress.getByName("0.0.0.0"))
        socket = server
        running.set(true)
        pool.execute {
            while (running.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { client.use(::handle) }
            }
        }
        return server.localPort
    }

    @Synchronized
    fun stop() {
        running.set(false)
        authorization.revokeAll()
        pending.clear()
        approvals.clear()
        runCatching { socket?.close() }
        socket = null
    }

    fun isRunning(): Boolean = running.get()

    fun pendingRequests(): List<NearbyPairingRequest> = pending.values.toList()

    fun approve(
        invite: NearbyPairingInvite,
        request: NearbyPairingRequest,
        policy: NearbyPairingPolicy,
    ): NearbyPairingSession? {
        val key = requestKey(request.remoteDeviceId, request.inviteNonce)
        if (pending[key] == null) return null
        val session = policy.approve(invite, request, userApproved = true) ?: return null
        authorization.grant(session)
        approvals[key] = session
        pending.remove(key)
        return session
    }

    /**
     * Discovery pairing still requires an explicit owner tap, but does not
     * require the guest to know the host's QR nonce. The request's random
     * nonce is the correlation key; approval creates the same short-lived
     * bearer session used by QR pairing.
     */
    fun approveDiscovered(request: NearbyPairingRequest, ttlMs: Long = 60 * 60 * 1000L): NearbyPairingSession? {
        val key = requestKey(request.remoteDeviceId, request.inviteNonce)
        if (pending[key] == null) return null
        val tokenBytes = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val token = tokenBytes.joinToString("") { "%02x".format(it) }
        val session = NearbyPairingSession(
            remoteDeviceId = request.remoteDeviceId,
            sessionToken = token,
            expiresAtEpochMs = System.currentTimeMillis() + ttlMs,
        )
        authorization.grant(session)
        approvals[key] = session
        pending.remove(key)
        return session
    }

    fun deny(request: NearbyPairingRequest) {
        pending.remove(requestKey(request.remoteDeviceId, request.inviteNonce))
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(client, 400, "Bad Request")
        val method = parts[0].uppercase()
        val rawTarget = parts[1]
        val path = rawTarget.substringBefore('?')

        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }

        if (path == CineVaultLanProtocol.HEALTH_PATH && (method == "GET" || method == "HEAD")) {
            return respond(client, 200, """{"ok":true,"version":${CineVaultLanProtocol.VERSION}}""", "application/json", method == "HEAD")
        }

        if (path == CineVaultLanProtocol.PAIR_REQUEST_PATH && method == "POST") {
            val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
            val chars = CharArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(chars, offset, length - offset)
                if (count <= 0) break
                offset += count
            }
            val envelope = runCatching {
                gson.fromJson(String(chars, 0, offset), CineVaultPairRequestEnvelope::class.java)
            }.getOrNull() ?: return respond(client, 400, """{"state":"error","message":"Invalid pairing request."}""", "application/json")

            val request = NearbyPairingRequest(
                remoteDeviceId = envelope.remoteDeviceId.trim().take(128),
                remoteDeviceName = envelope.remoteDeviceName.trim().take(80),
                inviteNonce = envelope.inviteNonce.trim(),
            )
            if (!isSafeLanDeviceId(request.remoteDeviceId) ||
                request.remoteDeviceName.isBlank() ||
                request.inviteNonce.length !in 16..128
            ) {
                return respond(client, 400, """{"state":"error","message":"Invalid pairing request."}""", "application/json")
            }
            val key = requestKey(request.remoteDeviceId, request.inviteNonce)
            pending[key] = request

            // Keep the original pairing socket alive while the owner decides. On some
            // Android/Wi-Fi combinations the first client -> host connection succeeds,
            // but immediate follow-up polling connections are temporarily filtered or
            // delayed. Returning the approval on this already-established socket makes
            // the physical-device handshake reliable; polling remains as a fallback.
            val deadline = System.currentTimeMillis() + 90_000L
            while (running.get() && System.currentTimeMillis() < deadline) {
                approvals[key]?.takeIf { it.expiresAtEpochMs > System.currentTimeMillis() }?.let { approved ->
                    return respond(
                        client,
                        200,
                        approvedResponseJson(approved),
                        "application/json",
                    )
                }
                if (pending[key] == null && approvals[key] == null) {
                    return respond(client, 200, """{"state":"denied"}""", "application/json")
                }
                try {
                    Thread.sleep(200L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return respond(client, 202, """{"state":"pending"}""", "application/json")
        }

        if (path == CineVaultLanProtocol.PAIR_APPROVE_PATH && method == "GET") {
            val query = parseQuery(rawTarget.substringAfter('?', ""))
            val deviceId = query["deviceId"].orEmpty()
            val nonce = query["nonce"].orEmpty()
            val key = requestKey(deviceId, nonce)
            val approved = approvals[key]
            if (approved != null && approved.expiresAtEpochMs > System.currentTimeMillis()) {
                return respond(
                    client, 200,
                    approvedResponseJson(approved),
                    "application/json",
                )
            }
            if (approved != null) approvals.remove(key)
            return respond(client, 200, """{"state":"pending"}""", "application/json")
        }

        if (method != "GET" && method != "HEAD") return respond(client, 405, "Method Not Allowed")

        if (authorization.authorize(headers["authorization"]) == null) {
            return respond(client, 401, "Unauthorized")
        }

        when {
            path == CineVaultLanProtocol.LIBRARY_PATH ->
                respond(client, 200, catalogueJson(), "application/json", method == "HEAD")
            path.startsWith("/v1/media/") ->
                serveMediaRoute(client, path, headers["range"], method == "HEAD")
            else -> respond(client, 404, "Not Found")
        }
    }

    /**
     * Stable pairing wire contract. Keep these field names explicit so the
     * receiving client always gets the complete approval envelope.
     */
    private fun approvedResponseJson(session: NearbyPairingSession): String =
        """{"state":"approved","sessionToken":"${session.sessionToken}","expiresAtEpochMs":${session.expiresAtEpochMs}}"""

    private fun allowedMedia(): List<CineVaultDirectMedia> {
        val media = mediaProvider()
        val allowed = filterShareableLibrary(
            media.map { ShareableLibraryItem(it.id, it.file.absolutePath, it.folderId, it.isVaultOrSecret) },
            selectionProvider(),
        ).mapTo(hashSetOf()) { it.id }
        return media.filter { it.id in allowed }
    }

    private fun serveMediaRoute(client: Socket, path: String, rangeHeader: String?, headOnly: Boolean) {
        val rest = path.removePrefix("/v1/media/")
        val segments = rest.split('/')
        val id = java.net.URLDecoder.decode(segments.firstOrNull().orEmpty(), Charsets.UTF_8.name())
        val media = allowedMedia().firstOrNull { it.id == id } ?: return respond(client, 404, "Not Found")
        when {
            segments.size == 1 -> serveFile(client, media.file, media.mimeType, rangeHeader, headOnly)
            segments.size == 2 && segments[1] == "artwork" -> {
                val artwork = media.posterFile?.takeIf(File::isFile) ?: return respond(client, 404, "Not Found")
                serveFile(client, artwork, "image/*", rangeHeader, headOnly)
            }
            segments.size == 3 && segments[1] == "subtitle" -> {
                val index = segments[2].toIntOrNull() ?: return respond(client, 404, "Not Found")
                val subtitle = media.subtitleFiles.getOrNull(index)?.takeIf(File::isFile)
                    ?: return respond(client, 404, "Not Found")
                serveFile(client, subtitle, subtitleMime(subtitle), rangeHeader, headOnly)
            }
            else -> respond(client, 404, "Not Found")
        }
    }

    private fun serveFile(client: Socket, file: File, mime: String, rangeHeader: String?, headOnly: Boolean) {
        if (!file.isFile) return respond(client, 404, "Not Found")
        val length = file.length()
        val requestedRange = parseHttpRange(rangeHeader, length)
        if (!rangeHeader.isNullOrBlank() && requestedRange == null) {
            val out = client.getOutputStream()
            writeHeaders(out, 416, "Range Not Satisfiable", mapOf(
                "Content-Range" to "bytes */$length", "Content-Length" to "0", "Connection" to "close",
            ))
            return
        }
        val range = requestedRange ?: HttpByteRange(0, maxOf(0, length - 1))
        val status = if (requestedRange != null) 206 else 200
        val out = client.getOutputStream()
        val extra = linkedMapOf(
            "Content-Type" to mime, "Accept-Ranges" to "bytes",
            "Content-Length" to if (length == 0L) "0" else range.length.toString(),
            "Connection" to "close",
        )
        if (status == 206) extra["Content-Range"] = contentRangeHeader(range, length)
        writeHeaders(out, status, if (status == 206) "Partial Content" else "OK", extra)
        if (headOnly || length == 0L) return
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(range.start)
            var remaining = range.length
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        out.flush()
    }

    private fun catalogueJson(): String = gson.toJson(
        buildDirectCatalogue(allowedMedia(), ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY)),
    )

    private fun respond(client: Socket, code: Int, body: String, type: String = "text/plain; charset=utf-8", headOnly: Boolean = false) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        writeHeaders(out, code, reason(code), mapOf(
            "Content-Type" to type, "Content-Length" to bytes.size.toString(), "Connection" to "close",
        ))
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun writeHeaders(out: java.io.OutputStream, code: Int, reason: String, headers: Map<String, String>) {
        val text = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            headers.forEach { (key, value) -> append("$key: $value\r\n") }
            append("\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else
                java.net.URLDecoder.decode(it.substring(0, i), Charsets.UTF_8.name()) to
                    java.net.URLDecoder.decode(it.substring(i + 1), Charsets.UTF_8.name())
        }.toMap()

    private fun requestKey(deviceId: String, nonce: String) = "$deviceId|$nonce"

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 202 -> "Accepted"; 206 -> "Partial Content"; 400 -> "Bad Request"
        401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        else -> "Error"
    }

    private fun subtitleMime(file: File): String = when (file.extension.lowercase()) {
        "vtt" -> "text/vtt"; "ass", "ssa" -> "text/x-ssa"; else -> "application/x-subrip"
    }
}
