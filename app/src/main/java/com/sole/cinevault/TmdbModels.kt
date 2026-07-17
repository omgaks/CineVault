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
    // id wasn't being captured before — it's already in every TMDB cast
    // response, just unused. Needed as a stable key for actor pages (name
    // alone risks collisions between different real people).
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

// ── Added for media intelligence: genres, collections, director/crew ──────────
// The plain /search/movie and /search/tv endpoints only return raw
// genre_ids (numbers, no names) and nothing about collections or crew at
// all. These back the richer /movie/{id} and /tv/{id} "details" endpoints
// instead, which return proper genre names, belongs_to_collection, and
// (via append_to_response=credits) full cast+crew in a single extra call.

data class TmdbGenre(
    val id: Int?,
    val name: String?
)

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

data class TmdbCreatedBy(
    val id: Int?,
    val name: String?
)

// Keywords back curated collections (e.g. "Marvel Cinematic Universe") without
// hardcoding a movie-ID list — TMDB tags official MCU films with a keyword,
// so matching by keyword NAME self-updates as new films are tagged, instead
// of going stale the moment a new movie releases.
// NOTE: movie and TV keyword responses have different shapes from TMDB
// ("keywords" array vs "results" array) — hence two block types.
data class TmdbKeyword(
    val id: Int?,
    val name: String?
)

data class TmdbMovieKeywordsBlock(
    val keywords: List<TmdbKeyword> = emptyList()
)

data class TmdbTvKeywordsBlock(
    val results: List<TmdbKeyword> = emptyList()
)

// Embedded via append_to_response=credits on the details endpoints — avoids
// a second network round-trip just to get cast/crew.
data class TmdbCreditsBlock(
    val cast: List<TmdbCastMember> = emptyList(),
    val crew: List<TmdbCrewMember> = emptyList()
)

data class TmdbMovieDetails(
    val id: Int?,
    val title: String?,
    val genres: List<TmdbGenre>? = null,
    val belongs_to_collection: TmdbCollection? = null,
    val credits: TmdbCreditsBlock? = null,
    val keywords: TmdbMovieKeywordsBlock? = null
)

data class TmdbTvDetails(
    val id: Int?,
    val name: String?,
    val genres: List<TmdbGenre>? = null,
    // TV shows don't have a single per-show "Director" the way movies do
    // (different episodes can have different directors) — created_by (the
    // showrunner/creator) is TMDB's standard equivalent for this purpose.
    val created_by: List<TmdbCreatedBy>? = null,
    val credits: TmdbCreditsBlock? = null,
    val keywords: TmdbTvKeywordsBlock? = null
)
