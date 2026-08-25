package com.sole.cinevault

import com.sole.cinevault.subtitles.OpenSubtitlesClient

internal fun playerSubtitleSearchQuery(
    videoPath: String,
): String = OpenSubtitlesClient.cleanMovieNamePublic(videoPath)
