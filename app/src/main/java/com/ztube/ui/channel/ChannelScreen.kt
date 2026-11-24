package com.ztube.ui.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.Avatar
import com.ztube.ui.components.BackTopBar
import com.ztube.ui.components.ErrorState
import com.ztube.ui.components.LoadMoreEffect
import com.ztube.ui.components.LoadingRow
import com.ztube.ui.components.SubscribeButton
import com.ztube.ui.components.VideoCard
import com.ztube.ui.components.VideoListSkeleton

@Composable
fun ChannelScreen(
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val loaded = state as? ChannelUiState.Loaded

    LoadMoreEffect(
        state = listState,
        enabled = loaded != null && loaded.page.nextPage != null && !loaded.loadingMore,
        onLoadMore = viewModel::loadMore,
    )

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = loaded?.page?.name.orEmpty(), onBack = onBack)
        when (val s = state) {
            ChannelUiState.Loading -> Column(Modifier.padding(top = 16.dp)) { VideoListSkeleton() }
            is ChannelUiState.Error -> ErrorState(s.message, onRetry = viewModel::load)
            is ChannelUiState.Loaded -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(url = s.page.avatarUrl, name = s.page.name, size = 72.dp)
                        Text(
                            s.page.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        )
                        SubscribeButton(subscribed, viewModel::toggleSubscribe)
                    }
                }
                items(s.page.videos, key = { it.url }) { video ->
                    VideoCard(video, onClick = { onVideoClick(video.url) })
                }
                if (s.loadingMore) item { LoadingRow() }
            }
        }
    }
}
