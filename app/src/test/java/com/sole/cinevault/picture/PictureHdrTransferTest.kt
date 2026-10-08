package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureHdrTransferTest {
    @Test fun `PQ black and peak map to reference luminance`() {
        assertEquals(0.0, PictureHdrTransfer.pqToNits(0.0), 0.00001)
        assertEquals(10000.0, PictureHdrTransfer.pqToNits(1.0), 0.01)
    }

    @Test fun `PQ 100 nits round trips`() {
        val encoded = PictureHdrTransfer.nitsToPq(100.0)
        assertEquals(0.508, encoded, 0.002)
        assertEquals(100.0, PictureHdrTransfer.pqToNits(encoded), 0.01)
    }

    @Test fun `PQ reference values are monotonic`() {
        val samples = listOf(0.0, 0.1, 0.25, 0.5, 0.75, 1.0)
        assertTrue(samples.zipWithNext().all { (a, b) ->
            PictureHdrTransfer.pqToNits(a) < PictureHdrTransfer.pqToNits(b)
        })
    }

    @Test fun `HLG breakpoint matches one twelfth scene linear`() {
        assertEquals(1.0 / 12.0, PictureHdrTransfer.hlgToSceneLinear(0.5), 0.000001)
        assertEquals(0.5, PictureHdrTransfer.sceneLinearToHlg(1.0 / 12.0), 0.000001)
    }

    @Test fun `HLG scene linear values round trip`() {
        listOf(0.0, 0.01, 1.0 / 12.0, 0.2, 0.5, 1.0).forEach { value ->
            assertEquals(value, PictureHdrTransfer.hlgToSceneLinear(
                PictureHdrTransfer.sceneLinearToHlg(value)), 0.00001)
        }
    }

    @Test fun `nonfinite and out of range values are bounded`() {
        assertEquals(0.0, PictureHdrTransfer.pqToNits(Double.NaN), 0.0)
        assertEquals(0.0, PictureHdrTransfer.hlgToSceneLinear(-2.0), 0.0)
        assertEquals(1.0, PictureHdrTransfer.sceneLinearToHlg(3.0), 0.00001)
        assertEquals(1.0, PictureHdrTransfer.nitsToPq(20000.0), 0.00001)
    }
}
