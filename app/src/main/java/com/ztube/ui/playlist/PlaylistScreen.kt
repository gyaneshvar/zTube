package com.ztube.ui.playlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Column
import com.ztube.ui.components.VideoRow

@Composable
fun PlaylistScreen(
    onVideoClick: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                playlist?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.deletePlaylist(onDeleted) }) { Text("Delete") }
        }
        if (videos.isEmpty()) {
            Text("No videos yet", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(videos, key = { it.videoUrl }) { video ->
                VideoRow(
                    title = video.title,
                    subtitle = video.uploader,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    onClick = { onVideoClick(video.videoUrl) },
                ) {
                    IconButton(onClick = { viewModel.removeVideo(video.videoUrl) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                    }
                }
            }
        }
    }
}
