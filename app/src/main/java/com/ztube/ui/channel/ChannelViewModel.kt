package com.ztube.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.SubscriptionEntity
import com.ztube.data.extractor.ChannelPage
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import org.schabi.newpipe.extractor.Page
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

data class ShortsUiState(
    val items: List<VideoItem> = emptyList(),
    val nextPage: Page? = null,
    val started: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YoutubeRepository,
    private val subscriptionDao: SubscriptionDao,
) : ViewModel() {

    private val url: String = checkNotNull(savedStateHandle["url"])

    private val _state = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val state: StateFlow<ChannelUiState> = _state.asStateFlow()

    private val _shorts = MutableStateFlow(ShortsUiState())
    val shorts: StateFlow<ShortsUiState> = _shorts.asStateFlow()

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

    fun loadShorts() {
        if (_shorts.value.started && _shorts.value.error == null) return
        _shorts.value = ShortsUiState(started = true, loading = true)
        viewModelScope.launch {
            _shorts.value = try {
                val first = repository.channelShorts(url)
                ShortsUiState(items = first.items, nextPage = first.nextPage, started = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShortsUiState(started = true, error = e.message ?: "Failed to load Shorts")
            }
        }
    }

    fun loadMoreShorts() {
        val current = _shorts.value
        val next = current.nextPage ?: return
        if (current.loadingMore) return
        _shorts.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val more = repository.channelShortsMore(url, next)
                val latest = _shorts.value
                val known = latest.items.mapTo(HashSet()) { it.url }
                _shorts.value = latest.copy(
                    items = latest.items + more.items.filter { known.add(it.url) },
                    nextPage = more.nextPage,
                    loadingMore = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _shorts.value = _shorts.value.copy(nextPage = null, loadingMore = false)
            }
        }
    }

    fun toggleSubscribe() {
        val page = (_state.value as? ChannelUiState.Loaded)?.page ?: return
        viewModelScope.launch {
            if (subscribed.value) {
                subscriptionDao.unsubscribe(url)
            } else {
                subscriptionDao.subscribe(
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
