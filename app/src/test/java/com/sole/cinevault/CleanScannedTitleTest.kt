package com.sole.cinevault

import com.sole.cinevault.library.cleanScannedTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanScannedTitleTest {
    @Test fun editionWordsAreDropped() {
        assertEquals("I Am Legend", cleanScannedTitle("I.Am.Legend.ALTERNATE.ENDING.2007.1080p.BluRay.mkv"))
        assertEquals("Blade Runner", cleanScannedTitle("Blade.Runner.Remastered.Unrated.1982.mkv"))
    }

    @Test fun plainTitleIsKept() {
        assertEquals("Dune", cleanScannedTitle("Dune.2021.1080p.mkv"))
    }
}
