package com.sole.cinevault.network

import org.junit.Assert.*
import org.junit.Test

class NearbyPairingPolicyTest {
    private var now = 1_000_000L
    private val policy = NearbyPairingPolicy(
        nowMs = { now },
        randomBytes = { size -> ByteArray(size) { (it + 1).toByte() } },
    )

    @Test fun discoveryDoesNotEqualPairing() {
        val invite = policy.createInvite("host-a", "Ash's CineVault")
        val request = NearbyPairingRequest("phone-b", "Phone B", invite.nonce)
        assertNull(policy.approve(invite, request, userApproved = false))
    }

    @Test fun explicitApprovalCreatesTemporarySession() {
        val invite = policy.createInvite("host-a", "Ash's CineVault")
        val request = NearbyPairingRequest("phone-b", "Phone B", invite.nonce)
        val session = policy.approve(invite, request, userApproved = true)
        assertNotNull(session)
        assertEquals("phone-b", session!!.remoteDeviceId)
        assertTrue(session.sessionToken.length >= 32)
        assertFalse(session.sessionToken.contains("phone-b"))
    }

    @Test fun expiredInviteCannotPair() {
        val invite = policy.createInvite("host-a", "Host", ttlMs = 30_000L)
        now += 30_001L
        assertNull(
            policy.approve(
                invite,
                NearbyPairingRequest("phone-b", "Phone B", invite.nonce),
                userApproved = true,
            )
        )
    }

    @Test fun wrongNonceCannotPair() {
        val invite = policy.createInvite("host-a", "Host")
        assertNull(
            policy.approve(
                invite,
                NearbyPairingRequest("phone-b", "Phone B", "wrong"),
                userApproved = true,
            )
        )
    }

    @Test fun pairingCodeIsStableSixDigits() {
        val code = pairingCode("opaque-session-token")
        assertEquals(6, code.length)
        assertTrue(code.all(Char::isDigit))
    }
}
