package com.ztube.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun LoadMoreEffect(state: LazyListState, enabled: Boolean, buffer: Int = 5, onLoadMore: () -> Unit) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - buffer
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentLoadMore() }
    }
}

@Composable
fun LoadMoreEffect(state: LazyGridState, enabled: Boolean, buffer: Int = 6, onLoadMore: () -> Unit) {
    val currentLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 1 - buffer
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentLoadMore() }
    }
}
