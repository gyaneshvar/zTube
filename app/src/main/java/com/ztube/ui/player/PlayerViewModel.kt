package com.ztube.ui.player

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.best
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.data.prefs.SettingsRepository
import com.ztube.download.DownloadManager
import com.ztube.download.DownloadOption
import com.ztube.download.DownloadStatus
import com.ztube.download.DownloadStreams
import com.ztube.download.thumbnailModel
import com.ztube.playback.PipState
import com.ztube.playback.PlayerConnection
import com.ztube.playback.StreamSelector
import com.ztube.playback.isStreamExpired
import com.ztube.playback.videoMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
        val likeCount: Long = -1,
        val subscriberCount: Long = -1,
        val uploadedText: String? = null,
        val thumbnail: String? = null,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
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

    private val currentUrl = MutableStateFlow(checkNotNull(savedStateHandle.get<String>("url")))
    private val url: String get() = currentUrl.value

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val controller: StateFlow<MediaController?> = connection.controller

    val speed: StateFlow<Float> = settings.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val playing: StateFlow<Boolean> = connection.playing

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

    val download: StateFlow<DownloadEntity?> = currentUrl
        .flatMapLatest { downloadDao.download(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _related = MutableStateFlow<List<VideoItem>?>(null)
    val related: StateFlow<List<VideoItem>?> = _related.asStateFlow()

    val autoplay: StateFlow<Boolean> = settings.autoplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _downloadOptions = MutableStateFlow<List<DownloadOption>>(emptyList())
    val downloadOptions: StateFlow<List<DownloadOption>> = _downloadOptions.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var info: StreamInfo? = null
    private var visible = false
    private var started = false
    private var lastItem: MediaItem? = null
    private var loadJob: Job? = null
    private var errorJob: Job? = null
    private var attached: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) _playbackError.value = null
            updatePip()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId ?: return
            if (id == url) return
            reset(id)
            load(id, startPlayback = false)
        }

        override fun onPlayerError(error: PlaybackException) {
            errorJob?.cancel()
            errorJob = viewModelScope.launch {
                if (error.isStreamExpired()) delay(RECOVERY_GRACE_MS)
                if (connection.controller.value?.playerError != null) {
                    _playbackError.value = error.message ?: "Playback failed"
                }
            }
        }
    }

    init {
        connection.connect()
        viewModelScope.launch { connection.errors.collect { _playbackError.value = it } }
        viewModelScope.launch {
            connection.controller.collect { current ->
                attached?.removeListener(listener)
                attached = current
                current?.addListener(listener)
                updatePip()
            }
        }
        load(url, startPlayback = true)
    }

    fun reload() {
        reset(url)
        load(url, startPlayback = true)
    }

    fun playVideo(videoUrl: String) {
        if (videoUrl == url) return
        reset(videoUrl)
        load(videoUrl, startPlayback = true)
    }

    private fun reset(videoUrl: String) {
        currentUrl.value = videoUrl
        savedStateHandle["url"] = videoUrl
        info = null
        started = false
        lastItem = null
        _state.value = PlayerUiState.Loading
        _related.value = null
        _downloadOptions.value = emptyList()
        _playbackError.value = null
    }

    private fun load(target: String, startPlayback: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val saved = downloadDao.get(target)?.takeIf { it.status == DownloadStatus.DONE }
            if (saved != null) playDownloaded(target, saved, startPlayback) else playOnline(target, startPlayback)
        }
    }

    private suspend fun playOnline(target: String, startPlayback: Boolean) {
        try {
            val streamInfo = repository.streamInfo(target)
            if (url != target) return
            setInfo(streamInfo)
            val heights = StreamSelector.heights(streamInfo)
            val quality = StreamSelector.pickHeight(heights, settings.quality.first())
            _state.value = loaded(streamInfo, heights, quality)
            _related.value = repository.related(streamInfo)
            val item = currentItem(streamInfo, quality)
            if (startPlayback) {
                startPlayback(item, startPosition(streamInfo.duration * 1000))
            } else {
                lastItem = item
                started = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (url == target) _state.value = PlayerUiState.Error(e.message ?: "Failed to load video")
        }
    }

    private suspend fun playDownloaded(target: String, saved: DownloadEntity, startPlayback: Boolean) {
        _state.value = PlayerUiState.Loaded(
            title = saved.title,
            uploader = saved.uploader,
            uploaderUrl = "",
            uploaderAvatarUrl = null,
            viewCount = -1,
            description = "",
            heights = emptyList(),
            quality = 0,
            thumbnail = saved.thumbnailModel(),
        )
        try {
            coroutineScope {
                launch {
                    try {
                        val streamInfo = repository.streamInfo(target)
                        if (url != target) return@launch
                        setInfo(streamInfo)
                        _state.value = loaded(streamInfo, emptyList(), 0)
                        _related.value = repository.related(streamInfo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (url == target) _related.value = emptyList()
                    }
                }
                val item = videoMediaItem(
                    target,
                    saved.title,
                    saved.uploader,
                    saved.thumbnailModel(),
                    saved.durationSeconds,
                    0,
                )
                if (startPlayback) {
                    startPlayback(item, startPosition(saved.durationSeconds * 1000))
                } else {
                    lastItem = item
                    started = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (url == target) _state.value = PlayerUiState.Error(e.message ?: "Failed to play download")
        }
    }

    fun setAutoplay(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoplay(enabled) }
    }

    private suspend fun startPlayback(item: MediaItem, startPositionMs: Long) {
        lastItem = item
        val controller = connection.awaitController()
        if (controller.currentMediaItem?.mediaId != url) {
            controller.setMediaItem(item, startPositionMs)
            controller.prepare()
            controller.setPlaybackSpeed(settings.playbackSpeed.first())
            controller.play()
        }
        started = true
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
        uploaderAvatarUrl = streamInfo.uploaderAvatars.best(),
        viewCount = streamInfo.viewCount,
        description = streamInfo.description.content.orEmpty(),
        heights = heights,
        quality = quality,
        likeCount = streamInfo.likeCount,
        subscriberCount = streamInfo.uploaderSubscriberCount,
        uploadedText = streamInfo.textualUploadDate,
        thumbnail = streamInfo.thumbnails.best(),
    )

    fun startDownload(option: DownloadOption) {
        val streamInfo = info ?: return
        viewModelScope.launch {
            downloadManager.start(
                DownloadEntity(
                    videoUrl = url,
                    title = streamInfo.name,
                    uploader = streamInfo.uploaderName.orEmpty(),
                    thumbnailUrl = streamInfo.thumbnails.best(),
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
        connection.controller.value?.setPlaybackSpeed(speed)
        viewModelScope.launch { settings.setPlaybackSpeed(speed) }
    }

    fun setQuality(height: Int) {
        val current = _state.value as? PlayerUiState.Loaded ?: return
        val streamInfo = info ?: return
        _state.value = current.copy(quality = height)
        viewModelScope.launch { settings.setQuality(height) }
        _playbackError.value = null
        connection.controller.value?.run {
            val item = currentItem(streamInfo, height)
            lastItem = item
            setMediaItem(item, currentPosition)
            prepare()
        }
    }

    fun retry() {
        val controller = connection.controller.value ?: return
        val metadata = lastItem?.mediaMetadata ?: return
        val quality = (_state.value as? PlayerUiState.Loaded)?.quality ?: 0
        val item = videoMediaItem(
            url,
            metadata.title?.toString().orEmpty(),
            metadata.artist?.toString().orEmpty(),
            metadata.artworkUri?.toString(),
            (metadata.durationMs ?: 0L) / 1000,
            quality,
            fresh = true,
        )
        val live = info?.let { StreamSelector.isLive(it) } == true
        _playbackError.value = null
        controller.setMediaItem(item, if (live) C.TIME_UNSET else controller.currentPosition)
        controller.prepare()
        controller.play()
    }

    fun setVisible(visible: Boolean) {
        this.visible = visible
        updatePip()
        if (!visible || !started) return
        val id = connection.controller.value?.currentMediaItem?.mediaId ?: return
        if (id != url) {
            reset(id)
            load(id, startPlayback = false)
        }
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
                    thumbnailUrl = streamInfo.thumbnails.best(),
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
        attached?.removeListener(listener)
    }

    private fun updatePip() {
        pipState.canEnter.value = visible && connection.controller.value?.isPlaying == true
    }

    private suspend fun startPosition(durationMs: Long): Long {
        val saved = historyDao.position(url) ?: return 0
        return if (saved > RESUME_MARGIN_MS && saved < durationMs - RESUME_MARGIN_MS) saved else 0
    }

    private fun currentItem(streamInfo: StreamInfo, quality: Int): MediaItem = videoMediaItem(
        url,
        streamInfo.name,
        streamInfo.uploaderName.orEmpty(),
        streamInfo.thumbnails.best(),
        streamInfo.duration,
        quality,
    )

    private companion object {
        const val RESUME_MARGIN_MS = 10_000L
        const val RECOVERY_GRACE_MS = 4_000L
    }
}
