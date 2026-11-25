package com.ztube.ui.shorts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.EmptyState

@Composable
fun ShortsTabScreen(
    onChannelClick: (String) -> Unit,
    viewModel: ShortsTabViewModel = hiltViewModel(),
) {
    val shorts by viewModel.shorts.collectAsStateWithLifecycle()
    val items = shorts
    when {
        items == null -> Box(Modifier.fillMaxSize())
        items.isEmpty() -> EmptyState(
            icon = Icons.Outlined.Bolt,
            title = "Subscribe to channels to see their Shorts here",
        )
        else -> ShortsPager(items, startIndex = 0, onChannelClick = onChannelClick)
    }
}

@Composable
fun ShortsRouteScreen(
    onChannelClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ShortsRouteViewModel = hiltViewModel(),
) {
    if (viewModel.items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (viewModel.items.isNotEmpty()) {
            ShortsPager(viewModel.items, viewModel.startIndex, onChannelClick)
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    }
}
