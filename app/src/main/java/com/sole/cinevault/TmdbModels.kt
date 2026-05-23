package com.sole.cinevault

data class TmdbMovieSearchResponse(
    val results: List<TmdbMovie>
)

data class TmdbMovie(
    val id: Int?,
    val title: String?,
    val release_date: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Double?
)

data class TmdbTvSearchResponse(
    val results: List<TmdbTvShow>
)

data class TmdbTvShow(
    val id: Int?,
    val name: String?,
    val first_air_date: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val overview: String?,
    val vote_average: Double?
)

data class TmdbCreditsResponse(
    val cast: List<TmdbCastMember>
)

data class TmdbCastMember(
    val name: String?,
    val character: String?,
    val profile_path: String?
)
data class TmdbEpisode(
    val name: String?,
    val overview: String?,
    val still_path: String?
)