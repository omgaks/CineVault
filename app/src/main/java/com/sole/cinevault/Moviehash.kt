package com.sole.cinevault

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ── OpenSubtitles movie hash (a.k.a. "OSHash" / "moviehash") ────────────
// Verified against the published algorithm spec, not implemented from
// memory — getting this wrong is a genuinely dangerous failure mode
// specifically because it's SILENT: a single wrong byte anywhere in this
// produces a hash that just never matches anything, forever, with no
// error to signal it. Unlike a compile error, nothing here would ever
// tell us it's broken.
//
// Algorithm: file size + a 64-bit checksum of the first 64KB + a 64-bit
// checksum of the last 64KB, where each checksum is the sum (with normal
// 64-bit overflow wraparound) of that chunk read as 8192 little-endian
// 64-bit integers. All arithmetic is unsigned 64-bit with wraparound —
// Kotlin's Long addition already wraps identically in two's complement
// whether the bits are interpreted as signed or unsigned, so plain Long
// arithmetic here produces bit-identical results without needing
// ULong/unsigned types anywhere.
//
// Minimum file size is 131,072 bytes (128KB) per OpenSubtitles' own
// requirement — smaller files can't be hashed by this algorithm at all
// (the first/last 64KB chunks would overlap in a way the spec doesn't
// define), and OpenSubtitles rejects hash queries below that size anyway.
object MovieHash {

    private const val CHUNK_SIZE = 65536L // 64KB
    private const val MIN_FILE_SIZE = 131072L // 128KB, OpenSubtitles' own minimum

    // Returns a 16-character lowercase hex string, or null if the file is
    // too small to hash, doesn't exist, or couldn't be read. Zero-padded
    // explicitly — a documented real bug in other implementations is
    // producing a 15-character string when the top byte happens to be
    // zero, which OpenSubtitles' own docs specifically call out.
    fun compute(filePath: String): String? {
        return try {
            if (filePath.startsWith("content://", ignoreCase = true)) return null
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null
            val size = file.length()
            if (size < MIN_FILE_SIZE) return null

            RandomAccessFile(file, "r").use { raf ->
                var hash = size
                hash += sumChunkAsLongsLE(raf, 0L)
                hash += sumChunkAsLongsLE(raf, size - CHUNK_SIZE)
                "%016x".format(hash)
            }
        } catch (e: Exception) {
            null
        }
    }

    // content:// variant — reads via the file descriptor's own channel
    // rather than RandomAccessFile, which needs a real filesystem path.
    fun compute(context: Context, uri: Uri): String? {
        return try {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r") ?: return null
            afd.use {
                val size = it.length
                if (size < MIN_FILE_SIZE) return null
                java.io.FileInputStream(it.fileDescriptor).use { stream ->
                    val channel = stream.channel
                    var hash = size
                    hash += sumChunkAsLongsLE(channel, 0L)
                    hash += sumChunkAsLongsLE(channel, size - CHUNK_SIZE)
                    "%016x".format(hash)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sumChunkAsLongsLE(raf: RandomAccessFile, offset: Long): Long {
        raf.seek(offset)
        val buffer = ByteArray(CHUNK_SIZE.toInt())
        raf.readFully(buffer)
        return sumBufferAsLongsLE(buffer)
    }

    private fun sumChunkAsLongsLE(channel: java.nio.channels.FileChannel, offset: Long): Long {
        channel.position(offset)
        val buffer = ByteBuffer.allocate(CHUNK_SIZE.toInt())
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer)
            if (read < 0) break
        }
        return sumBufferAsLongsLE(buffer.array())
    }

    private fun sumBufferAsLongsLE(buffer: ByteArray): Long {
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0L
        val longCount = buffer.size / 8
        for (i in 0 until longCount) {
            sum += bb.long
        }
        return sum
    }
}
