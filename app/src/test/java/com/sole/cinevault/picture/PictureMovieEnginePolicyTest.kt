package com.sole.cinevault.picture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class PictureMovieEnginePolicyTest {
 @Test fun `film only`() {
  assertEquals(1f,PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,1080).enabled,0f)
  listOf(PictureContent.AUTO,PictureContent.ANIME,PictureContent.ANIMATION).forEach{
   assertEquals(0f,PictureMovieEnginePolicy.forState(it,.9f,1080).enabled,0f)}
 }
 @Test fun `lower resolution receives more recovery`() {
  val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
  val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
  assertTrue(sd.detailRecovery>uhd.detailRecovery)
 }
 @Test fun `UHD strengthens preservation and narrows recovery band`() {
  val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
  val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
  assertTrue(uhd.textureProtection>sd.textureProtection);assertTrue(uhd.grainProtection>sd.grainProtection)
  assertTrue(uhd.sharpenCeiling<sd.sharpenCeiling);assertTrue(uhd.structureFloor>sd.structureFloor)
  assertTrue(uhd.textureCeiling<sd.textureCeiling)
 }
 @Test fun `quality changes recovery not protection gates`() {
  val e=PictureMovieEnginePolicy.forState(PictureContent.FILM,.6f,720)
  val b=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,720)
  val m=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,720)
  assertTrue(e.detailRecovery<b.detailRecovery);assertTrue(b.detailRecovery<m.detailRecovery)
  assertEquals(e.structureFloor,m.structureFloor,0f);assertEquals(e.textureCeiling,m.textureCeiling,0f)
 }
 @Test fun `face recovery remains conservative`() {
  val sd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,576)
  val uhd=PictureMovieEnginePolicy.forState(PictureContent.FILM,1f,2160)
  assertTrue(sd.skinProtection>=.90f);assertTrue(sd.faceRecoveryScale<=.22f)
  assertTrue(uhd.faceRecoveryScale<=sd.faceRecoveryScale);assertTrue(uhd.faceRecoveryScale>=.16f)
 }
 @Test fun `disabled path cannot leak movie recovery`() {
  val p=PictureMovieEnginePolicy.forState(PictureContent.ANIME,1f,576)
  assertEquals(0f,p.enabled,0f);assertEquals(0f,p.detailRecovery,0f)
  assertEquals(0f,p.faceRecoveryScale,0f);assertEquals(0f,p.sharpenCeiling,0f)
 }
}
