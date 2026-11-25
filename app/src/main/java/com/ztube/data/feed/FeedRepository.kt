package com.ztube.data.feed

import com.ztube.DebugLog
import com.ztube.data.db.FeedDao
import com.ztube.data.db.toFeedEntity
import com.ztube.data.db.toVideoItem
import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val repository: YoutubeRepository,
    private val feedDao: FeedDao,
) {

    private val mutex = Mutex()
    private val _refreshing = MutableStateFlow(false)

    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    val videos: Flow<List<VideoItem>> = flow {
        var first = true
        emitAll(
            feedDao.feed()
                .debounce { if (first) 0L else DEBOUNCE_MS }
                .distinctUntilChanged()
                .map { rows ->
                    first = false
                    rows.map { it.toVideoItem() }
                },
        )
    }.flowOn(Dispatchers.Default)

    val shorts: Flow<List<VideoItem>> = flow {
        var first = true
        emitAll(
            feedDao.shorts()
                .debounce { if (first) 0L else DEBOUNCE_MS }
                .distinctUntilChanged()
                .map { rows ->
                    first = false
                    rows.map { it.toVideoItem() }
                },
        )
    }.flowOn(Dispatchers.Default)

    suspend fun refresh(force: Boolean, allowFallback: Boolean = true): Boolean? {
        if (force) mutex.lock() else if (!mutex.tryLock()) return null
        _refreshing.value = true
        try {
            feedDao.pruneUnsubscribed()
            val now = System.currentTimeMillis()
            val pending = feedDao.channelStates()
                .filter { force || it.lastFetchedAt == null || now - it.lastFetchedAt > STALE_AFTER_MS }
                .sortedWith(
                    compareBy({ it.lastFetchedAt != null }, { -(it.newestUpload ?: Long.MIN_VALUE) }),
                )
                .map { it.channelUrl }
            DebugLog.log("Feed", "start pending=${pending.size} force=$force")
            if (pending.isEmpty()) return true
            val limit = Semaphore(CONCURRENCY)
            val done = AtomicInteger()
            val succeeded = AtomicInteger()
            coroutineScope {
                pending.forEach { url ->
                    launch {
                        val videos = limit.withPermit {
                            try {
                                fetch(url, allowFallback)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (videos != null) {
                            feedDao.replaceChannel(url, videos.map { it.toFeedEntity(url) }, System.currentTimeMillis())
                            succeeded.incrementAndGet()
                        }
                        DebugLog.log("Feed", "channel done ${done.incrementAndGet()}/${pending.size}")
                    }
                }
            }
            return succeeded.get() > 0
        } finally {
            _refreshing.value = false
            mutex.unlock()
        }
    }

    private suspend fun fetch(url: String, allowFallback: Boolean): List<VideoItem>? {
        try {
            val videos = repository.channelFeed(url).take(PER_CHANNEL)
            if (videos.isNotEmpty()) {
                DebugLog.log("Feed", "feed rss $url")
                return videos
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!allowFallback) throw e
        }
        if (!allowFallback) return null
        DebugLog.log("Feed", "feed fallback $url")
        return repository.channelUploads(url).items.take(PER_CHANNEL)
    }

    private companion object {
        const val CONCURRENCY = 8
        const val PER_CHANNEL = 15
        const val DEBOUNCE_MS = 300L
        val STALE_AFTER_MS = TimeUnit.MINUTES.toMillis(45)
    }
}
