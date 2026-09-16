package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMenuCloseCoordinatorTest {
    @Test
    fun closeAllClosesEveryTransientPlayerSurface() {
        val closed = mutableListOf<String>()
        val coordinator = PlayerMenuCloseCoordinator(
            closeAudioSelector = { closed += "audioSelector" },
            closeAudioFxDashboard = { closed += "audioFx" },
            closeSettings = { closed += "settings" },
            closeDriftDialog = { closed += "drift" },
            closeDialogueSync = { closed += "dialogueSync" },
            closeSpeedMenu = { closed += "speed" },
            closeSleepMenu = { closed += "sleep" },
            closeSrtBrowser = { closed += "srt" },
            closeSubtitleDock = { closed += "subtitleDock" },
            closeSubtitleBloom = { closed += "subtitleBloom" },
            closeDualSubsWindow = { closed += "dualSubs" },
            closeSubtitleBehaviourWindow = { closed += "subtitleBehaviour" },
            closeSubtitleSurfaces = { closed += "subtitleSurfaces" },
        )

        coordinator.closeAll()

        assertEquals(
            listOf(
                "audioSelector", "audioFx", "settings", "drift", "dialogueSync", "speed",
                "sleep", "srt", "subtitleDock", "subtitleBloom", "dualSubs",
                "subtitleBehaviour", "subtitleSurfaces",
            ),
            closed,
        )
    }
}
