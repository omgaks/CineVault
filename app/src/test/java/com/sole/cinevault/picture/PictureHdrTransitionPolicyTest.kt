package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureHdrTransitionPolicyTest {
    @Test fun sdrWithoutEffectRemainsSdr() = assertEquals(
        PictureHdrTransitionPolicy.Action.KEEP_SDR,
        PictureHdrTransitionPolicy.decide(PictureHdrPolicy.Route.SDR_ENHANCEMENT, false))
    @Test fun sdrWithEffectRemainsSdr() = assertEquals(
        PictureHdrTransitionPolicy.Action.KEEP_SDR,
        PictureHdrTransitionPolicy.decide(PictureHdrPolicy.Route.SDR_ENHANCEMENT, true))
    @Test fun hdrWithoutEffectStaysPassthrough() = assertEquals(
        PictureHdrTransitionPolicy.Action.KEEP_PASSTHROUGH,
        PictureHdrTransitionPolicy.decide(PictureHdrPolicy.Route.HDR_PASSTHROUGH, false))
    @Test fun hdrWithSdrEffectRequiresRemoval() = assertEquals(
        PictureHdrTransitionPolicy.Action.REMOVE_SDR_EFFECT_AND_REPREPARE,
        PictureHdrTransitionPolicy.decide(PictureHdrPolicy.Route.HDR_PASSTHROUGH, true))
}
