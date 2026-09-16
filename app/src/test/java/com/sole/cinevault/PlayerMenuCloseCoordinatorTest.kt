package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMenuCloseCoordinatorTest {
    @Test
    fun closeAllClosesAudioStudioAndEveryTransientSurface() {
        val closed = mutableListOf<String>()
        val coordinator = PlayerMenuCloseCoordinator(
            closeAudioSelector = { closed += "audioSelector" },
            closeAudioFxDashboard = { closed += "audioFx" },
            closeSettings = { closed += "settings" },
            closeDriftDialog = { closed += "drift" },
            closeSpeedMenu = { closed += "speed" },
            closeSleepMenu = { closed += "sleep" },
            closeSrtBrowser = { closed += "srt" },
            closeSubtitleSurfaces = { closed += "subtitles" },
        )

        coordinator.closeAll()

        assertEquals(
            listOf(
                "audioSelector",
                "audioFx",
                "settings",
                "drift",
                "speed",
                "sleep",
                "srt",
                "subtitles",
            ),
            closed,
        )
    }
}
