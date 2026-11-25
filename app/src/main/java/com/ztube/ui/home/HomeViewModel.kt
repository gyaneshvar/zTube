package com.ztube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.extractor.VideoItem
import com.ztube.data.feed.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hasSubscriptions: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val shorts: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val feed: FeedRepository,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)
    private val pulling = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(
        subscriptionDao.all(),
        combine(feed.videos, feed.shorts) { videos, shorts -> videos to shorts },
        feed.refreshing,
        pulling,
        error,
    ) { subs, (videos, shorts), loading, pulling, error ->
        HomeUiState(
            hasSubscriptions = subs.isNotEmpty(),
            videos = videos,
            shorts = shorts,
            loading = loading,
            refreshing = pulling && loading,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            subscriptionDao.all()
                .map { subs -> subs.map { it.channelUrl }.toSet() }
                .distinctUntilChanged()
                .collectLatest { applyResult(feed.refresh(force = false)) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            pulling.value = true
            try {
                applyResult(feed.refresh(force = true))
            } finally {
                pulling.value = false
            }
        }
    }

    private fun applyResult(ok: Boolean?) {
        if (ok != null) error.value = if (ok) null else FEED_ERROR
    }

    private companion object {
        const val FEED_ERROR = "Couldn't load feed"
    }
}
