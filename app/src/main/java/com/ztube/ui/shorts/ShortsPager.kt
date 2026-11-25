package com.ztube.ui.shorts

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ztube.R
import com.ztube.data.extractor.VideoItem
import com.ztube.ui.components.rememberImageRequest
import com.ztube.ui.components.shareVideo
import com.ztube.ui.player.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ShortsPager(
    items: List<VideoItem>,
    startIndex: Int,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = hiltViewModel(),
) {
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val playWhenReady by viewModel.playWhenReady.collectAsStateWithLifecycle()
    val buffering by viewModel.buffering.collectAsStateWithLifecycle()
    val renderedUrl by viewModel.renderedUrl.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, maxOf(items.lastIndex, 0))) { items.size }
    val settled = items.getOrNull(pagerState.settledPage)
    val next = items.getOrNull(pagerState.settledPage + 1)
    val context = LocalContext.current
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()

    LaunchedEffect(settled?.url) {
        if (settled != null) viewModel.play(settled, next)
    }

    LaunchedEffect(pagerState) {
        viewModel.advance.collect { url ->
            val settled = pagerState.settledPage
            if (items.getOrNull(settled)?.url == url && settled + 1 < items.size) {
                viewModel.pause()
                try {
                    pagerState.animateScrollToPage(settled + 1)
                } catch (e: CancellationException) {
                    if (isActive) viewModel.resume() else throw e
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }

    DisposableEffect(Unit) {
        onDispose { viewModel.leave() }
    }

    DisposableEffect(darkTheme) {
        val activity = context.findActivity()
        val insets = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        insets?.isAppearanceLightStatusBars = false
        onDispose { insets?.isAppearanceLightStatusBars = !darkTheme }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().background(Color.Black),
        key = { items[it].url },
    ) { page ->
        val item = items[page]
        ShortPage(
            item = item,
            active = item.url == settled?.url,
            controller = controller,
            playWhenReady = playWhenReady,
            buffering = buffering,
            rendered = item.url == renderedUrl,
            failed = failed,
            onTap = {
                if (item.url == settled?.url) {
                    if (failed) viewModel.play(item, next) else viewModel.togglePlay()
                }
            },
            onChannelClick = onChannelClick,
        )
    }
}

@Composable
private fun ShortPage(
    item: VideoItem,
    active: Boolean,
    controller: Player?,
    playWhenReady: Boolean,
    buffering: Boolean,
    rendered: Boolean,
    failed: Boolean,
    onTap: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(active, controller) {
        val player = controller
        while (active && player != null) {
            val duration = player.duration
            progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        if (active && controller != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = {
                    it.player = controller
                    it.keepScreenOn = playWhenReady
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!active || !rendered) {
            AsyncImage(
                model = rememberImageRequest(item.thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (active) {
            when {
                failed -> Text(
                    "Couldn't play this Short. Tap to retry.",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                !rendered || buffering -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                !playWhenReady -> Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 16.dp),
        ) {
            Text(
                item.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.uploader.isNotEmpty()) {
                Text(
                    item.uploader,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(enabled = item.uploaderUrl.isNotEmpty()) { onChannelClick(item.uploaderUrl) },
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 112.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    shareVideo(context, item.url, item.title, true)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = "Share",
                tint = Color.White,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(10.dp),
            )
            Text(
                "Share",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color.Black, blurRadius = 6f)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (active) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private const val PROGRESS_INTERVAL_MS = 250L
