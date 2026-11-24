package com.ztube.data.extractor

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import javax.inject.Inject
import javax.inject.Singleton

data class ChannelPage(
    val name: String,
    val avatarUrl: String?,
    val videos: List<VideoItem>,
)

@Singleton
class YoutubeRepository @Inject constructor() {

    private val youtube = ServiceList.YouTube

    suspend fun search(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        val handler = youtube.searchQHFactory.fromQuery(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
            "",
        )
        SearchInfo.getInfo(youtube, handler)
            .relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
    }

    private class CachedInfo(val url: String, val info: StreamInfo, val at: Long)

    @Volatile
    private var cachedInfo: CachedInfo? = null

    suspend fun streamInfo(url: String, fresh: Boolean = false): StreamInfo = withContext(Dispatchers.IO) {
        val now = SystemClock.elapsedRealtime()
        if (!fresh) {
            cachedInfo?.takeIf { it.url == url && now - it.at < CACHE_TTL_MS }?.let { return@withContext it.info }
        }
        StreamInfo.getInfo(youtube, url).also { cachedInfo = CachedInfo(url, it, SystemClock.elapsedRealtime()) }
    }

    suspend fun channelUploads(channelUrl: String): List<VideoItem> = withContext(Dispatchers.IO) {
        uploads(ChannelInfo.getInfo(youtube, channelUrl))
    }

    suspend fun channel(channelUrl: String): ChannelPage = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        ChannelPage(
            name = info.name.orEmpty(),
            avatarUrl = info.avatars.firstOrNull()?.url,
            videos = uploads(info),
        )
    }

    private fun uploads(info: ChannelInfo): List<VideoItem> {
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?: return emptyList()
        return ChannelTabInfo.getInfo(youtube, videosTab)
            .relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
    }

    private companion object {
        const val CACHE_TTL_MS = 30 * 60 * 1000L
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        uploader = uploaderName.orEmpty(),
        uploaderUrl = uploaderUrl.orEmpty(),
        thumbnailUrl = thumbnails.firstOrNull()?.url,
        durationSeconds = duration,
        viewCount = viewCount,
        uploadedText = textualUploadDate,
        uploadedAtMillis = uploadDate?.instant?.toEpochMilli(),
    )
}
