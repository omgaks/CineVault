package com.sole.cinevault.glasses.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlassesCalibrationIdentityTest {
    @Test
    fun blankDisplayNameCannotCreatePersistentCalibrationIdentity() {
        assertNull(GlassesCalibrationIdentity.preferenceKey(null))
        assertNull(GlassesCalibrationIdentity.preferenceKey(""))
        assertNull(GlassesCalibrationIdentity.preferenceKey("   "))
    }

    @Test
    fun displayNameIsTrimmedBeforeBuildingPreferenceKey() {
        assertEquals(
            "calibrated_RayNeo Air",
            GlassesCalibrationIdentity.preferenceKey("  RayNeo Air  "),
        )
    }

    @Test
    fun modelNamesRemainDistinct() {
        assertEquals(
            "calibrated_Model A",
            GlassesCalibrationIdentity.preferenceKey("Model A"),
        )
        assertEquals(
            "calibrated_Model B",
            GlassesCalibrationIdentity.preferenceKey("Model B"),
        )
    }
}
