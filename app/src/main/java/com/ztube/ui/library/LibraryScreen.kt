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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ztube.data.db.DownloadEntity
import com.ztube.download.DownloadStatus
import com.ztube.download.formatSize
import com.ztube.download.thumbnailModel
import com.ztube.ui.components.VideoRow

private val SECTIONS = listOf("History", "Playlists", "Subscriptions", "Downloads", "Settings")

@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp) {
            SECTIONS.forEachIndexed { index, label ->
                Tab(selected = section == index, onClick = { section = index }, text = { Text(label) })
            }
        }
        when (section) {
            0 -> HistorySection(viewModel, onVideoClick)
            1 -> PlaylistsSection(viewModel, onPlaylistClick)
            2 -> SubscriptionsSection(viewModel, onChannelClick)
            3 -> DownloadsSection(viewModel, onVideoClick)
            else -> SettingsSection(viewModel)
        }
    }
}

@Composable
private fun HistorySection(viewModel: LibraryViewModel, onVideoClick: (String) -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No history yet") }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::clearHistory) { Text("Clear all") }
            }
        }
        items(history, key = { it.videoUrl }) { entry ->
            VideoRow(
                title = entry.title,
                subtitle = entry.uploader,
                thumbnailUrl = entry.thumbnailUrl,
                durationSeconds = entry.durationSeconds,
                onClick = { onVideoClick(entry.videoUrl) },
            ) {
                IconButton(onClick = { viewModel.removeHistory(entry.videoUrl) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
private fun DownloadsSection(viewModel: LibraryViewModel, onVideoClick: (String) -> Unit) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No downloads yet") }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(downloads, key = { it.videoUrl }) { download ->
            var menu by rememberSaveable { mutableStateOf(false) }
            val done = download.status == DownloadStatus.DONE
            Column {
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
                                    viewModel.deleteDownload(download.videoUrl)
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
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
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
                    .fillMaxWidth()
                    .clickable { onChannelClick(subscription.channelUrl) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = subscription.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    subscription.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                )
                IconButton(onClick = { viewModel.unsubscribe(subscription.channelUrl) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Unsubscribe")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(viewModel: LibraryViewModel) {
    val sponsorBlock by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()

    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Skip sponsor segments", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = sponsorBlock, onCheckedChange = viewModel::setSponsorBlockEnabled)
    }
}
