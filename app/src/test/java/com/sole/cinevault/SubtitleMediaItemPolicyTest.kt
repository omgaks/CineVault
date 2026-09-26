package com.sole.cinevault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleMediaItemPolicyTest {

    @Test
    fun subtitleAttachmentRequestedWhenSubtitleExists() {
        assertTrue(shouldAttachExternalSubtitle(hasSubtitleUri = true))
    }

    @Test
    fun subtitleAttachmentNotRequestedWithoutSubtitle() {
        assertFalse(shouldAttachExternalSubtitle(hasSubtitleUri = false))
    }
}
