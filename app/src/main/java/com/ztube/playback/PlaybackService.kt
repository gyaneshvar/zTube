package com.ztube.playback

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(StreamMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadSegments(mediaItem)
                recordHistory(mediaItem)
            }

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

    private suspend fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        resolveDownloaded(item)?.let { return it }
        val info = repository.streamInfo(item.mediaId)
        val quality = item.requestMetadata.extras?.getInt(EXTRA_QUALITY, 0)?.takeIf { it > 0 }
            ?: settings.quality.first()
        val stream = StreamSelector.resolve(info, quality)
        val extras = Bundle().apply {
            putString(EXTRA_VIDEO_ID, info.id)
            stream.audioUri?.let { putString(EXTRA_AUDIO_URL, it) }
        }
        return item.buildUpon()
            .setUri(stream.uri)
            .setMimeType(if (stream.isDash) MimeTypes.APPLICATION_MPD else null)
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
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
    }
}
