package com.sole.cinevault.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SsdpDiscovery(
    private val timeoutMs: Int = 2_500,
) {
    suspend fun discoverMediaServers(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val request = buildSsdpSearch().toByteArray(Charsets.US_ASCII)
        val found = linkedMapOf<String, DlnaDevice>()

        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.send(
                DatagramPacket(
                    request,
                    request.size,
                    InetAddress.getByName("239.255.255.250"),
                    1900,
                )
            )

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val buffer = ByteArray(16 * 1024)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val response = String(packet.data, packet.offset, packet.length, Charsets.ISO_8859_1)
                    parseSsdpResponse(response)?.let { device ->
                        found[device.usn] = device
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }
        }
        found.values.toList()
    }
}

internal fun buildSsdpSearch(): String =
    "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"

internal fun parseSsdpResponse(response: String): DlnaDevice? {
    val lines = response.split("\r\n", "\n")
    if (lines.firstOrNull()?.contains("200 OK", ignoreCase = true) != true) return null
    val headers = lines.drop(1).mapNotNull { line ->
        val colon = line.indexOf(':')
        if (colon <= 0) null
        else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
    }.toMap()

    val location = headers["location"]?.takeIf(::isSafeHttpUrl) ?: return null
    val usn = headers["usn"]?.takeIf(String::isNotBlank) ?: return null
    return DlnaDevice(usn = usn, location = location, server = headers["server"])
}
