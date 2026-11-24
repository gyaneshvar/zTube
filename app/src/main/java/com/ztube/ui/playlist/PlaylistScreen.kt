package com.ztube.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.BackTopBar
import com.ztube.ui.components.EmptyState
import com.ztube.ui.components.LocalSnackbar
import com.ztube.ui.components.VideoRow

@Composable
fun PlaylistScreen(
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val readOnly = playlist?.youtubePlaylistId != null

    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            title = playlist?.name.orEmpty(),
            onBack = onBack,
            actions = {
                if (!readOnly) {
                    TextButton(
                        onClick = {
                            viewModel.deletePlaylist { restore ->
                                onDeleted()
                                snackbar.show("Playlist deleted", "Undo", onAction = restore)
                            }
                        },
                    ) { Text("Delete") }
                }
            },
        )
        if (videos.isEmpty()) {
            EmptyState(Icons.Filled.VideoLibrary, "No videos yet", "Add videos from the player")
        }
        LazyColumn {
            items(videos, key = { it.videoUrl }) { video ->
                VideoRow(
                    title = video.title,
                    subtitle = video.uploader,
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    onClick = { onVideoClick(video.videoUrl) },
                    modifier = Modifier.animateItem(),
                ) {
                    if (!readOnly) {
                        IconButton(
                            onClick = {
                                viewModel.removeVideo(video)
                                snackbar.show(
                                    "Removed from playlist",
                                    "Undo",
                                    onAction = { viewModel.restoreVideo(video) },
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}
