package com.sole.cinevault.picture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureChromaPolicyTest {
 @Test fun `off disables chroma reconstruction`() {
  assertFalse(PictureChromaPolicy.decide(0f,0f,0f).enabled)
 }
 @Test fun `smooth low chroma edge permits conservative reconstruction`() {
  val d=PictureChromaPolicy.decide(1f,0.004f,0.010f)
  assertTrue(d.enabled); assertTrue(d.strength>0f); assertTrue(d.strength<=0.24f)
 }
 @Test fun `strong luma edge blocks reconstruction`() {
  assertFalse(PictureChromaPolicy.decide(1f,0.10f,0.005f).enabled)
 }
 @Test fun `strong colour boundary blocks reconstruction`() {
  assertFalse(PictureChromaPolicy.decide(1f,0.005f,0.15f).enabled)
 }
}
