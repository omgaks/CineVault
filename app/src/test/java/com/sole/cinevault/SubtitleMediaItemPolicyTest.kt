package com.sole.cinevault

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMediaItemPolicyTest {
    @Test
    fun noSubtitleKeepsSubtitleConfigurationEmpty() {
        val item = buildPlaybackMediaItem("file:///movie.mkv", null)
        assertTrue(item.localConfiguration!!.subtitleConfigurations.isEmpty())
    }

    @Test
    fun externalSrtIsAttachedToSameVideoItem() {
        val item = buildPlaybackMediaItem(
            "file:///movie.mkv",
            Uri.parse("file:///movie.srt")
        )
        assertEquals(1, item.localConfiguration!!.subtitleConfigurations.size)
        assertEquals(
            "file:///movie.mkv",
            item.localConfiguration!!.uri.toString()
        )
    }
}
