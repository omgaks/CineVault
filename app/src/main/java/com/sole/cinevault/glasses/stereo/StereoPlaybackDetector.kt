package com.sole.cinevault.glasses.stereo
import java.util.Locale
internal object StereoPlaybackDetector {
 private val sbsTokens=setOf("sbs","hsbs","fsbs","sidebyside")
 private val tbTokens=setOf("tab","htab","ftab","hou","fou","topbottom","overunder")
 // Markers are matched as whole words, so "House", "Stable" or "Mountain" never look like 3D.
 private fun tokens(text:String):List<String> = text.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter(String::isNotEmpty)
 private fun hasPair(t:List<String>,a:String,b:String)=t.zipWithNext().any{it.first==a&&it.second==b}
 private fun isSbs(t:List<String>)=t.any(sbsTokens::contains)||(hasPair(t,"side","by")&&t.windowed(3).any{it==listOf("side","by","side")})
 private fun isTb(t:List<String>)=t.any(tbTokens::contains)||hasPair(t,"top","bottom")||hasPair(t,"over","under")||
  t.zipWithNext().any{it.second=="ou"&&it.first in setOf("3d","half","full","h","f")}
 /** True when a file name says the film is 3D: a layout marker or a plain "3D" word. Used for library badges. */
 fun looksStereo(fileName:String):Boolean { val t=tokens(fileName); return isSbs(t)||isTb(t)||"3d" in t }
 fun detect(fileName:String,path:String=fileName,width:Int?=null,height:Int?=null):StereoPlaybackDecision {
  val t=tokens("$fileName $path"); val a=isSbs(t); val b=isTb(t)
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
