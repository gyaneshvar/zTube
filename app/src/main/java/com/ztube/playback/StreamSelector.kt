package com.ztube.playback

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

const val EXTRA_QUALITY = "ztube.quality"
const val EXTRA_AUDIO_URL = "ztube.audioUrl"
const val EXTRA_VIDEO_ID = "ztube.videoId"

data class ResolvedStream(
    val uri: String,
    val audioUri: String?,
    val isDash: Boolean,
)

object StreamSelector {

    fun heights(info: StreamInfo): List<Int> {
        if (!info.dashMpdUrl.isNullOrBlank()) return emptyList()
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

        val height = pickHeight(heights(info), preferred)
        val audio = bestAudio(info)
        val videoOnly = bestVideoOnly(info, height)
        if (videoOnly != null && audio != null) {
            return ResolvedStream(videoOnly.content, audio.content, false)
        }

        val muxed = bestMuxed(info, height)
            ?: info.videoStreams.firstOrNull { it.isProgressive() }
            ?: throw IllegalStateException("No playable stream")
        return ResolvedStream(muxed.content, null, false)
    }

    fun bestAudio(info: StreamInfo): AudioStream? = info.audioStreams
        .filter { it.isProgressive() }
        .maxWithOrNull(compareBy<AudioStream> { it.format == MediaFormat.M4A }.thenBy { it.averageBitrate })

    fun bestVideoOnly(info: StreamInfo, height: Int): VideoStream? = info.videoOnlyStreams
        .filter { it.isProgressive() && it.height == height }
        .maxWithOrNull(compareBy<VideoStream> { it.format == MediaFormat.MPEG_4 }.thenBy { it.bitrate })

    fun bestMuxed(info: StreamInfo, height: Int): VideoStream? = info.videoStreams
        .filter { it.isProgressive() && it.height == height }
        .maxByOrNull { it.bitrate }

    private fun VideoStream.isProgressive() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP

    private fun AudioStream.isProgressive() =
        isUrl && deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP
}
