package com.ztube.ui.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.data.extractor.VideoItem
import com.ztube.ui.components.Avatar
import com.ztube.ui.components.BackTopBar
import com.ztube.ui.components.ErrorState
import com.ztube.ui.components.LoadMoreEffect
import com.ztube.ui.components.LoadingRow
import com.ztube.ui.components.ShortCard
import com.ztube.ui.components.SubscribeButton
import com.ztube.ui.components.VideoCard
import com.ztube.ui.components.VideoListSkeleton

private val TABS = listOf("Videos", "Shorts")

@Composable
fun ChannelScreen(
    onVideoClick: (String) -> Unit,
    onShortClick: (List<VideoItem>, Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shorts by viewModel.shorts.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val loaded = state as? ChannelUiState.Loaded

    LaunchedEffect(tab) {
        if (tab == 1) viewModel.loadShorts()
    }
    LoadMoreEffect(
        state = listState,
        enabled = tab == 0 && loaded != null && loaded.page.nextPage != null && !loaded.loadingMore,
        onLoadMore = viewModel::loadMore,
    )
    LoadMoreEffect(
        state = gridState,
        enabled = tab == 1 && shorts.nextPage != null && !shorts.loadingMore,
        onLoadMore = viewModel::loadMoreShorts,
    )

    Column(Modifier.fillMaxSize()) {
        BackTopBar(title = loaded?.page?.name.orEmpty(), onBack = onBack)
        when (val s = state) {
            ChannelUiState.Loading -> Column(Modifier.padding(top = 16.dp)) { VideoListSkeleton() }
            is ChannelUiState.Error -> ErrorState(s.message, onRetry = viewModel::load)
            is ChannelUiState.Loaded -> {
                val header: @Composable () -> Unit = {
                    Column {
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
                        PrimaryTabRow(
                            selectedTabIndex = tab,
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            TABS.forEachIndexed { index, label ->
                                Tab(
                                    selected = tab == index,
                                    onClick = {
                                        tab = index
                                    },
                                    text = { Text(label) },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (tab == 0) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item { header() }
                        items(s.page.videos, key = { it.url }) { video ->
                            VideoCard(video, onClick = { onVideoClick(video.url) })
                        }
                        if (s.loadingMore) item { LoadingRow() }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            header()
                        }
                        if (shorts.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
                        }
                        shorts.error?.let { message ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(message, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = viewModel::loadShorts) { Text("Retry") }
                                }
                            }
                        }
                        if (shorts.started && !shorts.loading && shorts.error == null && shorts.items.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "No Shorts",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        itemsIndexed(shorts.items, key = { _, short -> short.url }) { index, short ->
                            ShortCard(
                                short,
                                onClick = { onShortClick(shorts.items, index) },
                                modifier = Modifier.padding(horizontal = 6.dp),
                            )
                        }
                        if (shorts.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow() }
                        }
                    }
                }
            }
        }
    }
}
