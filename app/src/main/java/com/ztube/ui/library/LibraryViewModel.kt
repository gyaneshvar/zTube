package com.ztube.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.DownloadDao
import com.ztube.data.db.DownloadEntity
import com.ztube.data.db.HistoryDao
import com.ztube.data.db.HistoryEntity
import com.ztube.data.db.PlaylistDao
import com.ztube.data.db.PlaylistEntity
import com.ztube.data.db.PlaylistSummary
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.SubscriptionEntity
import com.ztube.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val subscriptionDao: SubscriptionDao,
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val history: StateFlow<List<HistoryEntity>> = historyDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistSummary>> = playlistDao.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = subscriptionDao.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val hiddenDownloads = MutableStateFlow<Set<String>>(emptySet())

    val downloads: StateFlow<List<DownloadEntity>> = combine(downloadDao.all(), hiddenDownloads) { all, hidden ->
        all.filter { it.videoUrl !in hidden }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun removeHistory(entry: HistoryEntity) {
        viewModelScope.launch { historyDao.delete(entry.videoUrl) }
    }

    suspend fun restoreHistory(entry: HistoryEntity) = historyDao.record(entry)

    fun clearHistory() {
        viewModelScope.launch { historyDao.clear() }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insert(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        }
    }

    fun hideDownload(videoUrl: String) {
        hiddenDownloads.update { it + videoUrl }
    }

    fun restoreDownload(videoUrl: String) {
        hiddenDownloads.update { it - videoUrl }
    }

    suspend fun deleteDownload(videoUrl: String) {
        downloadManager.delete(videoUrl)
        hiddenDownloads.update { it - videoUrl }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch { downloadManager.retry(download) }
    }

    fun unsubscribe(subscription: SubscriptionEntity) {
        viewModelScope.launch { subscriptionDao.delete(subscription.channelUrl) }
    }

    suspend fun resubscribe(subscription: SubscriptionEntity) = subscriptionDao.insert(subscription)

    fun importSubscriptions(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val channels = withContext(Dispatchers.IO) { readChannels(uri) }
                val inserted = subscriptionDao.insertAll(channels).count { it != -1L }
                "Imported $inserted subscriptions"
            } catch (e: Exception) {
                "Import failed: ${e.message ?: "unreadable file"}"
            }
            _messages.send(message)
        }
    }

    private fun readChannels(uri: Uri): List<SubscriptionEntity> {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            ?: throw IllegalStateException("Cannot open file")
        if (lines.isEmpty()) return emptyList()
        val header = parseCsvLine(lines.first().removePrefix("﻿")).map { it.trim() }
        val urlIndex = header.indexOf("Channel Url").takeIf { it >= 0 } ?: 1
        val titleIndex = header.indexOf("Channel Title").takeIf { it >= 0 } ?: 2
        val now = System.currentTimeMillis()
        return lines.drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val columns = parseCsvLine(line)
                val url = columns.getOrNull(urlIndex)?.trim()?.replaceFirst("http://", "https://")
                if (url.isNullOrEmpty()) return@mapNotNull null
                SubscriptionEntity(
                    channelUrl = url,
                    name = columns.getOrNull(titleIndex)?.trim().orEmpty().ifEmpty { url },
                    avatarUrl = null,
                    subscribedAt = now,
                )
            }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
