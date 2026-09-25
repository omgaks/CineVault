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
}
