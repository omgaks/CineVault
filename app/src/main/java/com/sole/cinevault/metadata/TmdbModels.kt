package com.sole.cinevault.metadata

import com.google.gson.annotations.SerializedName

data class TmdbMovieSearchResponse(val results: List<TmdbMovie>)

data class TmdbMovie(
    val id: Int?,
    val title: String?,
    val release_date: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Double?
)

data class TmdbTvSearchResponse(val results: List<TmdbTvShow>)

data class TmdbTvShow(
    val id: Int?,
    val name: String?,
    val first_air_date: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Double?
)

data class TmdbCreditsResponse(val cast: List<TmdbCastMember>)

data class TmdbCastMember(
    val id: Int? = null,
    val name: String?,
    val character: String?,
    val profile_path: String?
)

data class TmdbEpisode(
    val name: String?,
    val overview: String?,
    val still_path: String?
)

data class TmdbImage(
    val file_path: String?,
    val iso_639_1: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class TmdbImagesResponse(
    val posters: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList()
)

data class TmdbGenre(val id: Int?, val name: String?)

data class TmdbCollection(
    val id: Int?,
    val name: String?,
    val poster_path: String?,
    val backdrop_path: String?
)

data class TmdbCrewMember(
    val id: Int? = null,
    val name: String?,
    val job: String?,
    val profile_path: String?
)

data class TmdbCreatedBy(val id: Int?, val name: String?)
data class TmdbKeyword(val id: Int?, val name: String?)
data class TmdbMovieKeywordsBlock(val keywords: List<TmdbKeyword> = emptyList())
data class TmdbTvKeywordsBlock(val results: List<TmdbKeyword> = emptyList())

data class TmdbCreditsBlock(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList()
)

data class TmdbMovieDetails(
    val id: Int?,
    val title: String?,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val original_language: String? = null,
    val genres: List<TmdbGenre>? = null,
    val belongs_to_collection: TmdbCollection? = null,
    val credits: TmdbCreditsBlock? = null,
    val keywords: TmdbMovieKeywordsBlock? = null
)

data class TmdbTvDetails(
    val id: Int?,
    val name: String?,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val original_language: String? = null,
    val genres: List<TmdbGenre>? = null,
    val created_by: List<TmdbCreatedBy>? = null,
    val credits: TmdbCreditsBlock? = null,
    val keywords: TmdbTvKeywordsBlock? = null
)

// ── Collections V2 ──────────────────────────────────────────────────────────
// Every constructor parameter has a default so Kotlin emits a no-arg
// constructor — Gson then honours the defaults instead of leaving
// non-nullable collections null when a field is absent from the response.
data class TmdbCollectionDetails(
    val id: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val parts: List<TmdbMovie> = emptyList()
)

data class TmdbVideo(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean? = null
)

data class TmdbVideosBlock(val results: List<TmdbVideo> = emptyList())

data class TmdbProvider(val provider_name: String? = null)

data class TmdbRegionProviders(
    val link: String? = null,
    val flatrate: List<TmdbProvider>? = null,
    val rent: List<TmdbProvider>? = null,
    val buy: List<TmdbProvider>? = null
)

data class TmdbWatchProvidersBlock(val results: Map<String, TmdbRegionProviders>? = null)

data class TmdbMovieExtras(
    val id: Int? = null,
    val runtime: Int? = null,
    val overview: String? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val videos: TmdbVideosBlock? = null,
    // The JSON key contains a slash, so it can't be a Kotlin property name.
    @SerializedName("watch/providers") val watchProviders: TmdbWatchProvidersBlock? = null
)

// Slice B — collection search by name (defaults keep Gson's no-arg constructor path safe).
data class TmdbCollectionSummary(val id: Int? = null, val name: String? = null)

data class TmdbCollectionSearchResponse(val results: List<TmdbCollectionSummary> = emptyList())
