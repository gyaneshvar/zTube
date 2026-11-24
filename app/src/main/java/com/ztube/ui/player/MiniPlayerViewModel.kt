package com.ztube.ui.player

import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import com.ztube.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
) : ViewModel() {

    val item: StateFlow<MediaItem?> = connection.currentItem
    val playing: StateFlow<Boolean> = connection.playing

    init {
        connection.connect()
    }

    fun togglePlay() = connection.togglePlay()

    fun close() = connection.close()
}
