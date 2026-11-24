package com.ztube.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.EmptyState
import com.ztube.ui.components.ErrorState
import com.ztube.ui.components.VideoCard
import com.ztube.ui.components.VideoListSkeleton
import com.ztube.ui.components.ZTubeTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ZTubeTopBar {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
        if (!state.hasSubscriptions) {
            EmptyState(
                icon = Icons.Filled.Subscriptions,
                title = "Your feed is empty",
                subtitle = "Subscribe to channels to build your feed",
                action = {
                    Button(onClick = onOpenSearch) { Text("Find videos") }
                },
            )
            return@Column
        }
        PullToRefreshBox(
            isRefreshing = state.loading && state.videos.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val error = state.error
            when {
                state.videos.isEmpty() && state.loading -> Column(Modifier.padding(top = 8.dp)) { VideoListSkeleton() }
                state.videos.isEmpty() && error != null -> ErrorState(error, onRetry = viewModel::refresh)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.videos, key = { it.url }) { video ->
                        VideoCard(
                            video,
                            onClick = { onVideoClick(video.url) },
                            onUploaderClick = { onChannelClick(video.uploaderUrl) },
                        )
                    }
                }
            }
        }
    }
}
