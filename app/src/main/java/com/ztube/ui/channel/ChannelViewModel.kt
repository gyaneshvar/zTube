package com.ztube.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.SubscriptionEntity
import com.ztube.data.extractor.ChannelPage
import com.ztube.data.extractor.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelUiState {
    data object Loading : ChannelUiState
    data class Loaded(val page: ChannelPage, val loadingMore: Boolean = false) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YoutubeRepository,
    private val subscriptionDao: SubscriptionDao,
) : ViewModel() {

    private val url: String = checkNotNull(savedStateHandle["url"])

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    val subscribed: StateFlow<Boolean> = subscriptionDao.isSubscribed(url)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    fun load() {
        _state.value = ChannelUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ChannelUiState.Loaded(repository.channel(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChannelUiState.Error(e.message ?: "Failed to load channel")
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? ChannelUiState.Loaded ?: return
        val next = current.page.nextPage ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val more = repository.channelUploadsMore(url, next)
                val latest = _state.value as? ChannelUiState.Loaded ?: return@launch
                val known = latest.page.videos.mapTo(HashSet()) { it.url }
                _state.value = ChannelUiState.Loaded(
                    latest.page.copy(
                        videos = latest.page.videos + more.items.filter { known.add(it.url) },
                        nextPage = more.nextPage,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val latest = _state.value as? ChannelUiState.Loaded ?: return@launch
                _state.value = ChannelUiState.Loaded(latest.page.copy(nextPage = null))
            }
        }
    }

    fun toggleSubscribe() {
        val page = (_state.value as? ChannelUiState.Loaded)?.page ?: return
        viewModelScope.launch {
            if (subscribed.value) {
                subscriptionDao.delete(url)
            } else {
                subscriptionDao.insert(
                    SubscriptionEntity(
                        channelUrl = url,
                        name = page.name,
                        avatarUrl = page.avatarUrl,
                        subscribedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
