package com.ztube.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.data.db.DownloadEntity
import com.ztube.download.DownloadStatus
import com.ztube.download.formatSize
import com.ztube.download.thumbnailModel
import com.ztube.ui.components.Avatar
import com.ztube.ui.components.EmptyState
import com.ztube.ui.components.LocalSnackbar
import com.ztube.ui.components.VideoRow
import com.ztube.ui.components.ZTubeTopBar
import kotlinx.coroutines.launch

private val SECTIONS = listOf("History", "Playlists", "Subscriptions", "Downloads")

@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { SECTIONS.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        ZTubeTopBar()
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            SECTIONS.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> HistorySection(viewModel, onVideoClick)
                1 -> PlaylistsSection(viewModel, onPlaylistClick)
                2 -> SubscriptionsSection(viewModel, onChannelClick)
                else -> DownloadsSection(viewModel, onVideoClick)
            }
        }
    }
}

@Composable
private fun HistorySection(viewModel: LibraryViewModel, onVideoClick: (String) -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    if (history.isEmpty()) {
        EmptyState(Icons.Filled.History, "No history yet", "Videos you watch will show up here")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
            }
        }
        items(history, key = { it.videoUrl }) { entry ->
            VideoRow(
                title = entry.title,
                subtitle = entry.uploader,
                thumbnailUrl = entry.thumbnailUrl,
                durationSeconds = entry.durationSeconds,
                onClick = { onVideoClick(entry.videoUrl) },
                modifier = Modifier.animateItem(),
            ) {
                IconButton(
                    onClick = {
                        viewModel.removeHistory(entry)
                        snackbar.show(
                            "Removed from history",
                            "Undo",
                            onAction = { viewModel.restoreHistory(entry) },
                        )
                    },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            text = { Text("This removes all videos from your history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        confirmClear = false
                    },
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DownloadsSection(viewModel: LibraryViewModel, onVideoClick: (String) -> Unit) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current

    if (downloads.isEmpty()) {
        EmptyState(Icons.Filled.Download, "No downloads yet", "Download videos from the player to watch offline")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(downloads, key = { it.videoUrl }) { download ->
            var menu by rememberSaveable { mutableStateOf(false) }
            val done = download.status == DownloadStatus.DONE
            Column(Modifier.animateItem()) {
                VideoRow(
                    title = download.title,
                    subtitle = "${download.uploader}\n${downloadStatus(download)}",
                    thumbnailUrl = download.thumbnailModel(),
                    durationSeconds = download.durationSeconds,
                    onClick = { if (done) onVideoClick(download.videoUrl) },
                ) {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (download.status == DownloadStatus.FAILED) {
                                DropdownMenuItem(
                                    text = { Text("Retry") },
                                    onClick = {
                                        menu = false
                                        viewModel.retryDownload(download)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menu = false
                                    viewModel.hideDownload(download.videoUrl)
                                    snackbar.show(
                                        "Download deleted",
                                        "Undo",
                                        onAction = { viewModel.restoreDownload(download.videoUrl) },
                                        onDismiss = { viewModel.deleteDownload(download.videoUrl) },
                                    )
                                },
                            )
                        }
                    }
                }
                if (download.status == DownloadStatus.RUNNING) {
                    LinearProgressIndicator(
                        progress = {
                            if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes else 0f
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

private fun downloadStatus(download: DownloadEntity): String {
    val size = if (download.totalBytes > 0) formatSize(download.totalBytes) else null
    val state = when (download.status) {
        DownloadStatus.DONE -> null
        DownloadStatus.RUNNING ->
            if (download.totalBytes > 0) "${download.downloadedBytes * 100 / download.totalBytes}%" else "Downloading"
        DownloadStatus.QUEUED -> "Queued"
        else -> "Failed"
    }
    return listOfNotNull(download.quality, size, state).joinToString(" · ")
}

@Composable
private fun PlaylistsSection(viewModel: LibraryViewModel, onPlaylistClick: (Long) -> Unit) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("New playlist")
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            Column(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                    if (playlist.youtubePlaylistId != null) {
                        Text(
                            "YouTube",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "${playlist.videoCount} videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylist(name)
                        creating = false
                    },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SubscriptionsSection(viewModel: LibraryViewModel, onChannelClick: (String) -> Unit) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSubscriptions(uri)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Import subscriptions") }
        }
        items(subscriptions, key = { it.channelUrl }) { subscription ->
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable { onChannelClick(subscription.channelUrl) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = subscription.avatarUrl, name = subscription.name, size = 44.dp)
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
                IconButton(
                    onClick = {
                        viewModel.unsubscribe(subscription)
                        snackbar.show(
                            "Unsubscribed from ${subscription.name}",
                            "Undo",
                            onAction = { viewModel.resubscribe(subscription) },
                        )
                    },
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Unsubscribe")
                }
            }
        }
    }
}
