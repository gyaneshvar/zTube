package com.ztube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val videos: List<VideoItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YoutubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = SearchUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                SearchUiState.Results(repository.search(query.trim()))
            } catch (e: Exception) {
                SearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }
}
