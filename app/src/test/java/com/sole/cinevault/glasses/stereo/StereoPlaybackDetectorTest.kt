package com.sole.cinevault.glasses.stereo
import org.junit.Assert.*
import org.junit.Test
class StereoPlaybackDetectorTest {
 @Test fun normalMovieStays2d(){assertEquals(StereoPlaybackMode.NORMAL_2D,StereoPlaybackDetector.detect("Fallout.S01E01.2160p.mkv").mode)}
 @Test fun sbsMarkerDetectsSideBySide(){assertEquals(StereoPlaybackMode.SIDE_BY_SIDE,StereoPlaybackDetector.detect("Avatar.3D.HSBS.1080p.mkv").mode)}
 @Test fun topBottomMarkerDetectsTopBottom(){assertEquals(StereoPlaybackMode.TOP_BOTTOM,StereoPlaybackDetector.detect("Movie.3D.HOU.mkv").mode)}
 @Test fun wideDimensionsAloneNeverForceStereo(){assertEquals(StereoPlaybackMode.NORMAL_2D,StereoPlaybackDetector.detect("ordinary_movie.mkv",width=3840,height=1080).mode)}
 @Test fun matchingDimensionsStrengthenSbsMarker(){assertEquals(StereoDetectionConfidence.STRONG,StereoPlaybackDetector.detect("movie.HSBS.mkv",width=3840,height=1080).confidence)}
 @Test fun conflictingMarkersFallBackTo2d(){assertEquals(StereoPlaybackMode.NORMAL_2D,StereoPlaybackDetector.detect("movie.SBS.HOU.mkv").mode)}
 @Test fun wordsContainingMarkerLettersAreNot3d(){
  listOf("The.House.2019.mkv","Stable.Boys.mkv","Mountain.Hours.mkv","Four.Rooms.1080p.mkv","Table.for.Two.mkv").forEach{
   assertEquals(it,false,StereoPlaybackDetector.looksStereo(it))
   assertEquals(it,StereoPlaybackMode.NORMAL_2D,StereoPlaybackDetector.detect(it).mode)
  }
 }
 @Test fun realMarkersStillCount(){
  listOf("Avatar.3D.HSBS.mkv","Movie 3D Half-SBS.mkv","Film.SBS.mkv","Film.Side.by.Side.mkv","Movie.3D.HOU.mkv","Movie.Half-OU.mkv","Film.Top-Bottom.mkv","Pirates of the Caribbean 3D.mkv").forEach{
   assertEquals(it,true,StereoPlaybackDetector.looksStereo(it))
  }
 }
 @Test fun halfOuAndSideBySideSetTheRightMode(){
  assertEquals(StereoPlaybackMode.TOP_BOTTOM,StereoPlaybackDetector.detect("Movie.Half-OU.mkv").mode)
  assertEquals(StereoPlaybackMode.SIDE_BY_SIDE,StereoPlaybackDetector.detect("Film.Side.by.Side.mkv").mode)
 }
}
