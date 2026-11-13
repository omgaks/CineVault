package com.sole.cinevault.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingEpisodesTest {
    private val catalog = listOf(
        CatalogEpisode(0, 1, "Special", "2020-01-01"),
        CatalogEpisode(1, 1, "One", "2024-01-01"),
        CatalogEpisode(1, 2, "Two", "2024-01-08"),
        CatalogEpisode(1, 3, "Three", "2024-01-15"),
        CatalogEpisode(2, 1, "Future", "2999-01-01"),
        CatalogEpisode(2, 2, "Unknown date", null)
    )

    @Test
    fun listsOnlyAiredEpisodesWeDoNotHave() {
        val missing = missingEpisodes(catalog, setOf(1 to 2), "2025-01-01")
        assertEquals(listOf(1 to 1, 1 to 3), missing.map { it.season to it.number })
    }

    @Test
    fun nothingMissingWhenAllAiredOwned() {
        assertEquals(emptyList<CatalogEpisode>(), missingEpisodes(catalog, setOf(1 to 1, 1 to 2, 1 to 3), "2025-01-01"))
    }

    @Test
    fun airedCountSkipsSpecialsAndFuture() {
        assertEquals(3, airedEpisodeCount(catalog, "2025-01-01"))
    }
}
