package com.sole.cinevault.picture

import org.junit.Assert.assertEquals
import org.junit.Test

/** P6 closure regression: Movie, Anime and Animation execution policies must remain isolated. */
class PictureEngineIsolationTest {
    @Test fun `film enables movie engine and disables anime bridge`() {
        val movie=PictureMovieEnginePolicy.forState(PictureContent.FILM,.9f,720)
        val anime=PictureAnimeEnginePolicy.forState(PictureContent.FILM,.9f,720)
        assertEquals(1f,movie.enabled,0f)
        assertEquals(0f,anime.enabled,0f)
    }

    @Test fun `anime enables anime engine and disables movie engine`() {
        val movie=PictureMovieEnginePolicy.forState(PictureContent.ANIME,.9f,720)
        val anime=PictureAnimeEnginePolicy.forState(PictureContent.ANIME,.9f,720)
        assertEquals(0f,movie.enabled,0f)
        assertEquals(1f,anime.enabled,0f)
    }

    @Test fun `animation enables animation bridge and disables movie engine`() {
        val movie=PictureMovieEnginePolicy.forState(PictureContent.ANIMATION,.9f,720)
        val animation=PictureAnimeEnginePolicy.forState(PictureContent.ANIMATION,.9f,720)
        assertEquals(0f,movie.enabled,0f)
        assertEquals(1f,animation.enabled,0f)
    }

    @Test fun `unresolved auto cannot accidentally activate an execution engine`() {
        val movie=PictureMovieEnginePolicy.forState(PictureContent.AUTO,.9f,720)
        val anime=PictureAnimeEnginePolicy.forState(PictureContent.AUTO,.9f,720)
        assertEquals(0f,movie.enabled,0f)
        assertEquals(0f,anime.enabled,0f)
    }
}
