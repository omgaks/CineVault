package com.sole.cinevault.network

import java.io.File
import java.io.RandomAccessFile

data class HttpByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

fun parseHttpRange(header: String?, fileLength: Long): HttpByteRange? {
    if (header.isNullOrBlank() || fileLength <= 0L) return null
    val value = header.trim()
    if (!value.startsWith("bytes=") || ',' in value) return null
    val spec = value.removePrefix("bytes=").trim()
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val left = spec.substring(0, dash).trim()
    val right = spec.substring(dash + 1).trim()

    return when {
        left.isNotEmpty() -> {
            val start = left.toLongOrNull() ?: return null
            if (start < 0 || start >= fileLength) return null
            val requestedEnd = right.toLongOrNull() ?: (fileLength - 1)
            if (requestedEnd < start) return null
            HttpByteRange(start, minOf(requestedEnd, fileLength - 1))
        }
        right.isNotEmpty() -> {
            val suffixLength = right.toLongOrNull() ?: return null
            if (suffixLength <= 0) return null
            val length = minOf(suffixLength, fileLength)
            HttpByteRange(fileLength - length, fileLength - 1)
        }
        else -> null
    }
}

fun readFileRange(file: File, range: HttpByteRange): ByteArray {
    require(file.isFile)
    require(range.start >= 0 && range.endInclusive < file.length())
    require(range.length <= Int.MAX_VALUE)
    return RandomAccessFile(file, "r").use { raf ->
        raf.seek(range.start)
        ByteArray(range.length.toInt()).also(raf::readFully)
    }
}

fun contentRangeHeader(range: HttpByteRange, fileLength: Long): String =
    "bytes ${range.start}-${range.endInclusive}/$fileLength"
