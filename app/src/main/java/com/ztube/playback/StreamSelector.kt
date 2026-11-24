package com.ztube.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

const val EXTRA_QUALITY = "ztube.quality"
const val EXTRA_AUDIO_URL = "ztube.audioUrl"
const val EXTRA_VIDEO_ID = "ztube.videoId"
const val EXTRA_VIDEO_MANIFEST = "ztube.videoManifest"
const val EXTRA_AUDIO_MANIFEST = "ztube.audioManifest"

data class ResolvedStream(
    val uri: String,
    val audioUri: String?,
    val isDash: Boolean,
    val isHls: Boolean = false,
    val videoManifest: String? = null,
    val audioManifest: String? = null,
)

object StreamSelector {

    fun isLive(info: StreamInfo): Boolean =
        info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM

    fun heights(info: StreamInfo): List<Int> {
        if (isLive(info) || !info.dashMpdUrl.isNullOrBlank()) return emptyList()
        return (info.videoOnlyStreams + info.videoStreams)
            .filter { it.isProgressive() && it.height > 0 }
            .map { it.height }
            .distinct()
            .sorted()
    }

    fun pickHeight(heights: List<Int>, preferred: Int): Int =
        heights.lastOrNull { it <= preferred } ?: heights.firstOrNull() ?: 0

    fun resolve(info: StreamInfo, preferred: Int): ResolvedStream {
        if (!info.dashMpdUrl.isNullOrBlank()) return ResolvedStream(info.dashMpdUrl, null, true)
        val hlsUrl = info.hlsUrl
        if (isLive(info) && !hlsUrl.isNullOrEmpty()) return ResolvedStream(hlsUrl, null, false, isHls = true)

        val height = pickHeight(heights(info), preferred)
        val audio = bestAudio(info)
        val videoOnly = bestVideoOnly(info, height)
        if (videoOnly != null && audio != null) {
            return ResolvedStream(
                videoOnly.content,
                audio.content,
                false,
                videoManifest = manifest(videoOnly.content, videoOnly.itagItem, info.duration),
                audioManifest = manifest(audio.content, audio.itagItem, info.duration),
            )
        }

        val muxed = bestMuxed(info, height)
            ?: info.videoStreams.firstOrNull { it.isProgressive() }
            ?: throw IllegalStateException("No playable stream")
        return ResolvedStream(muxed.content, null, false)
    }

    fun bestAudio(info: StreamInfo): AudioStream? = info.audioStreams
        .filter { it.isProgressive() }
        .maxWithOrNull(
            compareBy<AudioStream> { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }
                .thenBy { it.format == MediaFormat.M4A }
                .thenBy { it.averageBitrate },
        )

    fun bestVideoOnly(info: StreamInfo, height: Int): VideoStream? = info.videoOnlyStreams
        .filter { it.isProgressive() && it.height == height }
        .maxWithOrNull(compareBy<VideoStream> { codecRank(it.codec) }.thenBy { it.bitrate })

    @OptIn(UnstableApi::class)
    private val av1Hardware: Boolean by lazy {
        try {
            MediaCodecUtil.getDecoderInfo("video/av01", false, false)?.hardwareAccelerated == true
        } catch (e: MediaCodecUtil.DecoderQueryException) {
            false
        }
    }

    private fun codecRank(codec: String?): Int = when {
        codec == null -> 1
        codec.startsWith("avc1") -> 3
        codec.startsWith("vp9") || codec.startsWith("vp09") -> 2
        codec.startsWith("av01") -> if (av1Hardware) 2 else 0
        else -> 1
    }

    private fun manifest(url: String, itag: ItagItem?, durationSeconds: Long): String? {
        if (itag == null) return null
        return try {
            YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itag, durationSeconds)
        } catch (e: Exception) {
            null
        }
    }

    fun bestMuxed(info: StreamInfo, height: Int): VideoStream? = info.videoStreams
        .filter { it.isProgressive() && it.height == height }
        .maxByOrNull { it.bitrate }

    private fun VideoStream.isProgressive() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun AudioStream.isProgressive() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
}
