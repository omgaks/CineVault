package com.sole.cinevault.network

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small LAN-only HTTP/1.1 server for CineVault Direct.
 *
 * It binds to an ephemeral local port, accepts only authenticated library/media
 * requests, supports byte ranges for seeking, and never exposes filesystem paths.
 * Pairing transport remains separate from media authorization.
 */
class CineVaultDirectServer(
    private val authorization: CineVaultDirectAuthorization,
    private val mediaProvider: () -> List<CineVaultDirectMedia>,
    private val selectionProvider: () -> ShareLibrarySelection,
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private var socket: ServerSocket? = null

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
        runCatching { socket?.close() }
        socket = null
    }

    fun isRunning(): Boolean = running.get()

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = input.readLine() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) return respond(client, 400, "Bad Request")
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?')
        if (method != "GET" && method != "HEAD") return respond(client, 405, "Method Not Allowed")

        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = input.readLine() ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }

        if (path == CineVaultLanProtocol.HEALTH_PATH) {
            return respond(client, 200, """{"ok":true,"version":${CineVaultLanProtocol.VERSION}}""", "application/json", method == "HEAD")
        }

        if (authorization.authorize(headers["authorization"]) == null) {
            return respond(client, 401, "Unauthorized")
        }

        when {
            path == CineVaultLanProtocol.LIBRARY_PATH ->
                respond(client, 200, catalogueJson(), "application/json", method == "HEAD")

            path.startsWith("/v1/media/") -> serveMediaRoute(client, path, headers["range"], method == "HEAD")
            else -> respond(client, 404, "Not Found")
        }
    }

    private fun allowedMedia(): List<CineVaultDirectMedia> {
        val media = mediaProvider()
        val allowed = filterShareableLibrary(
            media.map {
                ShareableLibraryItem(it.id, it.file.absolutePath, it.folderId, it.isVaultOrSecret)
            },
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
                "Content-Range" to "bytes */$length",
                "Content-Length" to "0",
                "Connection" to "close",
            ))
            return
        }

        val range = requestedRange ?: HttpByteRange(0, maxOf(0, length - 1))
        val status = if (requestedRange != null) 206 else 200
        val reason = if (status == 206) "Partial Content" else "OK"
        val out = client.getOutputStream()
        val extra = linkedMapOf(
            "Content-Type" to mime,
            "Accept-Ranges" to "bytes",
            "Content-Length" to if (length == 0L) "0" else range.length.toString(),
            "Connection" to "close",
        )
        if (status == 206) extra["Content-Range"] = contentRangeHeader(range, length)
        writeHeaders(out, status, reason, extra)
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

    private fun catalogueJson(): String {
        val catalogue = buildDirectCatalogue(allowedMedia(), ShareLibrarySelection(SharedLibraryScope.ENTIRE_LIBRARY))
        return buildString {
            append("""{"protocolVersion":${catalogue.protocolVersion},"items":[""")
            catalogue.items.forEachIndexed { index, item ->
                if (index > 0) append(',')
                append("""{"id":"${json(item.id)}","title":"${json(item.title)}","sizeBytes":${item.sizeBytes},"mimeType":"${json(item.mimeType)}","streamPath":"${json(item.streamPath)}","subtitlePaths":[""")
                item.subtitlePaths.forEachIndexed { subIndex, sub ->
                    if (subIndex > 0) append(',')
                    append("\"${json(sub)}\"")
                }
                append("]")
                item.artworkPath?.let { append(""","artworkPath":"${json(it)}"""") }
                append("}")
            }
            append("]}")
        }
    }

    private fun respond(client: Socket, code: Int, body: String, type: String = "text/plain; charset=utf-8", headOnly: Boolean = false) {
        val bytes = body.toByteArray()
        val out = client.getOutputStream()
        writeHeaders(out, code, reason(code), mapOf(
            "Content-Type" to type,
            "Content-Length" to bytes.size.toString(),
            "Connection" to "close",
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

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 206 -> "Partial Content"; 400 -> "Bad Request"; 401 -> "Unauthorized"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
    }

    private fun subtitleMime(file: File): String = when (file.extension.lowercase()) {
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }

    private fun json(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
