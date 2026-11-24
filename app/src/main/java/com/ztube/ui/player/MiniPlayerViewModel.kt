package com.ztube.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.ztube.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val connection: PlayerConnection,
) : ViewModel() {

    val item: StateFlow<MediaItem?> = connection.currentItem
    val playing: StateFlow<Boolean> = connection.playing

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    init {
        connection.connect()
        viewModelScope.launch {
            combine(connection.currentItem, connection.playing) { item, playing -> (item != null) to playing }
                .collectLatest { (hasItem, playing) ->
                    if (!hasItem) {
                        _progress.value = 0f
                        return@collectLatest
                    }
                    val controller = connection.awaitController()
                    while (controller.isConnected) {
                        val duration = controller.duration
                        _progress.value = if (duration > 0) {
                            (controller.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        if (!playing) break
                        delay(PROGRESS_INTERVAL_MS)
                    }
                }
        }
    }

    fun togglePlay() = connection.togglePlay()

    fun close() = connection.close()

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
