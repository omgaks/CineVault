package com.sole.cinevault.glasses.halo

import org.junit.Assert.assertEquals
import org.junit.Test

class PadScrollPrefsTest {
    @Test
    fun reversedFlipsTheDirection() {
        assertEquals(-1f, scrollSign(reversed = true), 0f)
    }

    @Test
    fun naturalKeepsPhoneDirection() {
        assertEquals(1f, scrollSign(reversed = false), 0f)
    }
}
