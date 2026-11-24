package com.ztube.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import com.ztube.data.db.DownloadEntity
import com.ztube.data.db.PlaylistSummary
import com.ztube.download.DownloadOption
import com.ztube.download.DownloadStatus
import com.ztube.download.formatSize

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private var notificationAsked = false

private enum class Sheet { Speed, Quality, Playlist, Download }

@Composable
fun PlayerScreen(
    onChannelClick: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val inPip by viewModel.inPip.collectAsStateWithLifecycle()
    val subscribed by viewModel.subscribed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val downloadOptions by viewModel.downloadOptions.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var newPlaylist by rememberSaveable { mutableStateOf(false) }
    val videoOnly = inPip || fullscreen

    val context = LocalContext.current
    val view = LocalView.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(Unit) {
        if (notificationAsked) return@LaunchedEffect
        notificationAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(fullscreen) {
        val activity = context.findActivity()
        val insets = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insets?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        viewModel.setVisible(true)
        onDispose { viewModel.setVisible(false) }
    }

    BackHandler(fullscreen) { fullscreen = false }

    Column(
        Modifier.fillMaxSize().then(if (videoOnly) Modifier else Modifier.systemBarsPadding()),
    ) {
        Box(
            (if (videoOnly) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        setFullscreenButtonClickListener { fullscreen = it }
                    }
                },
                update = {
                    it.player = controller
                    it.useController = !inPip
                    it.setFullscreenButtonState(fullscreen)
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            if (controller == null && state !is PlayerUiState.Error) CircularProgressIndicator()
            playbackError?.let { message ->
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
            }
        }

        if (!videoOnly) {
            when (val s = state) {
                PlayerUiState.Loading -> Unit
                is PlayerUiState.Loaded -> Details(
                    s,
                    speed,
                    subscribed,
                    download,
                    canDownload = downloadOptions.isNotEmpty(),
                    onDownload = { sheet = Sheet.Download },
                    onSpeed = { sheet = Sheet.Speed },
                    onQuality = { sheet = Sheet.Quality },
                    onPlaylist = { sheet = Sheet.Playlist },
                    onSubscribe = viewModel::toggleSubscribe,
                    onChannelClick = { onChannelClick(s.uploaderUrl) },
                )
                is PlayerUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    val loaded = state as? PlayerUiState.Loaded
    when (sheet) {
        Sheet.Speed -> OptionSheet(
            options = SPEEDS,
            selected = speed,
            label = ::speedLabel,
            onSelect = viewModel::setSpeed,
            onDismiss = { sheet = null },
        )
        Sheet.Quality -> if (loaded != null) OptionSheet(
            options = loaded.heights.reversed(),
            selected = loaded.quality,
            label = { "${it}p" },
            onSelect = viewModel::setQuality,
            onDismiss = { sheet = null },
        )
        Sheet.Playlist -> PlaylistSheet(
            playlists = playlists,
            onSelect = viewModel::addToPlaylist,
            onNew = { newPlaylist = true },
            onDismiss = { sheet = null },
        )
        Sheet.Download -> DownloadSheet(
            download = download,
            options = downloadOptions,
            onStart = viewModel::startDownload,
            onDelete = viewModel::deleteDownload,
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    if (newPlaylist) {
        NewPlaylistDialog(
            onCreate = viewModel::createPlaylist,
            onDismiss = { newPlaylist = false },
        )
    }
}

@Composable
private fun Details(
    state: PlayerUiState.Loaded,
    speed: Float,
    subscribed: Boolean,
    download: DownloadEntity?,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onSpeed: () -> Unit,
    onQuality: () -> Unit,
    onPlaylist: () -> Unit,
    onSubscribe: () -> Unit,
    onChannelClick: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(state.title, style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.viewCount >= 0) "${state.uploader} · ${"%,d".format(state.viewCount)} views" else state.uploader,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = state.uploaderUrl.isNotEmpty(), onClick = onChannelClick),
            )
            if (state.uploaderUrl.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onSubscribe) {
                    Text(if (subscribed) "Subscribed" else "Subscribe")
                }
            }
        }
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = onSpeed, label = { Text(speedLabel(speed)) })
            AssistChip(
                onClick = onQuality,
                enabled = state.heights.isNotEmpty(),
                label = { Text(if (state.heights.isEmpty()) "Auto" else "${state.quality}p") },
            )
            AssistChip(onClick = onPlaylist, label = { Text("Playlist") })
            AssistChip(
                onClick = onDownload,
                enabled = canDownload || download != null,
                label = { Text(downloadLabel(download)) },
            )
        }
        Text(
            state.description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            modifier = Modifier.clickable { expanded = !expanded },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(label(option), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSheet(
    download: DownloadEntity?,
    options: List<DownloadOption>,
    onStart: (DownloadOption) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            when (download?.status) {
                DownloadStatus.DONE -> {
                    Text("Downloaded (${download.quality})", modifier = Modifier.padding(16.dp))
                    Text(
                        "Delete download",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDelete()
                                onDismiss()
                            }
                            .padding(16.dp),
                    )
                }
                DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
                    Text("${downloadLabel(download)} (${download.quality})", modifier = Modifier.padding(16.dp))
                    Text(
                        "Cancel download",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDelete()
                                onDismiss()
                            }
                            .padding(16.dp),
                    )
                }
                else -> {
                    if (download != null) {
                        Text(
                            "Download failed, pick a quality to retry",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    options.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStart(option)
                                    onDismiss()
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(option.label)
                            if (option.sizeBytes > 0) Text("~${formatSize(option.sizeBytes)}")
                        }
                    }
                }
            }
        }
    }
}

private fun downloadLabel(download: DownloadEntity?): String = when (download?.status) {
    DownloadStatus.DONE -> "Downloaded"
    DownloadStatus.QUEUED -> "Queued"
    DownloadStatus.RUNNING ->
        if (download.totalBytes > 0) "${download.downloadedBytes * 100 / download.totalBytes}%" else "Downloading"
    else -> "Download"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    playlists: List<PlaylistSummary>,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                "New playlist",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onNew()
                        onDismiss()
                    }
                    .padding(16.dp),
            )
            playlists.forEach { playlist ->
                Text(
                    "${playlist.name} (${playlist.videoCount})",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(playlist.id)
                            onDismiss()
                        }
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onCreate(name)
                    onDismiss()
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun speedLabel(speed: Float) =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
