package com.ztube.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@OptIn(UnstableApi::class)
class StreamMediaSourceFactory(
    private val dataSourceFactory: DataSource.Factory,
    private val delegate: DefaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory),
) : MediaSource.Factory by delegate {

    private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audioUrl = mediaItem.mediaMetadata.extras?.getString(EXTRA_AUDIO_URL)
            ?: return delegate.createMediaSource(mediaItem)
        val video = progressive.createMediaSource(mediaItem)
        val audio = progressive.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(video, audio)
    }
}
