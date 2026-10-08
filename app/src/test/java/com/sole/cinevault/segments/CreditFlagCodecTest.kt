package com.sole.cinevault.segments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditFlagCodecTest {
    @Test fun roundTrips() {
        for (mid in listOf(false, true)) for (post in listOf(false, true)) {
            assertEquals(mid to post, CreditFlagCodec.decode(CreditFlagCodec.encode(mid, post)))
        }
    }

    @Test fun rejectsGarbage() {
        assertNull(CreditFlagCodec.decode(null))
        assertNull(CreditFlagCodec.decode(""))
        assertNull(CreditFlagCodec.decode("1"))
        assertNull(CreditFlagCodec.decode("2,0"))
        assertNull(CreditFlagCodec.decode("1,0,1"))
    }
}
