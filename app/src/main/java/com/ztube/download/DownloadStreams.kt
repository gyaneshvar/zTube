package com.ztube.download

import com.ztube.playback.StreamSelector
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

data class DownloadOption(val height: Int, val label: String, val sizeBytes: Long)

class DownloadSelection(val video: VideoStream?, val audio: AudioStream?)

object DownloadStreams {

    private const val MIN_HEIGHT = 144
    private const val MAX_HEIGHT = 1080

    fun options(info: StreamInfo): List<DownloadOption> {
        if (!info.dashMpdUrl.isNullOrBlank()) return emptyList()
        val videos = StreamSelector.heights(info)
            .filter { it in MIN_HEIGHT..MAX_HEIGHT }
            .reversed()
            .mapNotNull { height ->
                val selection = select(info, height) ?: return@mapNotNull null
                DownloadOption(height, "${height}p", size(selection, info.duration))
            }
        val audio = StreamSelector.bestAudio(info)
            ?.let { DownloadOption(0, "Audio only", size(DownloadSelection(null, it), info.duration)) }
        return videos + listOfNotNull(audio)
    }

    fun select(info: StreamInfo, height: Int): DownloadSelection? {
        val audio = StreamSelector.bestAudio(info)
        if (height == 0) return audio?.let { DownloadSelection(null, it) }
        val videoOnly = StreamSelector.bestVideoOnly(info, height)
        if (videoOnly != null && audio != null) return DownloadSelection(videoOnly, audio)
        return StreamSelector.bestMuxed(info, height)?.let { DownloadSelection(it, null) }
    }

    private fun size(selection: DownloadSelection, durationSeconds: Long): Long =
        listOfNotNull<Stream>(selection.video, selection.audio).sumOf { estimate(it, durationSeconds) }

    private fun estimate(stream: Stream, durationSeconds: Long): Long {
        val itag: ItagItem? = stream.itagItem
        if (itag != null && itag.contentLength > 0) return itag.contentLength
        val bitrate = when (stream) {
            is VideoStream -> stream.bitrate
            is AudioStream -> stream.averageBitrate
            else -> 0
        }
        return if (bitrate > 0) bitrate / 8L * durationSeconds else 0
    }
}
