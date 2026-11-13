package com.sole.cinevault.library

import com.sole.cinevault.VideoWithMetadata

/** One episode TMDB lists for a show. */
data class CatalogEpisode(
    val season: Int,
    val number: Int,
    val name: String,
    val airDate: String?
)

/** (season, episode) pairs the person actually has files for. */
internal fun ownedEpisodeKeys(episodes: List<VideoWithMetadata>): Set<Pair<Int, Int>> =
    episodes.mapNotNull { extractEpisodeInfo(it.video.name)?.let { info -> info.season to info.episode } }.toSet()

/** Episodes that have aired (air date on or before [today], ISO yyyy-MM-dd) and are not in [owned]. Specials excluded. */
internal fun missingEpisodes(
    catalog: List<CatalogEpisode>,
    owned: Set<Pair<Int, Int>>,
    today: String
): List<CatalogEpisode> =
    catalog
        .filter { it.season >= 1 && !it.airDate.isNullOrBlank() && it.airDate <= today }
        .filter { (it.season to it.number) !in owned }
        .sortedWith(compareBy({ it.season }, { it.number }))

/** Number of aired episodes in the catalog (specials excluded). */
internal fun airedEpisodeCount(catalog: List<CatalogEpisode>, today: String): Int =
    catalog.count { it.season >= 1 && !it.airDate.isNullOrBlank() && it.airDate <= today }
