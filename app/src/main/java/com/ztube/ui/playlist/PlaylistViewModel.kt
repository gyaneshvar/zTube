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

    fun removeVideo(videoUrl: String) {
        viewModelScope.launch { playlistDao.removeVideo(id, videoUrl) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistDao.delete(id)
            onDeleted()
        }
    }
}
