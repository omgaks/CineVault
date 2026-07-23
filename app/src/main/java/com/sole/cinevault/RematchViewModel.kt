package com.sole.cinevault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * RematchViewModel
 *
 * Powers a manual "Fix Match" flow: user types a search query, we hit TMDB,
 * show candidates, user taps one, we re-fetch full details and overwrite
 * the stored metadata for that video.
 *
 * INTEGRATION NOTES (adjust to your actual class/method names):
 *  - `tmdbClient` below should be whatever client you already call for the
 *    append_to_response=credits,keywords fetch in your media intelligence
 *    work. Swap in the real type + method names.
 *  - `metadataStore` should be whatever persists VideoWithMetadata (Room DAO,
 *    repository, etc). Swap in the real type + method names.
 *  - `MatchCandidate` mirrors whatever your TMDB search response model
 *    already looks like — trim/rename fields to match it instead of
 *    duplicating a parallel model if you already have one.
 */

data class MatchCandidate(
    val tmdbId: Int,
    val title: String,
    val releaseYear: Int?,
    val posterPath: String?,   // relative TMDB path, e.g. "/abc123.jpg"
    val overview: String?
)

sealed class RematchUiState {
    data object Idle : RematchUiState()
    data object Loading : RematchUiState()
    data class Results(val candidates: List<MatchCandidate>) : RematchUiState()
    data object Empty : RematchUiState()
    data class Error(val message: String) : RematchUiState()
    data object Applying : RematchUiState()
    data object Applied : RematchUiState()
}

class RematchViewModel(
    private val videoId: String,
    private val tmdbClient: TmdbClientContract,
    private val metadataStore: MetadataStoreContract
) : ViewModel() {

    private val _uiState = MutableStateFlow<RematchUiState>(RematchUiState.Idle)
    val uiState: StateFlow<RematchUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = RematchUiState.Loading
            try {
                val results = tmdbClient.searchMovies(q)
                _uiState.value = if (results.isEmpty()) {
                    RematchUiState.Empty
                } else {
                    RematchUiState.Results(results)
                }
            } catch (e: Exception) {
                _uiState.value = RematchUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun applyMatch(candidate: MatchCandidate) {
        viewModelScope.launch {
            _uiState.value = RematchUiState.Applying
            try {
                // Re-fetch full details with credits+keywords, same as your
                // existing single-call enrichment pattern.
                val fullDetails = tmdbClient.getMovieDetails(
                    tmdbId = candidate.tmdbId,
                    appendToResponse = "credits,keywords"
                )
                metadataStore.overwriteMetadata(videoId, fullDetails)
                _uiState.value = RematchUiState.Applied
            } catch (e: Exception) {
                _uiState.value = RematchUiState.Error(e.message ?: "Failed to apply match")
            }
        }
    }

    fun reset() {
        _uiState.value = RematchUiState.Idle
        _query.value = ""
    }
}

/**
 * Thin contracts so this file compiles standalone as a sketch.
 * Delete these and point at your real TMDB client / metadata store types.
 */
interface TmdbClientContract {
    suspend fun searchMovies(query: String): List<MatchCandidate>
    suspend fun getMovieDetails(tmdbId: Int, appendToResponse: String): Any // -> your VideoWithMetadata-producing type
}

interface MetadataStoreContract {
    suspend fun overwriteMetadata(videoId: String, details: Any)
}
