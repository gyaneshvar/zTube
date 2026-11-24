package com.ztube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.extractor.VideoItem
import com.ztube.data.feed.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hasSubscriptions: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    subscriptionDao: SubscriptionDao,
    private val feed: FeedRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val refreshKey = MutableStateFlow(0)
    private var handledKey = 0

    init {
        viewModelScope.launch {
            val channelUrls = subscriptionDao.all()
                .map { subs -> subs.map { it.channelUrl }.toSet() }
                .distinctUntilChanged()
            combine(channelUrls, refreshKey) { urls, key -> urls to key }
                .collectLatest { (urls, key) ->
                    val force = key != handledKey
                    handledKey = key
                    if (urls.isEmpty()) {
                        _state.value = HomeUiState(hasSubscriptions = false, loading = false)
                        return@collectLatest
                    }
                    _state.value = _state.value.copy(
                        hasSubscriptions = true,
                        loading = true,
                        refreshing = force,
                        error = null,
                    )
                    var first = true
                    feed.feed(urls.toList(), force).collect { videos ->
                        _state.value = _state.value.copy(videos = videos, refreshing = false)
                        first = false
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = if (first) "Couldn't load feed" else null,
                    )
                }
        }
    }

    fun refresh() {
        refreshKey.value++
    }
}
