package com.sole.cinevault

data class SubtitleSearchResponse(
    val data: List<SubtitleItem>
)

data class SubtitleItem(
    val attributes: SubtitleAttributes
)

data class SubtitleAttributes(
    val language: String?,
    val release: String?,
    val files: List<SubtitleFile>
)

data class SubtitleFile(
    val file_id: Int
)

data class SubtitleDownloadRequest(
    val file_id: Int
)

data class SubtitleDownloadResponse(
    val link: String,
    val file_name: String
)