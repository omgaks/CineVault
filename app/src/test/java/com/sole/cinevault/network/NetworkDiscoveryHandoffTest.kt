package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class NetworkDiscoveryHandoffTest {

    @Test
    fun dlnaDiscoveryKeepsTheRealEndpointAndBecomesBrowsableSource() {
        val device = DiscoveredNetworkDevice(
            id = "dlna-1",
            displayName = "Living Room TV",
            kind = NetworkDiscoveryKind.DLNA,
            addressHint = "http://192.168.1.50:1400/device.xml",
        )

        val action = discoveredDeviceAction(device)

        assertTrue(action is DiscoveredDeviceAction.OpenDlna)
        val source = (action as DiscoveredDeviceAction.OpenDlna).source
        assertEquals(NetworkType.DLNA, source.type)
        assertEquals("Living Room TV", source.displayName)
    }

    @Test
    fun cineVaultDiscoveryCarriesEndpointInsteadOfOnlyId() {
        val device = DiscoveredNetworkDevice(
            id = "peer_12345678",
            displayName = "Bedroom CineVault",
            kind = NetworkDiscoveryKind.CINEVAULT,
            addressHint = "http://192.168.1.22:48211/",
        )

        val action = discoveredDeviceAction(device)

        assertTrue(action is DiscoveredDeviceAction.CineVaultPeer)
        val peer = (action as DiscoveredDeviceAction.CineVaultPeer).device
        assertEquals("http://192.168.1.22:48211", peer.addressHint)
        assertEquals(device.id, peer.id)
    }

    @Test
    fun credentialBearingDiscoveryAddressIsRejected() {
        val device = DiscoveredNetworkDevice(
            id = "dlna-2",
            displayName = "Unsafe",
            kind = NetworkDiscoveryKind.DLNA,
            addressHint = "http://user:password@192.168.1.4/device.xml",
        )

        assertTrue(discoveredDeviceAction(device) is DiscoveredDeviceAction.Unsupported)
    }

    @Test
    fun cineVaultQrPayloadRoundTripParsesRequiredConnectionFields() {
        val endpoint = "http://192.168.1.20:49152"
        val raw = buildString {
            append("cinevault://nearby?")
            append("id=peer_12345678")
            append("&name=")
            append(URLEncoder.encode("Ash's Tablet", Charsets.UTF_8.name()))
            append("&endpoint=")
            append(URLEncoder.encode(endpoint, Charsets.UTF_8.name()))
            append("&nonce=0123456789abcdef0123456789abcdef")
            append("&expires=1999999999999")
        }

        val payload = parseCineVaultQrPayload(raw)

        assertNotNull(payload)
        assertEquals("peer_12345678", payload!!.deviceId)
        assertEquals("Ash's Tablet", payload.deviceName)
        assertEquals(endpoint, payload.endpoint)
        assertEquals("0123456789abcdef0123456789abcdef", payload.nonce)
    }

    @Test
    fun foreignQrAndIncompleteInviteAreRejected() {
        assertNull(parseCineVaultQrPayload("https://example.com"))
        assertNull(parseCineVaultQrPayload("cinevault://nearby?id=peer_12345678"))
    }

    @Test
    fun qrPayloadBecomesEndpointAwareCineVaultDiscovery() {
        val payload = CineVaultQrPayload(
            deviceId = "peer_12345678",
            deviceName = "CineVault Pad",
            endpoint = "http://10.0.0.8:50000",
            nonce = "abcdef0123456789abcdef0123456789",
            expiresAtEpochMs = 1_999_999_999_999,
        )

        val device = payload.asDiscoveredDevice()

        assertEquals(NetworkDiscoveryKind.CINEVAULT, device.kind)
        assertEquals(payload.endpoint, device.addressHint)
        assertEquals(payload.deviceId, device.id)
    }
}
