package com.ztube.data.feed

import com.ztube.data.extractor.VideoItem
import com.ztube.data.extractor.YoutubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val repository: YoutubeRepository,
) {

    private val mutex = Mutex()
    private val byChannel = mutableMapOf<String, List<VideoItem>>()

    suspend fun feed(channelUrls: List<String>, force: Boolean): List<VideoItem> = mutex.withLock {
        if (force) byChannel.clear()
        val limit = Semaphore(CONCURRENCY)
        val fetched = coroutineScope {
            channelUrls
                .filter { it !in byChannel }
                .map { url ->
                    async {
                        limit.withPermit {
                            url to try {
                                repository.channelUploads(url).items.take(PER_CHANNEL)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
                .awaitAll()
        }
        fetched.forEach { (url, videos) -> if (videos != null) byChannel[url] = videos }
        channelUrls
            .flatMap { byChannel[it].orEmpty() }
            .distinctBy { it.url }
            .sortedByDescending { it.uploadedAtMillis ?: Long.MIN_VALUE }
    }

    private companion object {
        const val CONCURRENCY = 4
        const val PER_CHANNEL = 10
    }
}
