package com.sole.cinevault.glasses.stereo
internal object StereoPlaybackPolicy {
 fun resolve(detected:StereoPlaybackDecision,externalDisplayActive:Boolean,userOverride:StereoPlaybackMode?=null):StereoPlaybackDecision {
  userOverride?.let{return StereoPlaybackDecision(it,StereoDetectionConfidence.STRONG,"user override")}
  if(!externalDisplayActive)return StereoPlaybackDecision(StereoPlaybackMode.NORMAL_2D,StereoDetectionConfidence.NONE,"host display keeps normal 2D playback")
  return detected
 }
}
