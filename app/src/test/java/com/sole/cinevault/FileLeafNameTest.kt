package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Test

class FileLeafNameTest {
    @Test fun plainPathsKeepTheLastPart() {
        assertEquals("Dune.mkv", fileLeafName("/storage/emulated/0/Movies/Dune.mkv"))
        assertEquals("Dune.mkv", fileLeafName("C:\\Movies\\Dune.mkv"))
    }

    @Test fun contentAddressesAreDecoded() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/" +
            "primary%3ADownload%2FSeal%2FAngel%20youngs%20truth%20or%20dare.mp4"
        assertEquals("Angel youngs truth or dare.mp4", fileLeafName(uri))
    }

    @Test fun plusSignsInNamesSurvive() {
        assertEquals("A+B Film.mp4", fileLeafName("content://x/document/primary%3AMovies%2FA%2BB%20Film.mp4"))
    }

    @Test fun documentIdWithoutFolderDropsTheVolume() {
        assertEquals("Clip.mp4", fileLeafName("content://x/document/primary%3AClip.mp4"))
    }
}
