package com.ztube.ui.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.ztube.data.db.DownloadDao
import com.ztube.data.db.DownloadEntity
import com.ztube.data.db.HistoryDao
import com.ztube.data.db.PlaylistDao
import com.ztube.data.db.PlaylistEntity
import com.ztube.data.db.PlaylistSummary
import com.ztube.data.db.PlaylistVideoEntity
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.SubscriptionEntity
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.data.prefs.SettingsRepository
import com.ztube.download.DownloadManager
import com.ztube.download.DownloadOption
import com.ztube.download.DownloadStatus
import com.ztube.download.DownloadStreams
import com.ztube.download.thumbnailModel
import com.ztube.playback.EXTRA_QUALITY
import com.ztube.playback.PipState
import com.ztube.playback.PlayerConnection
import com.ztube.playback.StreamSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Loaded(
        val title: String,
        val uploader: String,
        val uploaderUrl: String,
        val uploaderAvatarUrl: String?,
        val viewCount: Long,
        val description: String,
        val heights: List<Int>,
        val quality: Int,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: YoutubeRepository,
    private val settings: SettingsRepository,
    private val pipState: PipState,
    private val connection: PlayerConnection,
    private val historyDao: HistoryDao,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    private val url: String = Uri.decode(checkNotNull(savedStateHandle["url"]))

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    val speed: StateFlow<Float> = settings.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val inPip: StateFlow<Boolean> = pipState.inPip

    val subscribed: StateFlow<Boolean> = _state
        .flatMapLatest { s ->
            if (s is PlayerUiState.Loaded && s.uploaderUrl.isNotEmpty()) {
                subscriptionDao.isSubscribed(s.uploaderUrl)
            } else {
                flowOf(false)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val playlists: StateFlow<List<PlaylistSummary>> = playlistDao.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val download: StateFlow<DownloadEntity?> = downloadDao.download(url)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _downloadOptions = MutableStateFlow<List<DownloadOption>>(emptyList())
    val downloadOptions: StateFlow<List<DownloadOption>> = _downloadOptions.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var info: StreamInfo? = null
    private var visible = false
    private var lastItem: MediaItem? = null
    private var lastStartMs = 0L

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) _playbackError.value = null
            updatePip()
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackError.value = error.message ?: "Playback failed"
        }
    }

    init {
        viewModelScope.launch { connection.errors.collect { _playbackError.value = it } }
        viewModelScope.launch {
            val saved = downloadDao.get(url)?.takeIf { it.status == DownloadStatus.DONE }
            if (saved != null) playDownloaded(saved) else playOnline()
        }
    }

    private suspend fun playOnline() {
        try {
            val streamInfo = repository.streamInfo(url)
            setInfo(streamInfo)
            val heights = StreamSelector.heights(streamInfo)
            val quality = StreamSelector.pickHeight(heights, settings.quality.first())
            _state.value = loaded(streamInfo, heights, quality)
            startPlayback(mediaItem(streamInfo, quality), startPosition(streamInfo.duration * 1000))
        } catch (e: Exception) {
            _state.value = PlayerUiState.Error(e.message ?: "Failed to load video")
        }
    }

    private suspend fun playDownloaded(saved: DownloadEntity) {
        _state.value = PlayerUiState.Loaded(
            title = saved.title,
            uploader = saved.uploader,
            uploaderUrl = "",
            uploaderAvatarUrl = null,
            viewCount = -1,
            description = "",
            heights = emptyList(),
            quality = 0,
        )
        viewModelScope.launch {
            try {
                val streamInfo = repository.streamInfo(url)
                setInfo(streamInfo)
                _state.value = loaded(streamInfo, emptyList(), 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        try {
            startPlayback(offlineItem(saved), startPosition(saved.durationSeconds * 1000))
        } catch (e: Exception) {
            _state.value = PlayerUiState.Error(e.message ?: "Failed to play download")
        }
    }

    private suspend fun startPlayback(item: MediaItem, startPositionMs: Long) {
        lastItem = item
        lastStartMs = startPositionMs
        val controller = connection.controller()
        controller.removeListener(listener)
        controller.addListener(listener)
        if (controller.currentMediaItem?.mediaId != url) {
            controller.setMediaItem(item, startPositionMs)
            controller.prepare()
            controller.setPlaybackSpeed(settings.playbackSpeed.first())
            controller.play()
        }
        _controller.value = controller
        updatePip()
    }

    private fun setInfo(streamInfo: StreamInfo) {
        info = streamInfo
        _downloadOptions.value = DownloadStreams.options(streamInfo)
    }

    private fun loaded(streamInfo: StreamInfo, heights: List<Int>, quality: Int) = PlayerUiState.Loaded(
        title = streamInfo.name,
        uploader = streamInfo.uploaderName.orEmpty(),
        uploaderUrl = streamInfo.uploaderUrl.orEmpty(),
        uploaderAvatarUrl = streamInfo.uploaderAvatars.firstOrNull()?.url,
        viewCount = streamInfo.viewCount,
        description = streamInfo.description.content.orEmpty(),
        heights = heights,
        quality = quality,
    )

    fun startDownload(option: DownloadOption) {
        val streamInfo = info ?: return
        viewModelScope.launch {
            downloadManager.start(
                DownloadEntity(
                    videoUrl = url,
                    title = streamInfo.name,
                    uploader = streamInfo.uploaderName.orEmpty(),
                    thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url,
                    durationSeconds = streamInfo.duration,
                    videoPath = null,
                    audioPath = "",
                    quality = option.label,
                    totalBytes = option.sizeBytes,
                    downloadedBytes = 0,
                    status = DownloadStatus.QUEUED,
                    createdAt = System.currentTimeMillis(),
                ),
                option.height,
            )
            Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDownload() {
        viewModelScope.launch { downloadManager.delete(url) }
    }

    fun setSpeed(speed: Float) {
        _controller.value?.setPlaybackSpeed(speed)
        viewModelScope.launch { settings.setPlaybackSpeed(speed) }
    }

    fun setQuality(height: Int) {
        val current = _state.value as? PlayerUiState.Loaded ?: return
        val streamInfo = info ?: return
        _state.value = current.copy(quality = height)
        viewModelScope.launch { settings.setQuality(height) }
        _playbackError.value = null
        _controller.value?.run {
            val item = mediaItem(streamInfo, height)
            lastItem = item
            lastStartMs = currentPosition
            setMediaItem(item, currentPosition)
            prepare()
        }
    }

    fun retry() {
        val item = lastItem ?: return
        val controller = _controller.value ?: return
        _playbackError.value = null
        controller.setMediaItem(item, lastStartMs)
        controller.prepare()
        controller.play()
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        updatePip()
    }

    fun toggleSubscribe() {
        val current = _state.value as? PlayerUiState.Loaded ?: return
        if (current.uploaderUrl.isEmpty()) return
        viewModelScope.launch {
            if (subscribed.value) {
                subscriptionDao.delete(current.uploaderUrl)
            } else {
                subscriptionDao.insert(
                    SubscriptionEntity(
                        channelUrl = current.uploaderUrl,
                        name = current.uploader,
                        avatarUrl = current.uploaderAvatarUrl,
                        subscribedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun addToPlaylist(playlistId: Long) {
        val streamInfo = info ?: return
        viewModelScope.launch {
            playlistDao.insertVideo(
                PlaylistVideoEntity(
                    playlistId = playlistId,
                    videoUrl = url,
                    title = streamInfo.name,
                    uploader = streamInfo.uploaderName.orEmpty(),
                    thumbnailUrl = streamInfo.thumbnails.firstOrNull()?.url,
                    durationSeconds = streamInfo.duration,
                    position = playlistDao.nextPosition(playlistId),
                ),
            )
            Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = playlistDao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
            addToPlaylist(id)
        }
    }

    override fun onCleared() {
        pipState.canEnter.value = false
        _controller.value?.removeListener(listener)
    }

    private fun updatePip() {
        pipState.canEnter.value = visible && _controller.value?.isPlaying == true
    }

    private suspend fun startPosition(durationMs: Long): Long {
        val saved = historyDao.position(url) ?: return 0
        return if (saved > RESUME_MARGIN_MS && saved < durationMs - RESUME_MARGIN_MS) saved else 0
    }

    private fun offlineItem(saved: DownloadEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(saved.title)
                    .setArtist(saved.uploader)
                    .setArtworkUri(saved.thumbnailModel()?.toUri())
                    .setDurationMs(saved.durationSeconds * 1000)
                    .build(),
            )
            .build()

    private fun mediaItem(streamInfo: StreamInfo, height: Int): MediaItem {
        val extras = Bundle().apply { putInt(EXTRA_QUALITY, height) }
        return MediaItem.Builder()
            .setMediaId(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(streamInfo.name)
                    .setArtist(streamInfo.uploaderName)
                    .setArtworkUri(streamInfo.thumbnails.firstOrNull()?.url?.toUri())
                    .setDurationMs(streamInfo.duration * 1000)
                    .build(),
            )
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .build()
    }

    private companion object {
        const val RESUME_MARGIN_MS = 10_000L
    }
}
