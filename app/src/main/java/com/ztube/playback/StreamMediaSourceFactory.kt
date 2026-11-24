package com.ztube.playback

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@OptIn(UnstableApi::class)
class StreamMediaSourceFactory(
    private val dataSourceFactory: DataSource.Factory,
    hlsDataSourceFactory: DataSource.Factory,
    private val delegate: DefaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory),
) : MediaSource.Factory by delegate {

    private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)
    private val dash = DashMediaSource.Factory(dataSourceFactory)
    private val hls = HlsMediaSource.Factory(hlsDataSourceFactory)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) {
            return hls.createMediaSource(mediaItem)
        }
        val extras = mediaItem.mediaMetadata.extras
        val audioUrl = extras?.getString(EXTRA_AUDIO_URL)
            ?: return delegate.createMediaSource(mediaItem)
        val videoManifest = extras.getString(EXTRA_VIDEO_MANIFEST)
        val audioManifest = extras.getString(EXTRA_AUDIO_MANIFEST)
        val audioItem = MediaItem.fromUri(audioUrl)
        if (videoManifest != null && audioManifest != null) {
            try {
                return MergingMediaSource(
                    true,
                    dashSource(videoManifest, mediaItem),
                    dashSource(audioManifest, audioItem),
                )
            } catch (e: Exception) {
                Log.w(TAG, "DASH manifest failed, falling back to progressive", e)
            }
        }
        return MergingMediaSource(
            true,
            progressive.createMediaSource(mediaItem),
            progressive.createMediaSource(audioItem),
        )
    }

    private fun dashSource(manifest: String, item: MediaItem): MediaSource {
        val parsed = DashManifestParser().parse(
            item.localConfiguration?.uri ?: Uri.EMPTY,
            manifest.byteInputStream(),
        )
        return dash.createMediaSource(parsed, item)
    }

    private companion object {
        const val TAG = "StreamMediaSource"
    }
}
