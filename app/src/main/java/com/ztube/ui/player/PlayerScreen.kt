package com.ztube.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.ztube.R
import com.ztube.data.extractor.VideoItem
import com.ztube.ui.components.Avatar
import com.ztube.ui.components.ErrorState
import com.ztube.ui.components.SubscribeButton
import com.ztube.ui.components.VideoRow
import com.ztube.ui.components.VideoRowSkeleton
import com.ztube.ui.components.formatCount
import com.ztube.ui.components.shareVideo
import com.ztube.ui.components.shimmer
import com.ztube.ui.components.videoMeta
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ztube.ui.components.rememberImageRequest
import com.ztube.data.db.DownloadEntity
import com.ztube.data.db.PlaylistSummary
import com.ztube.download.DownloadOption
import com.ztube.download.DownloadStatus
import com.ztube.download.formatSize

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private val SEEK_MS = 10_000L
private val CONTROLS_HEIGHT = 54.dp

private var notificationAsked = false

private enum class Sheet { Speed, Quality, Playlist, Download }

@Composable
fun PlayerScreen(
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
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
    val related by viewModel.related.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playing.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<Sheet?>(null) }
    var newPlaylist by rememberSaveable { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val videoOnly = inPip || fullscreen || landscape
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var controllerVisible by remember { mutableStateOf(false) }
    val firstFrameRendered = rememberFirstFrameRendered(controller)
    val darkTheme = isSystemInDarkTheme()

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
        if (fullscreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(videoOnly) {
        val activity = context.findActivity()
        val insets = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        if (videoOnly) {
            insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insets?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    DisposableEffect(darkTheme) {
        val activity = context.findActivity()
        val insets = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        insets?.isAppearanceLightStatusBars = false
        onDispose { insets?.isAppearanceLightStatusBars = !darkTheme }
    }

    DisposableEffect(Unit) {
        viewModel.setVisible(true)
        onDispose { viewModel.setVisible(false) }
    }

    BackHandler(fullscreen) { fullscreen = false }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val target = player.currentPosition + deltaMs
        val duration = player.duration
        player.seekTo(if (duration == C.TIME_UNSET) target.coerceAtLeast(0) else target.coerceIn(0, duration))
    }

    fun toggleController() {
        val view = playerView ?: return
        if (view.isControllerFullyVisible) view.hideController() else view.showController()
    }

    Column(
        Modifier.fillMaxSize().then(if (videoOnly) Modifier else Modifier.navigationBarsPadding()),
    ) {
        if (!videoOnly) {
            Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Color.Black))
        }
        Box(
            (if (videoOnly) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setFullscreenButtonClickListener { fullscreen = it }
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { controllerVisible = it == View.VISIBLE },
                        )
                    }
                },
                update = {
                    playerView = it
                    it.player = controller
                    it.useController = !inPip
                    it.keepScreenOn = isPlaying
                    it.setFullscreenButtonState(fullscreen)
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            val thumbnail = (state as? PlayerUiState.Loaded)?.thumbnail
            if (!firstFrameRendered && thumbnail != null) {
                AsyncImage(
                    model = rememberImageRequest(thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!firstFrameRendered && playbackError == null && state !is PlayerUiState.Error) {
                CircularProgressIndicator()
            }
            if (!inPip) {
                Row(
                    Modifier.matchParentSize().padding(bottom = if (controllerVisible) CONTROLS_HEIGHT else 0.dp),
                ) {
                    SeekZone(
                        forward = false,
                        onSeek = { seekBy(-SEEK_MS) },
                        onTap = ::toggleController,
                        modifier = Modifier.weight(0.2f).fillMaxHeight(),
                    )
                    Spacer(Modifier.weight(0.6f))
                    SeekZone(
                        forward = true,
                        onSeek = { seekBy(SEEK_MS) },
                        onTap = ::toggleController,
                        modifier = Modifier.weight(0.2f).fillMaxHeight(),
                    )
                }
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
            if (!videoOnly && (controllerVisible || state !is PlayerUiState.Loaded)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize")
                }
            }
        }

        if (!videoOnly) {
            when (val s = state) {
                PlayerUiState.Loading -> LoadingDetails()
                is PlayerUiState.Loaded -> Details(
                    s,
                    speed,
                    subscribed,
                    download,
                    related,
                    autoplay,
                    canDownload = downloadOptions.isNotEmpty(),
                    onDownload = { sheet = Sheet.Download },
                    onSpeed = { sheet = Sheet.Speed },
                    onQuality = { sheet = Sheet.Quality },
                    onPlaylist = { sheet = Sheet.Playlist },
                    onShare = {
                        val url = viewModel.videoUrl
                        shareVideo(context, url, s.title, "/shorts/" in url)
                    },
                    onSubscribe = viewModel::toggleSubscribe,
                    onChannelClick = { onChannelClick(s.uploaderUrl) },
                    onAutoplay = viewModel::setAutoplay,
                    onRelatedClick = viewModel::playVideo,
                )
                is PlayerUiState.Error -> ErrorState(
                    s.message,
                    onRetry = viewModel::reload,
                    onOpenInYoutube = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.videoUrl)))
                        }
                    },
                )
            }
        }
    }

    val loaded = state as? PlayerUiState.Loaded
    if (!inPip) {
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
}

@Composable
private fun LoadingDetails() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(0.9f).height(20.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Box(Modifier.width(90.dp).height(32.dp).clip(CircleShape).shimmer()) }
        }
    }
}

@Composable
private fun Details(
    state: PlayerUiState.Loaded,
    speed: Float,
    subscribed: Boolean,
    download: DownloadEntity?,
    related: List<VideoItem>?,
    autoplay: Boolean,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onSpeed: () -> Unit,
    onQuality: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onSubscribe: () -> Unit,
    onChannelClick: () -> Unit,
    onAutoplay: (Boolean) -> Unit,
    onRelatedClick: (String) -> Unit,
) {
    var titleExpanded by rememberSaveable { mutableStateOf(false) }
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val uriHandler = LocalUriHandler.current
    val openLink = remember(uriHandler) {
        LinkInteractionListener { link ->
            runCatching { uriHandler.openUri((link as LinkAnnotation.Url).url) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { titleExpanded = !titleExpanded }
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(),
                )
                val meta = listOfNotNull(
                    state.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                    state.uploadedText?.takeIf { it.isNotEmpty() },
                    state.likeCount.takeIf { it >= 0 }?.let { "${formatCount(it)} likes" },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionChip(Icons.Filled.Speed, speedLabel(speed), onSpeed)
                ActionChip(
                    Icons.Filled.HighQuality,
                    if (state.heights.isEmpty()) "Auto" else "${state.quality}p",
                    onQuality,
                    enabled = state.heights.isNotEmpty(),
                )
                ActionChip(
                    if (download?.status == DownloadStatus.DONE) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    downloadLabel(download),
                    onDownload,
                    enabled = canDownload || download != null,
                )
                ActionChip(Icons.AutoMirrored.Filled.PlaylistAdd, "Save", onPlaylist)
                ActionChip(Icons.Outlined.Share, "Share", onShare)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onChannelClick,
                    enabled = state.uploaderUrl.isNotEmpty(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(url = state.uploaderAvatarUrl, name = state.uploader, size = 40.dp)
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            Text(
                                state.uploader,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.subscriberCount >= 0) {
                                Text(
                                    "${formatCount(state.subscriberCount)} subscribers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.uploaderUrl.isNotEmpty()) SubscribeButton(subscribed, onSubscribe)
            }
        }
        if (state.description.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateContentSize()
                        .clickable { descriptionExpanded = !descriptionExpanded },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Description", style = MaterialTheme.typography.titleSmall)
                        val description = remember(state.description, state.descriptionIsHtml, linkStyle, openLink) {
                            if (state.descriptionIsHtml) {
                                AnnotatedString.fromHtml(state.description, linkStyles = linkStyle, linkInteractionListener = openLink)
                            } else {
                                AnnotatedString(state.description)
                            }
                        }
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            if (descriptionExpanded) "Show less" else "Show more",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Up next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Autoplay",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(checked = autoplay, onCheckedChange = onAutoplay)
            }
        }
        if (related == null) {
            items(4) { VideoRowSkeleton() }
        } else {
            items(related, key = { it.url }) { video ->
                VideoRow(
                    title = video.title,
                    subtitle = videoMeta(video),
                    thumbnailUrl = video.thumbnailUrl,
                    durationSeconds = video.durationSeconds,
                    onClick = { onRelatedClick(video.url) },
                )
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = null,
    )
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun rememberFirstFrameRendered(player: Player?): Boolean {
    var rendered by remember(player) {
        mutableStateOf(
            player != null && player.playbackState == Player.STATE_READY && player.videoSize != VideoSize.UNKNOWN,
        )
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                rendered = true
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) rendered = true
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                rendered = false
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return rendered
}
