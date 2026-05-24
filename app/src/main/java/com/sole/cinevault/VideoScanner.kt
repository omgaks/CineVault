package com.sole.cinevault

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun scanDeviceVideos(
    context: Context
): List<VideoWithMetadata> = withContext(Dispatchers.IO) {

    val videoList = mutableListOf<VideoWithMetadata>()

    val projection = arrayOf(
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DISPLAY_NAME
    )

    val cursor = context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC"
    )

    cursor?.use {

        val pathColumn =
            it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

        val nameColumn =
            it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

        while (it.moveToNext()) {

            val path =
                it.getString(pathColumn) ?: continue

            val name =
                it.getString(nameColumn)
                    ?: path.substringAfterLast("/")

            val cleanedTitle =
                cleanMovieFilename(name)

            // ALWAYS create local fallback first
            var videoItem = VideoWithMetadata(
                video = VideoFile(
                    name = name,
                    path = path
                ),
                title = cleanedTitle,
                subtitle = "Movie",
                posterUrl = null,
                backdropUrl = null,
                overview = "",
                rating = 0.0,
                imdbRating = null,
                rottenTomatoesRating = null,
                tmdbId = null,
                type = "movie"
            )

            // THEN try TMDB enrichment
            try {

                val tmdb =
                    TmdbClient.api.searchMovie(
                        bearerToken = BuildConfig.TMDB_TOKEN,
                        query = cleanedTitle
                    )

                val result =
                    tmdb.results.firstOrNull()

                if (result != null) {

                    videoItem = videoItem.copy(
                        title = result.title ?: cleanedTitle,
                        posterUrl =
                            result.poster_path?.let {
                                "https://image.tmdb.org/t/p/w500$it"
                            },
                        backdropUrl =
                            result.backdrop_path?.let {
                                "https://image.tmdb.org/t/p/original$it"
                            },
                        overview = result.overview,
                        rating = result.vote_average,
                        tmdbId = result.id
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            videoList.add(videoItem)
        }
    }

    videoList
}