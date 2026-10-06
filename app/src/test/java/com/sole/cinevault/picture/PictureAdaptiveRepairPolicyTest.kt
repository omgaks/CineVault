package com.sole.cinevault.picture

import org.junit.Assert.assertTrue
import org.junit.Test

class PictureAdaptiveRepairPolicyTest {
    @Test fun `eco is lighter than balanced and max`() {
        val eco=PictureAdaptiveRepairPolicy.forState(PictureContent.FILM,0.62f)
        val balanced=PictureAdaptiveRepairPolicy.forState(PictureContent.FILM,0.90f)
        val max=PictureAdaptiveRepairPolicy.forState(PictureContent.FILM,1.00f)
        assertTrue(eco.repair<balanced.repair)
        assertTrue(balanced.repair<max.repair)
        assertTrue(eco.chroma<balanced.chroma)
        assertTrue(balanced.chroma<max.chroma)
    }
    @Test fun `anime gets stronger repair than film`() {
        val anime=PictureAdaptiveRepairPolicy.forState(PictureContent.ANIME,0.90f)
        val film=PictureAdaptiveRepairPolicy.forState(PictureContent.FILM,0.90f)
        assertTrue(anime.repair>film.repair)
    }
    @Test fun `animation gets stronger chroma reconstruction than film`() {
        val animation=PictureAdaptiveRepairPolicy.forState(PictureContent.ANIMATION,0.90f)
        val film=PictureAdaptiveRepairPolicy.forState(PictureContent.FILM,0.90f)
        assertTrue(animation.chroma>film.chroma)
    }
    @Test fun `all multipliers remain safely bounded`() {
        PictureContent.values().forEach { c ->
            listOf(0f,0.62f,0.90f,1f).forEach { q ->
                val p=PictureAdaptiveRepairPolicy.forState(c,q)
                assertTrue(p.repair in 0f..1f)
                assertTrue(p.chroma in 0f..1f)
                assertTrue(p.sharpenGuard in 0f..1f)
            }
        }
    }
}
