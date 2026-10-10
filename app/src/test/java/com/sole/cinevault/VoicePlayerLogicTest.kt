package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePlayerLogicTest {
    @Test fun skipsStayInsideTheFilm() {
        assertEquals(40_000L, seekTargetMs(10_000, 100_000, 30_000))
        assertEquals(0L, seekTargetMs(10_000, 100_000, -30_000))
        assertEquals(100_000L, seekTargetMs(90_000, 100_000, 30_000))
    }

    @Test fun jumpsAreClamped() {
        assertEquals(3_900_000L, jumpTargetMs(3_900_000, 7_000_000))
        assertEquals(7_000_000L, jumpTargetMs(9_000_000, 7_000_000))
    }

    @Test fun volumeStaysInRange() {
        assertEquals(60, volumeTargetPercent(50, VoiceCommand.VolumeBy(10)))
        assertEquals(100, volumeTargetPercent(95, VoiceCommand.VolumeBy(10)))
        assertEquals(0, volumeTargetPercent(5, VoiceCommand.VolumeBy(-10)))
        assertEquals(30, volumeTargetPercent(80, VoiceCommand.VolumeTo(30)))
        assertNull(volumeTargetPercent(50, VoiceCommand.Pause))
    }

    @Test fun brightnessNeverGoesBlack() {
        assertEquals(5, brightnessTargetPercent(10, VoiceCommand.BrightnessBy(-50)))
        assertEquals(100, brightnessTargetPercent(95, VoiceCommand.BrightnessBy(20)))
        assertEquals(70, brightnessTargetPercent(10, VoiceCommand.BrightnessTo(70)))
        assertNull(brightnessTargetPercent(50, VoiceCommand.Mute))
    }

    @Test fun speedIsLimitedAndTidy() {
        assertEquals(1.5f, speedTarget(1.0f, VoiceCommand.SpeedTo(1.5f)))
        assertEquals(3.0f, speedTarget(2.9f, VoiceCommand.SpeedBy(0.5f)))
        assertEquals(0.25f, speedTarget(0.3f, VoiceCommand.SpeedBy(-0.5f)))
        assertNull(speedTarget(1.0f, VoiceCommand.Pause))
    }
}
