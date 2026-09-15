package com.sole.cinevault

import androidx.media3.exoplayer.DefaultRenderersFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class CineAudioRendererPreferenceTest {

    @Test
    fun normalPlaybackKeepsPlatformRendererFirst() {
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            extensionRendererModeForAudioPreference(
                CineAudioRendererPreference.PLATFORM_FIRST
            ),
        )
    }

    @Test
    fun audioRescuePrefersFfmpegRenderer() {
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            extensionRendererModeForAudioPreference(
                CineAudioRendererPreference.FFMPEG_FIRST
            ),
        )
    }
}
