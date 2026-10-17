package com.sole.cinevault

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

internal enum class Completeness { COMPLETE, INCOMPLETE, UNKNOWN }

/** Reads [length] bytes at [offset], or null if that is not possible. */
internal typealias ByteReader = (offset: Long, length: Int) -> ByteArray?

/**
 * Looks only at the file's headers to see whether it is shorter than it says it
 * is, which is what a half-finished download looks like.
 *
 * Matroska (MKV/WebM): the Segment element declares its own size.
 * MP4/MOV: the top-level boxes declare theirs, and must end exactly at the end.
 * Anything else, or anything unclear, is UNKNOWN. Never guesses INCOMPLETE.
 */
internal fun checkContainerCompleteness(fileLength: Long, read: ByteReader): Completeness {
    if (fileLength <= 16L) return Completeness.UNKNOWN
    val head = read(0L, 12) ?: return Completeness.UNKNOWN
    if (head.size < 12) return Completeness.UNKNOWN
    return when {
        head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() ->
            checkMatroska(fileLength, read)
        head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() ->
            checkMp4(fileLength, read)
        else -> Completeness.UNKNOWN
    }
}

private class Vint(val value: Long, val length: Int, val unknownSize: Boolean)

/** EBML variable-length integer. [keepMarker] is true for element IDs. */
private fun parseVint(bytes: ByteArray, at: Int, keepMarker: Boolean): Vint? {
    if (at >= bytes.size) return null
    val first = bytes[at].toInt() and 0xFF
    if (first == 0) return null
    val length = Integer.numberOfLeadingZeros(first) - 24 + 1
    if (length > 8 || at + length > bytes.size) return null
    val dataMask = 0xFF shr length
    var value = if (keepMarker) first.toLong() else (first and dataMask).toLong()
    var allOnes = (first and dataMask) == dataMask
    for (i in 1 until length) {
        val b = bytes[at + i].toInt() and 0xFF
        value = (value shl 8) or b.toLong()
        if (b != 0xFF) allOnes = false
    }
    return Vint(value, length, allOnes && !keepMarker)
}

private fun checkMatroska(fileLength: Long, read: ByteReader): Completeness {
    val header = read(0L, 64) ?: return Completeness.UNKNOWN
    // EBML header: ID (4 bytes) + size, then its content, then the Segment.
    val ebmlSize = parseVint(header, 4, keepMarker = false) ?: return Completeness.UNKNOWN
    val segmentStart = 4L + ebmlSize.length + ebmlSize.value
    if (segmentStart + 12 > fileLength) return Completeness.UNKNOWN
    val seg = read(segmentStart, 12) ?: return Completeness.UNKNOWN
    if (seg.size < 12) return Completeness.UNKNOWN
    val isSegment = seg[0] == 0x18.toByte() && seg[1] == 0x53.toByte() &&
        seg[2] == 0x80.toByte() && seg[3] == 0x67.toByte()
    if (!isSegment) return Completeness.UNKNOWN
    val size = parseVint(seg, 4, keepMarker = false) ?: return Completeness.UNKNOWN
    if (size.unknownSize) return Completeness.UNKNOWN
    val dataStart = segmentStart + 4 + size.length
    val declaredEnd = dataStart + size.value
    return if (declaredEnd > fileLength) Completeness.INCOMPLETE else Completeness.COMPLETE
}

private fun checkMp4(fileLength: Long, read: ByteReader): Completeness {
    var offset = 0L
    repeat(256) {
        val box = read(offset, 16) ?: return Completeness.UNKNOWN
        if (box.size < 8) return Completeness.UNKNOWN
        var size = ((box[0].toLong() and 0xFF) shl 24) or ((box[1].toLong() and 0xFF) shl 16) or
            ((box[2].toLong() and 0xFF) shl 8) or (box[3].toLong() and 0xFF)
        if (size == 0L) return Completeness.COMPLETE            // runs to the end of the file
        if (size == 1L) {
            if (box.size < 16) return Completeness.UNKNOWN
            size = 0L
            for (i in 8 until 16) size = (size shl 8) or (box[i].toLong() and 0xFF)
        }
        if (size < 8L) return Completeness.UNKNOWN
        val end = offset + size
        if (end > fileLength) return Completeness.INCOMPLETE
        if (end == fileLength) return Completeness.COMPLETE
        offset = end
    }
    return Completeness.UNKNOWN
}

/** Checks a local file or content:// document. Network sources are UNKNOWN. */
internal fun checkFileCompleteness(context: Context, path: String): Completeness = runCatching {
    when {
        path.startsWith("content://") -> {
            val uri = Uri.parse(path)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val length = pfd.statSize
                if (length <= 0L) return@use Completeness.UNKNOWN
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    checkContainerCompleteness(length) { offset, count ->
                        val buffer = java.nio.ByteBuffer.allocate(count)
                        val n = channel.read(buffer, offset)
                        if (n <= 0) null else buffer.array().copyOf(n)
                    }
                }
            } ?: Completeness.UNKNOWN
        }
        path.contains("://") -> Completeness.UNKNOWN
        else -> {
            val file = File(path)
            if (!file.isFile) return@runCatching Completeness.UNKNOWN
            RandomAccessFile(file, "r").use { raf ->
                checkContainerCompleteness(raf.length()) { offset, count ->
                    if (offset >= raf.length()) null else {
                        raf.seek(offset)
                        val buffer = ByteArray(count)
                        val n = raf.read(buffer)
                        if (n <= 0) null else buffer.copyOf(n)
                    }
                }
            }
        }
    }
}.getOrDefault(Completeness.UNKNOWN)

/** True once enough frames have gone by and about as many were dropped as shown. */
internal fun softwareVideoIsTooSlow(presented: Int, dropped: Int): Boolean =
    presented + dropped >= 90 && dropped * 10 >= (presented + dropped) * 4
