package com.ztube.ui.shorts

import com.ztube.data.extractor.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortsQueue @Inject constructor() {
    var items: List<VideoItem> = emptyList()
    var startIndex: Int = 0

    private val _activeUrl = MutableStateFlow<String?>(null)
    val activeUrl: StateFlow<String?> = _activeUrl.asStateFlow()

    fun set(items: List<VideoItem>, startIndex: Int) {
        val tappedUrl = items.getOrNull(startIndex)?.url
        this.items = items.distinctBy { it.url }
        this.startIndex = this.items.indexOfFirst { it.url == tappedUrl }.coerceAtLeast(0)
    }

    fun setActive(url: String?) {
        _activeUrl.value = url
    }
}
