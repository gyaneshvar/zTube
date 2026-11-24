package com.ztube.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ztube.ui.channel.ChannelScreen
import com.ztube.ui.home.HomeScreen
import com.ztube.ui.library.LibraryScreen
import com.ztube.ui.player.MiniPlayer
import com.ztube.ui.player.MiniPlayerViewModel
import com.ztube.ui.player.PlayerScreen
import com.ztube.ui.playlist.PlaylistScreen
import com.ztube.ui.search.SearchScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Search("search", "Search", Icons.Filled.Search),
    Library("library", "Library", Icons.Filled.VideoLibrary),
}

private const val PLAYER_ROUTE = "player/{url}"
private const val CHANNEL_ROUTE = "channel/{url}"
private const val PLAYLIST_ROUTE = "playlist/{id}"

@Composable
fun ZTubeRoot(miniPlayer: MiniPlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val miniItem by miniPlayer.item.collectAsStateWithLifecycle()
    val miniPlaying by miniPlayer.playing.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    fun openVideo(url: String) = navController.navigate("player/${Uri.encode(url)}")
    fun openChannel(url: String) = navController.navigate("channel/${Uri.encode(url)}")
    fun openTab(tab: Tab) = navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (destination?.route != PLAYER_ROUTE) {
                Column {
                    miniItem?.let { item ->
                        MiniPlayer(
                            item = item,
                            playing = miniPlaying,
                            onClick = { openVideo(item.mediaId) },
                            onTogglePlay = miniPlayer::togglePlay,
                            onClose = miniPlayer::close,
                        )
                    }
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                                onClick = { openTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
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
            modifier = Modifier.padding(padding),
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
            composable(PLAYER_ROUTE) { PlayerScreen(onChannelClick = ::openChannel) }
            composable(CHANNEL_ROUTE) { ChannelScreen(onVideoClick = ::openVideo) }
            composable(
                PLAYLIST_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                PlaylistScreen(
                    onVideoClick = ::openVideo,
                    onDeleted = { navController.popBackStack() },
                )
            }
        }
    }
}
