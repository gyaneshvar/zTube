package com.ztube.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.EmptyState
import com.ztube.ui.components.ErrorState
import com.ztube.ui.components.LoadMoreEffect
import com.ztube.ui.components.LoadingRow
import com.ztube.ui.components.VideoCard
import com.ztube.ui.components.VideoListSkeleton

@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val query = viewModel.query
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val results = state as? SearchUiState.Results

    LaunchedEffect(Unit) {
        if (viewModel.state.value is SearchUiState.Idle && viewModel.query.isEmpty()) runCatching { focusRequester.requestFocus() }
    }

    LoadMoreEffect(
        state = listState,
        enabled = results != null && results.nextPage != null && !results.loadingMore,
        onLoadMore = viewModel::loadMore,
    )

    fun submit(text: String) {
        viewModel.search(text)
        focusManager.clearFocus()
    }

    BackHandler(focused) { focusManager.clearFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search videos") },
            singleLine = true,
            shape = CircleShape,
            leadingIcon = {
                if (focused) {
                    IconButton(onClick = { focusManager.clearFocus() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.onQueryChange("")
                            focusRequester.requestFocus()
                        },
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
        )

        Box(Modifier.fillMaxSize()) {
            when (val s = state) {
                SearchUiState.Idle -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Search videos",
                    subtitle = "Find videos, channels and more",
                )
                SearchUiState.Loading -> Column(Modifier.padding(top = 8.dp)) { VideoListSkeleton() }
                is SearchUiState.Error -> ErrorState(s.message, onRetry = { viewModel.search(query) })
                is SearchUiState.Results -> if (s.videos.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "No results",
                        subtitle = "Try different keywords",
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(s.videos, key = { it.url }) { video ->
                            VideoCard(
                                video,
                                onClick = { onVideoClick(video.url) },
                                onUploaderClick = { onChannelClick(video.uploaderUrl) },
                            )
                        }
                        if (s.loadingMore) item { LoadingRow() }
                    }
                }
            }

            if (focused) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
                    if (query.isBlank()) {
                        RecentList(
                            recents = recents,
                            onClick = ::submit,
                            onRemove = viewModel::removeRecent,
                            onClearAll = viewModel::clearRecents,
                        )
                    } else {
                        SuggestionList(
                            suggestions = suggestions,
                            query = query,
                            onClick = ::submit,
                            onInsert = viewModel::onQueryChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentList(
    recents: List<String>,
    onClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (recents.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearAll) { Text("Clear all") }
                }
            }
        }
        items(recents, key = { it }) { text ->
            SearchRow(
                icon = Icons.Filled.History,
                text = AnnotatedString(text),
                onClick = { onClick(text) },
                trailing = {
                    IconButton(onClick = { onRemove(text) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                    }
                },
            )
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    query: String,
    onClick: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(suggestions, key = { it }) { text ->
            SearchRow(
                icon = Icons.Filled.Search,
                text = highlight(text, query),
                onClick = { onClick(text) },
                trailing = {
                    IconButton(onClick = { onInsert(text) }) {
                        Icon(Icons.Filled.NorthWest, contentDescription = "Use suggestion")
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchRow(
    icon: ImageVector,
    text: AnnotatedString,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        trailing()
    }
}

private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    val typed = query.trim()
    val start = text.indexOf(typed, ignoreCase = true)
    if (typed.isEmpty() || start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    val end = start + typed.length
    append(text.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start, end)) }
    append(text.substring(end))
}
