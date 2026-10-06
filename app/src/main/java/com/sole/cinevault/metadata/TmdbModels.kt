package com.sole.cinevault.metadata

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
