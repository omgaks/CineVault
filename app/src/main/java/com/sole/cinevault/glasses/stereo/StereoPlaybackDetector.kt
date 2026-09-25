package com.sole.cinevault.glasses.stereo
import java.util.Locale
internal object StereoPlaybackDetector {
 private val sbsMarkers=listOf("sbs","sidebyside","side-by-side","side_by_side","hsbs","half-sbs","half_sbs","full-sbs","full_sbs","3d sbs","3d.sbs","3d-sbs","3d_sbs")
 private val tbMarkers=listOf("tab","topbottom","top-bottom","top_bottom","hou","half-ou","half_ou","overunder","over-under","over_under","3d tab","3d.tab","3d-tab","3d_tab","3d ou","3d.ou","3d-ou","3d_ou")
 fun detect(fileName:String,path:String=fileName,width:Int?=null,height:Int?=null):StereoPlaybackDecision {
  val s=("$fileName $path").lowercase(Locale.ROOT); val a=sbsMarkers.any(s::contains); val b=tbMarkers.any(s::contains)
  if(a&&!b)return decision(StereoPlaybackMode.SIDE_BY_SIDE,width,height,"SBS source marker detected")
  if(b&&!a)return decision(StereoPlaybackMode.TOP_BOTTOM,width,height,"top/bottom source marker detected")
  return StereoPlaybackDecision(StereoPlaybackMode.NORMAL_2D,StereoDetectionConfidence.NONE,if(a&&b)"conflicting stereo source markers; keeping 2D fallback" else "no explicit stereo source marker")
 }
 private fun decision(mode:StereoPlaybackMode,width:Int?,height:Int?,reason:String):StereoPlaybackDecision {
  val valid=width!=null&&height!=null&&width>0&&height>0
  val agrees=valid&&when(mode){StereoPlaybackMode.SIDE_BY_SIDE->width!!>=height!!*2;StereoPlaybackMode.TOP_BOTTOM->height!!>=width!!;StereoPlaybackMode.NORMAL_2D->false}
  return StereoPlaybackDecision(mode,if(agrees)StereoDetectionConfidence.STRONG else StereoDetectionConfidence.WEAK,if(agrees)"$reason; dimensions agree" else reason)
 }
}
