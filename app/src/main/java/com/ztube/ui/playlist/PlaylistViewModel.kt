package com.ztube.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.db.PlaylistDao
import com.ztube.data.db.PlaylistEntity
import com.ztube.data.db.PlaylistVideoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    private val id: Long = checkNotNull(savedStateHandle["id"])

    val playlist: StateFlow<PlaylistEntity?> = playlistDao.playlist(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val videos: StateFlow<List<PlaylistVideoEntity>> = playlistDao.videos(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeVideo(video: PlaylistVideoEntity) {
        viewModelScope.launch { playlistDao.removeVideo(id, video.videoUrl) }
    }

    suspend fun restoreVideo(video: PlaylistVideoEntity) = playlistDao.insertVideo(video)

    fun deletePlaylist(onDeleted: (restore: suspend () -> Unit) -> Unit) {
        val entity = playlist.value ?: return
        val items = videos.value
        viewModelScope.launch {
            playlistDao.delete(id)
            onDeleted {
                playlistDao.insert(entity)
                items.forEach { playlistDao.insertVideo(it) }
            }
        }
    }
}
