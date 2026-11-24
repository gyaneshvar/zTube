package com.ztube.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.MediaItem
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.ztube.data.prefs.YoutubeAccount
import com.ztube.ui.account.AccountTabViewModel
import com.ztube.ui.account.SignInPrompt
import com.ztube.ui.account.SignInPromptViewModel
import com.ztube.ui.channel.ChannelScreen
import com.ztube.ui.components.AppSnackbar
import com.ztube.ui.components.LocalSnackbar
import com.ztube.ui.components.rememberImageRequest
import com.ztube.ui.home.HomeScreen
import com.ztube.ui.library.LibraryScreen
import com.ztube.ui.player.MiniPlayer
import com.ztube.ui.player.MiniPlayerViewModel
import com.ztube.ui.player.PlayerScreen
import com.ztube.ui.playlist.PlaylistScreen
import com.ztube.ui.search.SearchScreen
import com.ztube.ui.settings.SettingsScreen
import com.ztube.ui.update.UpdateHost
import com.ztube.ui.update.UpdateViewModel

private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Search("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Library("library", "Library", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    You("you", "You", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}

private const val PLAYER_ROUTE = "player/{url}"
private const val CHANNEL_ROUTE = "channel/{url}"
private const val PLAYLIST_ROUTE = "playlist/{id}"

@Composable
fun ZTubeRoot(
    splashVisible: Boolean,
    miniPlayer: MiniPlayerViewModel = hiltViewModel(),
    accountTab: AccountTabViewModel = hiltViewModel(),
    update: UpdateViewModel = hiltViewModel(),
    signInPrompt: SignInPromptViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val miniItem by miniPlayer.item.collectAsStateWithLifecycle()
    val miniPlaying by miniPlayer.playing.collectAsStateWithLifecycle()
    val account by accountTab.account.collectAsStateWithLifecycle()
    val signInPromptVisible by signInPrompt.visible.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onPlayer = destination?.route == PLAYER_ROUTE
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appSnackbar = remember { AppSnackbar(snackbarHost, scope) }
    val lastMiniItem = remember { mutableStateOf<MediaItem?>(null) }
    miniItem?.let { lastMiniItem.value = it }

    fun openVideo(url: String) = navController.navigate("player/${Uri.encode(url)}") {
        popUpTo(PLAYER_ROUTE) { inclusive = true }
        launchSingleTop = true
    }
    fun openChannel(url: String) = navController.navigate("channel/${Uri.encode(url)}")
    fun openTab(tab: Tab) = navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalSnackbar provides appSnackbar) {
        SignInPrompt(splashVisible)
        UpdateHost(update, blocked = splashVisible || signInPromptVisible)
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                AnimatedVisibility(
                    visible = !onPlayer,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = miniItem != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            lastMiniItem.value?.let { item ->
                                MiniPlayer(
                                    item = item,
                                    playing = miniPlaying,
                                    progress = miniPlayer.progress,
                                    onClick = { openVideo(item.mediaId) },
                                    onTogglePlay = miniPlayer::togglePlay,
                                    onClose = miniPlayer::close,
                                )
                            }
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Tab.entries.forEach { tab ->
                                val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { openTab(tab) },
                                    icon = {
                                        if (tab == Tab.You) {
                                            YouTabIcon(account, selected)
                                        } else {
                                            Icon(
                                                if (selected) tab.selectedIcon else tab.icon,
                                                contentDescription = tab.label,
                                            )
                                        }
                                    },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(150)) },
            ) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        onVideoClick = ::openVideo,
                        onChannelClick = ::openChannel,
                        onOpenSearch = { openTab(Tab.Search) },
                    )
                }
                composable(Tab.Search.route) {
                    SearchScreen(onVideoClick = ::openVideo, onChannelClick = ::openChannel)
                }
                composable(Tab.Library.route) {
                    LibraryScreen(
                        onVideoClick = ::openVideo,
                        onChannelClick = ::openChannel,
                        onPlaylistClick = { navController.navigate("playlist/$it") },
                    )
                }
                composable(Tab.You.route) {
                    SettingsScreen(update = update)
                }
                composable(
                    PLAYER_ROUTE,
                    enterTransition = { slideInVertically { it } + fadeIn() },
                    popExitTransition = { slideOutVertically { it } + fadeOut() },
                ) {
                    PlayerScreen(
                        onChannelClick = ::openChannel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(CHANNEL_ROUTE) {
                    ChannelScreen(
                        onVideoClick = ::openVideo,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    PLAYLIST_ROUTE,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) {
                    PlaylistScreen(
                        onVideoClick = ::openVideo,
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTabIcon(account: YoutubeAccount?, selected: Boolean) {
    val avatar = account?.channelAvatar
    BadgedBox(
        badge = { if (account?.needsReauth == true) Badge() },
    ) {
        if (avatar != null) {
            val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            AsyncImage(
                model = rememberImageRequest(avatar),
                contentDescription = Tab.You.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape).border(2.dp, ring, CircleShape),
            )
        } else {
            Icon(
                if (selected) Tab.You.selectedIcon else Tab.You.icon,
                contentDescription = Tab.You.label,
            )
        }
    }
}
