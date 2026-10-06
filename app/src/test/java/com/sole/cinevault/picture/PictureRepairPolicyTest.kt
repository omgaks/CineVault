package com.sole.cinevault.picture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureRepairPolicyTest {
    @Test fun `zero strength disables repair`() {
        assertFalse(PictureRepairPolicy.eligible(0f,0f,0f,0f))
    }
    @Test fun `smooth low detail gradient is eligible`() {
        assertTrue(PictureRepairPolicy.eligible(0.65f,0.006f,0.006f,0.002f))
    }
    @Test fun `strong luma edge is protected`() {
        assertFalse(PictureRepairPolicy.eligible(1f,0.08f,0.004f,0.002f))
    }
    @Test fun `flat luma colour edge is protected`() {
        assertFalse(PictureRepairPolicy.eligible(1f,0.004f,0.10f,0.002f))
    }
    @Test fun `fine texture is protected`() {
        assertFalse(PictureRepairPolicy.eligible(1f,0.004f,0.004f,0.03f))
    }
    @Test fun `repair reach and blend remain conservative`() {
        val max=PictureRepairPolicy.limits(1f)
        assertTrue(max.radiusPx.endInclusive<=5.5f)
        assertTrue(max.maxBlend<=0.62f)
        assertTrue(max.ditherLsb<=0.85f)
    }
}
