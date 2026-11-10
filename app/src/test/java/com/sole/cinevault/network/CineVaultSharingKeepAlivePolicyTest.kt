package com.sole.cinevault.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CineVaultSharingKeepAlivePolicyTest {
    @Test
    fun firstActivityAlwaysRefreshes() {
        val policy = SharingKeepAlivePolicy(tailMs = 120_000, refreshEveryMs = 10_000)
        assertTrue(policy.shouldRefresh(0))
    }

    @Test
    fun burstOfActivityIsThrottledToOneRefreshPerWindow() {
        val policy = SharingKeepAlivePolicy(tailMs = 120_000, refreshEveryMs = 10_000)
        assertTrue(policy.shouldRefresh(1_000))
        assertFalse(policy.shouldRefresh(1_001))
        assertFalse(policy.shouldRefresh(10_999))
        assertTrue(policy.shouldRefresh(11_000))
    }

    @Test
    fun refreshWindowIsShorterThanTailSoLongStreamsNeverLapse() {
        val policy = SharingKeepAlivePolicy()
        assertTrue(policy.refreshEveryMs < policy.tailMs)
    }

    @Test
    fun resetAllowsImmediateRefreshAfterSharingRestarts() {
        val policy = SharingKeepAlivePolicy(tailMs = 120_000, refreshEveryMs = 10_000)
        assertTrue(policy.shouldRefresh(5_000))
        policy.reset()
        assertTrue(policy.shouldRefresh(5_001))
    }

    @Test
    fun defaultTailIsTwoMinutes() {
        assertEquals(120_000L, SharingKeepAlivePolicy.DEFAULT_TAIL_MS)
    }
}
