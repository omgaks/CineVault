package com.sole.cinevault
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleWebPolicyTest {
    @Test
    fun acceptsOnlyApprovedHttpsHosts() {
        assertTrue(SubtitleWebPolicy.isAllowed(Uri.parse("https://www.opensubtitles.com/en/search")))
        assertTrue(SubtitleWebPolicy.isAllowed(Uri.parse("https://dl.opensubtitles.com/file.zip")))
        assertFalse(SubtitleWebPolicy.isAllowed(Uri.parse("http://www.opensubtitles.com/file.zip")))
        assertFalse(SubtitleWebPolicy.isAllowed(Uri.parse("https://opensubtitles.com.evil.example/file.zip")))
        assertFalse(SubtitleWebPolicy.isAllowed(Uri.parse("https://evil.example/?opensubtitles.com")))
    }
}
