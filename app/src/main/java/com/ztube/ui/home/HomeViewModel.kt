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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hasSubscriptions: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
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
            combine(subscriptionDao.all(), refreshKey) { subs, key -> subs to key }
                .collectLatest { (subs, key) ->
                    val force = key != handledKey
                    handledKey = key
                    if (subs.isEmpty()) {
                        _state.value = HomeUiState(hasSubscriptions = false, loading = false)
                        return@collectLatest
                    }
                    _state.value = _state.value.copy(hasSubscriptions = true, loading = true, error = null)
                    val videos = feed.feed(subs.map { it.channelUrl }, force)
                    _state.value = HomeUiState(
                        videos = videos,
                        loading = false,
                        error = if (videos.isEmpty()) "Couldn't load feed" else null,
                    )
                }
        }
    }

    fun refresh() {
        refreshKey.value++
    }
}
