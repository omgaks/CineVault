package com.sole.cinevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerCompletenessTest {
    private fun reader(bytes: ByteArray): ByteReader = { offset, length ->
        if (offset >= bytes.size) null
        else bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + length))
    }

    /** EBML header (size 4, 4 bytes content) + Segment declaring [segmentSize] bytes. */
    private fun mkv(segmentSize: Long, actualSegmentBytes: Int): ByteArray {
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x84.toByte(), 0, 0, 0, 0)
        val seg = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0x01, 0, 0, 0, 0, 0, 0, 0)
        for (i in 0 until 7) seg[5 + i] = ((segmentSize shr (8 * (6 - i))) and 0xFF).toByte()
        return ebml + seg + ByteArray(actualSegmentBytes - 8 + 0)
    }

    @Test fun completeMatroskaIsComplete() {
        val file = mkv(segmentSize = 100, actualSegmentBytes = 100 + 8)
        assertEquals(Completeness.COMPLETE, checkContainerCompleteness(file.size.toLong(), reader(file)))
    }

    @Test fun truncatedMatroskaIsIncomplete() {
        val file = mkv(segmentSize = 5000, actualSegmentBytes = 100 + 8)
        assertEquals(Completeness.INCOMPLETE, checkContainerCompleteness(file.size.toLong(), reader(file)))
    }

    private fun box(type: String, total: Int): ByteArray {
        val out = ByteArray(total)
        out[0] = (total shr 24).toByte(); out[1] = (total shr 16).toByte()
        out[2] = (total shr 8).toByte(); out[3] = total.toByte()
        type.forEachIndexed { i, c -> out[4 + i] = c.code.toByte() }
        return out
    }

    @Test fun completeMp4IsComplete() {
        val file = box("ftyp", 24) + box("moov", 40) + box("mdat", 100)
        assertEquals(Completeness.COMPLETE, checkContainerCompleteness(file.size.toLong(), reader(file)))
    }

    @Test fun truncatedMp4IsIncomplete() {
        val whole = box("ftyp", 24) + box("moov", 40) + box("mdat", 100)
        val cut = whole.copyOf(whole.size - 30)
        assertEquals(Completeness.INCOMPLETE, checkContainerCompleteness(cut.size.toLong(), reader(cut)))
    }

    @Test fun unknownFormatsAreNeverAccused() {
        val junk = ByteArray(200) { 7 }
        assertEquals(Completeness.UNKNOWN, checkContainerCompleteness(200, reader(junk)))
        assertEquals(Completeness.UNKNOWN, checkContainerCompleteness(5, reader(ByteArray(5))))
    }

    @Test fun slowSoftwareVideoNeedsEnoughFramesAndManyDrops() {
        assertFalse(softwareVideoIsTooSlow(10, 10))
        assertTrue(softwareVideoIsTooSlow(60, 60))
        assertFalse(softwareVideoIsTooSlow(300, 10))
    }
}
