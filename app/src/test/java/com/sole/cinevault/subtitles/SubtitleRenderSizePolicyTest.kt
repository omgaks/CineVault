package com.sole.cinevault.subtitles

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleRenderSizePolicyTest {
    @Test fun compactPortraitConstrainsRenderedSizeWithoutChangingRequestedValue() {
        assertEquals(24.6f, adaptiveSubtitleRenderSizeSp(30f, 390f, 844f), 0.01f)
    }

    @Test fun mediumPortraitUsesGentlerConstraint() {
        assertEquals(27f, adaptiveSubtitleRenderSizeSp(30f, 700f, 1000f), 0.01f)
    }

    @Test fun landscapeKeepsRequestedSize() {
        assertEquals(30f, adaptiveSubtitleRenderSizeSp(30f, 844f, 390f), 0.01f)
    }

    @Test fun expandedPortraitKeepsRequestedSize() {
        assertEquals(30f, adaptiveSubtitleRenderSizeSp(30f, 900f, 1200f), 0.01f)
    }
}
