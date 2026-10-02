package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubtitleDisplayProfileDefaultsTest {

    @Test
    fun externalProfileKeepsDedicatedThirtySpDefault() {
        val external = defaultSubtitleProfileSettings(
            DisplayProfileType.EXTERNAL,
            isLandscape = true,
        )
        val phone = defaultSubtitleProfileSettings(
            DisplayProfileType.PHONE,
            isLandscape = true,
        )

        assertEquals(30f, external.fontSizeSp, 0.001f)
        assertNotEquals(phone.fontSizeSp, external.fontSizeSp, 0.001f)
        assertFalse(external.preserveOriginalStyling)
    }

    @Test
    fun profileIdsSeparateDisplayTypeAndOrientation() {
        assertEquals("phone_portrait", displayProfileId(DisplayProfileType.PHONE, false))
        assertEquals("phone_landscape", displayProfileId(DisplayProfileType.PHONE, true))
        assertEquals("external_portrait", displayProfileId(DisplayProfileType.EXTERNAL, false))
        assertEquals("external_landscape", displayProfileId(DisplayProfileType.EXTERNAL, true))
    }
}
