package com.ztube.ui.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.data.feed.FeedRepository
import com.ztube.data.prefs.SettingsRepository
import com.ztube.playback.PipState
import com.ztube.playback.PlayerConnection
import com.ztube.playback.videoMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val connection: PlayerConnection,
    private val settings: SettingsRepository,
    private val repository: YoutubeRepository,
    private val queue: ShortsQueue,
    pipState: PipState,
) : ViewModel() {

    val controller: StateFlow<MediaController?> = connection.controller

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    private val _renderedUrl = MutableStateFlow<String?>(null)
    val renderedUrl: StateFlow<String?> = _renderedUrl.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _advance = Channel<String>(Channel.CONFLATED)
    val advance: Flow<String> = _advance.receiveAsFlow()

    private val autoScroll = settings.shortsAutoScroll.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val played = mutableSetOf<String>()
    private var playJob: Job? = null
    private var prefetchJob: Job? = null
    private var attached: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _playWhenReady.value = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _buffering.value = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) _failed.value = false
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            val url = mediaItem?.mediaId ?: return
            if (autoScroll.value) _advance.trySend(url)
        }

        override fun onRenderedFirstFrame() {
            _renderedUrl.value = queue.activeUrl.value
        }

        override fun onPlayerError(error: PlaybackException) {
            _failed.value = true
        }
    }

    init {
        pipState.canEnter.value = false
        connection.connect()
        viewModelScope.launch {
            connection.controller.collect { current ->
                attached?.removeListener(listener)
                attached = current
                current?.addListener(listener)
                if (current != null) {
                    _playWhenReady.value = current.playWhenReady
                    _buffering.value = current.playbackState == Player.STATE_BUFFERING
                }
            }
        }
    }

    fun play(item: VideoItem, next: VideoItem?) {
        playJob?.cancel()
        _renderedUrl.value = null
        _failed.value = false
        queue.setActive(item.url)
        playJob = viewModelScope.launch {
            val quality = settings.quality.first()
            val player = connection.awaitController()
            played.add(item.url)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.setMediaItem(
                videoMediaItem(item.url, item.title, item.uploader, item.thumbnailUrl, item.durationSeconds, quality),
            )
            player.prepare()
            player.play()
            if (next != null) prefetch(next.url)
        }
    }

    fun togglePlay() = connection.togglePlay()

    fun resume() {
        connection.controller.value?.play()
    }

    fun pause() {
        connection.controller.value?.pause()
    }

    fun leave() {
        playJob?.cancel()
        prefetchJob?.cancel()
        queue.setActive(null)
        val player = connection.controller.value ?: return
        player.repeatMode = Player.REPEAT_MODE_OFF
        val id = player.currentMediaItem?.mediaId ?: return
        if (id in played) {
            player.stop()
            player.clearMediaItems()
        }
    }

    override fun onCleared() {
        attached?.removeListener(listener)
    }

    private fun prefetch(url: String) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            runCatching { repository.streamInfo(url) }
        }
    }
}

@HiltViewModel
class ShortsTabViewModel @Inject constructor(
    feed: FeedRepository,
) : ViewModel() {

    val shorts: StateFlow<List<VideoItem>?> = feed.shorts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@HiltViewModel
class ShortsRouteViewModel @Inject constructor(
    queue: ShortsQueue,
) : ViewModel() {

    val items: List<VideoItem> = queue.items
    val startIndex: Int = queue.startIndex
}

@HiltViewModel
class ShortsQueueViewModel @Inject constructor(
    val queue: ShortsQueue,
) : ViewModel()
