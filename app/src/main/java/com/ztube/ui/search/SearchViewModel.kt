package com.ztube.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(
        val videos: List<VideoItem>,
        val nextPage: Page? = null,
        val loadingMore: Boolean = false,
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YoutubeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    var query by mutableStateOf("")
        private set

    val suggestions: StateFlow<List<String>> = snapshotFlow { query.trim() }
        .debounce(SUGGESTION_DEBOUNCE_MS)
        .distinctUntilChanged()
        .mapLatest { text ->
            if (text.isEmpty()) {
                emptyList()
            } else {
                try {
                    repository.suggestions(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<String>> = settings.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var searchedQuery = ""

    fun onQueryChange(value: String) {
        query = value
    }

    fun search(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        searchedQuery = trimmed
        _state.value = SearchUiState.Loading
        searchJob?.cancel()
        moreJob?.cancel()
        searchJob = viewModelScope.launch {
            settings.addRecentSearch(trimmed)
            _state.value = try {
                val first = repository.search(trimmed)
                SearchUiState.Results(first.items, first.nextPage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? SearchUiState.Results ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            try {
                val more = repository.searchMore(searchedQuery, next)
                val latest = _state.value as? SearchUiState.Results ?: return@launch
                val known = latest.videos.mapTo(HashSet()) { it.url }
                _state.value = SearchUiState.Results(
                    videos = latest.videos + more.items.filter { known.add(it.url) },
                    nextPage = more.nextPage,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = _state.value as? SearchUiState.Results ?: return@launch
                _state.value = SearchUiState.Results(latest.videos)
            }
        }
    }

    fun removeRecent(text: String) {
        viewModelScope.launch { settings.removeRecentSearch(text) }
    }

    fun clearRecents() {
        viewModelScope.launch { settings.clearRecentSearches() }
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 300L
    }
}
