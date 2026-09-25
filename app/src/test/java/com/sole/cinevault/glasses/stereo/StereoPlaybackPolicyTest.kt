package com.sole.cinevault.glasses.stereo
import org.junit.Assert.assertEquals
import org.junit.Test
class StereoPlaybackPolicyTest {
 private val sbs=StereoPlaybackDecision(StereoPlaybackMode.SIDE_BY_SIDE,StereoDetectionConfidence.STRONG,"test")
 @Test fun hostDisplayNeverAutoEnablesStereo(){assertEquals(StereoPlaybackMode.NORMAL_2D,StereoPlaybackPolicy.resolve(sbs,false).mode)}
 @Test fun externalDisplayAcceptsDetectedStereo(){assertEquals(StereoPlaybackMode.SIDE_BY_SIDE,StereoPlaybackPolicy.resolve(sbs,true).mode)}
 @Test fun explicitOverrideWins(){assertEquals(StereoPlaybackMode.TOP_BOTTOM,StereoPlaybackPolicy.resolve(sbs,true,StereoPlaybackMode.TOP_BOTTOM).mode)}
}
