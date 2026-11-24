package com.ztube.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.ztube.data.db.DownloadDao
import com.ztube.data.db.HistoryDao
import com.ztube.data.db.HistoryEntity
import com.ztube.data.extractor.OkHttpDownloader
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.data.prefs.SettingsRepository
import com.ztube.data.sponsorblock.SkipSegment
import com.ztube.data.sponsorblock.SponsorBlockClient
import com.ztube.download.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var repository: YoutubeRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sponsorBlock: SponsorBlockClient
    @Inject lateinit var historyDao: HistoryDao
    @Inject lateinit var downloadDao: DownloadDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var segmentsJob: Job? = null
    private var segments: List<SkipSegment> = emptyList()
    private val skipped = mutableSetOf<Int>()
    private var sponsorBlockEnabled = true
    private var autoplay = true
    private var nextJob: Job? = null
    private var recoveryJob: Job? = null
    private var recoveryId: String? = null
    private var recoveryAttempts = 0
    private var lastMediaId: String? = null
    private val played = mutableSetOf<String>()
    private lateinit var player: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(50_000, 120_000, 1_000, 2_000)
            .build()
        val hlsDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                StreamMediaSourceFactory(
                    DefaultDataSource.Factory(this, YoutubeHttpDataSourceFactory()),
                    hlsDataSourceFactory,
                ),
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadSegments(mediaItem)
                val sameItem = mediaItem?.mediaId == lastMediaId
                lastMediaId = mediaItem?.mediaId
                if (!sameItem || reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    recordHistory(mediaItem)
                }
                enqueueNext()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) recoveryAttempts = 0
            }

            override fun onPlayerError(error: PlaybackException) = recover(error)

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress(player)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason != Player.DISCONTINUITY_REASON_REMOVE &&
                    reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                ) return
                oldPosition.mediaItem?.let { savePosition(it.mediaId, oldPosition.positionMs) }
            }
        })
        mediaSession = MediaSession.Builder(this, player).setCallback(SessionCallback()).build()

        scope.launch { settings.sponsorBlockEnabled.collect { sponsorBlockEnabled = it } }
        scope.launch {
            settings.autoplay.collect {
                autoplay = it
                if (it) enqueueNext() else removeQueued()
            }
        }
        scope.launch { pollSegments(player) }
        scope.launch { pollProgress(player) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun loadSegments(mediaItem: MediaItem?) {
        segmentsJob?.cancel()
        segments = emptyList()
        skipped.clear()
        val videoId = mediaItem?.mediaMetadata?.extras?.getString(EXTRA_VIDEO_ID) ?: return
        segmentsJob = scope.launch {
            segments = try {
                sponsorBlock.segments(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun pollSegments(player: Player) {
        while (true) {
            delay(POLL_INTERVAL_MS)
            if (!sponsorBlockEnabled || !player.isPlaying) continue
            val position = player.currentPosition
            val index = segments.indexOfFirst {
                position >= it.start * 1000 && position < it.end * 1000
            }
            if (index < 0 || !skipped.add(index)) continue
            val segment = segments[index]
            player.seekTo((segment.end * 1000).toLong())
            Toast.makeText(this, "Skipped ${segment.category}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun pollProgress(player: Player) {
        while (true) {
            delay(PROGRESS_INTERVAL_MS)
            if (player.isPlaying) saveProgress(player)
        }
    }

    private fun saveProgress(player: Player) {
        val item = player.currentMediaItem ?: return
        savePosition(item.mediaId, player.currentPosition)
    }

    private fun savePosition(videoUrl: String, positionMs: Long) {
        scope.launch { historyDao.updatePosition(videoUrl, positionMs) }
    }

    private fun recordHistory(mediaItem: MediaItem?) {
        val item = mediaItem ?: return
        val metadata = item.mediaMetadata
        scope.launch {
            historyDao.record(
                HistoryEntity(
                    videoUrl = item.mediaId,
                    title = metadata.title?.toString().orEmpty(),
                    uploader = metadata.artist?.toString().orEmpty(),
                    thumbnailUrl = metadata.artworkUri?.toString(),
                    durationSeconds = (metadata.durationMs ?: 0L) / 1000,
                    positionMs = 0,
                    watchedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun resolve(item: MediaItem, fresh: Boolean = false): MediaItem {
        if (item.localConfiguration != null) return item
        resolveDownloaded(item)?.let { return it }
        val requestExtras = item.requestMetadata.extras
        val info = repository.streamInfo(item.mediaId, fresh || requestExtras?.getBoolean(EXTRA_FRESH) == true)
        val quality = requestExtras?.getInt(EXTRA_QUALITY, 0)?.takeIf { it > 0 }
            ?: settings.quality.first()
        val stream = StreamSelector.resolve(info, quality)
        val extras = Bundle().apply {
            putString(EXTRA_VIDEO_ID, info.id)
            stream.audioUri?.let { putString(EXTRA_AUDIO_URL, it) }
            if (stream.videoManifest != null && stream.audioManifest != null) {
                putString(EXTRA_VIDEO_MANIFEST, stream.videoManifest)
                putString(EXTRA_AUDIO_MANIFEST, stream.audioManifest)
            }
        }
        val builder = item.buildUpon()
            .setUri(stream.uri)
            .setMimeType(
                when {
                    stream.isDash -> MimeTypes.APPLICATION_MPD
                    stream.isHls -> MimeTypes.APPLICATION_M3U8
                    else -> null
                },
            )
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
        if (stream.isHls) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(LIVE_EDGE_GAP_MS).build(),
            )
        }
        return builder.build()
    }

    private fun enqueueNext() {
        nextJob?.cancel()
        val current = player.currentMediaItem ?: return
        if (!autoplay || player.currentMediaItemIndex != player.mediaItemCount - 1) return
        played.add(current.mediaId)
        nextJob = scope.launch {
            try {
                val related = repository.related(repository.streamInfo(current.mediaId))
                val next = related.firstOrNull { it.url !in played }
                    ?: related.firstOrNull { it.url != current.mediaId }
                    ?: return@launch
                val resolved = resolve(
                    videoMediaItem(
                        next.url,
                        next.title,
                        next.uploader,
                        next.thumbnailUrl,
                        next.durationSeconds,
                        settings.quality.first(),
                    ),
                )
                val stillLast = player.currentMediaItem?.mediaId == current.mediaId &&
                    player.currentMediaItemIndex == player.mediaItemCount - 1
                if (autoplay && stillLast) player.addMediaItem(resolved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Autoplay enqueue failed", e)
            }
        }
    }

    private fun removeQueued() {
        nextJob?.cancel()
        val next = player.currentMediaItemIndex + 1
        if (next < player.mediaItemCount) player.removeMediaItems(next, player.mediaItemCount)
    }

    private fun recover(error: PlaybackException) {
        if (!error.isStreamExpired()) return
        val item = player.currentMediaItem ?: return
        if (item.localConfiguration?.uri?.scheme == "file") return
        if (recoveryId != item.mediaId) {
            recoveryId = item.mediaId
            recoveryAttempts = 0
        }
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) return
        recoveryAttempts++
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady
        val request = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setMediaMetadata(item.mediaMetadata)
            .setRequestMetadata(item.requestMetadata)
            .build()
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            try {
                val resolved = resolve(request, fresh = true)
                if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
                val index = player.currentMediaItemIndex
                player.replaceMediaItem(index, resolved)
                player.seekTo(index, position)
                player.prepare()
                if (wasPlaying) player.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Stream recovery failed", e)
            }
        }
    }

    private suspend fun resolveDownloaded(item: MediaItem): MediaItem? {
        val download = downloadDao.get(item.mediaId)?.takeIf { it.status == DownloadStatus.DONE } ?: return null
        val video = download.videoPath?.let { File(it) }
        val audio = download.audioPath.takeIf { it.isNotEmpty() }?.let { File(it) }
        val primary = video ?: audio ?: return null
        if (!primary.exists() || (video != null && audio != null && !audio.exists())) return null
        val extras = Bundle().apply {
            putString(EXTRA_VIDEO_ID, primary.parentFile?.name)
            if (video != null && audio != null) putString(EXTRA_AUDIO_URL, Uri.fromFile(audio).toString())
        }
        val metadata = item.mediaMetadata.buildUpon().setExtras(extras)
        if (item.mediaMetadata.title == null) {
            metadata.setTitle(download.title)
                .setArtist(download.uploader)
                .setDurationMs(download.durationSeconds * 1000)
        }
        return item.buildUpon()
            .setUri(Uri.fromFile(primary))
            .setMediaMetadata(metadata.build())
            .build()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            scope.launch {
                try {
                    future.set(mediaItems.map { resolve(it) }.toMutableList())
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    mediaSession.sendError(
                        controller,
                        SessionError(SessionError.ERROR_IO, e.message ?: "Failed to load video"),
                    )
                    future.setException(e)
                }
            }
            return future
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 500L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val MAX_RECOVERY_ATTEMPTS = 2
        const val LIVE_EDGE_GAP_MS = 10_000L
        const val TAG = "PlaybackService"
    }
}
