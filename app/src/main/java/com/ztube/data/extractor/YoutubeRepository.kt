package com.ztube.data.extractor

import android.os.SystemClock
import com.ztube.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PagedVideos(
    val items: List<VideoItem>,
    val nextPage: Page?,
)

data class ChannelPage(
    val name: String,
    val avatarUrl: String?,
    val videos: List<VideoItem>,
    val nextPage: Page? = null,
)

fun List<Image>.best(): String? = maxByOrNull { it.height }?.url

@Singleton
class YoutubeRepository @Inject constructor() {

    private val youtube = ServiceList.YouTube

    private class CachedInfo(val info: StreamInfo, val at: Long)

    private val infoCache = object : LinkedHashMap<String, CachedInfo>(CACHE_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedInfo>) = size > CACHE_SIZE
    }

    private val videosTabs = ConcurrentHashMap<String, ListLinkHandler>()

    suspend fun search(query: String): PagedVideos = withContext(Dispatchers.IO) {
        DebugLog.log("Repo", "search")
        val info = SearchInfo.getInfo(youtube, searchHandler(query))
        PagedVideos(info.relatedItems.toVideos(), info.nextPage.validOrNull())
    }

    suspend fun searchMore(query: String, page: Page): PagedVideos = withContext(Dispatchers.IO) {
        DebugLog.log("Repo", "searchMore")
        val more = SearchInfo.getMoreItems(youtube, searchHandler(query), page)
        PagedVideos(more.items.toVideos(), more.nextPage.validOrNull())
    }

    suspend fun streamInfo(url: String, fresh: Boolean = false): StreamInfo = withContext(Dispatchers.IO) {
        if (!fresh) {
            val cached = synchronized(infoCache) { infoCache[url] }
            if (cached != null && SystemClock.elapsedRealtime() - cached.at < CACHE_TTL_MS) {
                DebugLog.log("Repo", "streamInfo cache hit $url")
                return@withContext cached.info
            }
        }
        DebugLog.log("Repo", "streamInfo fetch #${DebugLog.fetches.incrementAndGet()} fresh=$fresh $url")
        StreamInfo.getInfo(youtube, url).also {
            synchronized(infoCache) { infoCache[url] = CachedInfo(it, SystemClock.elapsedRealtime()) }
        }
    }

    fun related(info: StreamInfo): List<VideoItem> {
        DebugLog.log("Repo", "related ${info.relatedItems.size}")
        return info.relatedItems.toVideos()
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        youtube.suggestionExtractor.suggestionList(query)
    }

    suspend fun channelUploads(channelUrl: String): PagedVideos = withContext(Dispatchers.IO) {
        DebugLog.log("Repo", "channelUploads $channelUrl")
        uploads(channelUrl, ChannelInfo.getInfo(youtube, channelUrl))
    }

    suspend fun channelUploadsMore(channelUrl: String, page: Page): PagedVideos = withContext(Dispatchers.IO) {
        DebugLog.log("Repo", "channelUploadsMore $channelUrl")
        val tab = videosTabs[channelUrl] ?: videosTab(ChannelInfo.getInfo(youtube, channelUrl), channelUrl)
            ?: return@withContext PagedVideos(emptyList(), null)
        val more = ChannelTabInfo.getMoreItems(youtube, tab, page)
        PagedVideos(more.items.toVideos(), more.nextPage.validOrNull())
    }

    suspend fun channel(channelUrl: String): ChannelPage = withContext(Dispatchers.IO) {
        DebugLog.log("Repo", "channel $channelUrl")
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        val videos = uploads(channelUrl, info)
        ChannelPage(
            name = info.name.orEmpty(),
            avatarUrl = info.avatars.best(),
            videos = videos.items,
            nextPage = videos.nextPage,
        )
    }

    private fun uploads(channelUrl: String, info: ChannelInfo): PagedVideos {
        val tab = videosTab(info, channelUrl) ?: return PagedVideos(emptyList(), null)
        val tabInfo = ChannelTabInfo.getInfo(youtube, tab)
        return PagedVideos(tabInfo.relatedItems.toVideos(), tabInfo.nextPage.validOrNull())
    }

    private fun videosTab(info: ChannelInfo, channelUrl: String): ListLinkHandler? =
        info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
            ?.also { videosTabs[channelUrl] = it }

    private fun searchHandler(query: String) = youtube.searchQHFactory.fromQuery(
        query,
        listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
        "",
    )

    private fun Page?.validOrNull(): Page? = this?.takeIf { Page.isValid(it) }

    private fun List<InfoItem>.toVideos(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>()
            .map { it.toVideoItem() }
            .distinctBy { it.url }

    private companion object {
        const val CACHE_TTL_MS = 30 * 60 * 1000L
        const val CACHE_SIZE = 4
    }

    private fun StreamInfoItem.toVideoItem() = VideoItem(
        url = url,
        title = name,
        uploader = uploaderName.orEmpty(),
        uploaderUrl = uploaderUrl.orEmpty(),
        thumbnailUrl = thumbnails.best(),
        durationSeconds = duration,
        viewCount = viewCount,
        uploadedText = textualUploadDate,
        uploadedAtMillis = uploadDate?.instant?.toEpochMilli(),
        uploaderAvatarUrl = uploaderAvatars.best(),
    )
}
