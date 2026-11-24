package com.ztube.data.feed

import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val repository: YoutubeRepository,
) {

    private val mutex = Mutex()
    private val byChannel = ConcurrentHashMap<String, List<VideoItem>>()

    fun feed(channelUrls: List<String>, force: Boolean): Flow<List<VideoItem>> = channelFlow {
        mutex.withLock {
            if (force) byChannel.clear()
            val pending = channelUrls.filter { !byChannel.containsKey(it) }
            if (pending.size < channelUrls.size) send(merged(channelUrls))
            val limit = Semaphore(CONCURRENCY)
            coroutineScope {
                pending.forEach { url ->
                    launch {
                        val videos = limit.withPermit {
                            try {
                                repository.channelUploads(url).items.take(PER_CHANNEL)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (videos != null) {
                            byChannel[url] = videos
                            send(merged(channelUrls))
                        }
                    }
                }
            }
        }
    }

    private fun merged(channelUrls: List<String>): List<VideoItem> = channelUrls
        .flatMap { byChannel[it].orEmpty() }
        .distinctBy { it.url }
        .sortedByDescending { it.uploadedAtMillis ?: Long.MIN_VALUE }

    private companion object {
        const val CONCURRENCY = 8
        const val PER_CHANNEL = 10
    }
}
