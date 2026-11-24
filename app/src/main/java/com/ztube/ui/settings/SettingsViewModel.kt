package com.ztube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.account.YoutubeAccountManager
import com.ztube.data.account.message
import com.ztube.data.prefs.SettingsRepository
import com.ztube.data.prefs.YoutubeAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accountManager: YoutubeAccountManager,
) : ViewModel() {

    val sponsorBlockEnabled: StateFlow<Boolean> = settings.sponsorBlockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoplay: StateFlow<Boolean> = settings.autoplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val account: StateFlow<YoutubeAccount?> = accountManager.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun setSponsorBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setSponsorBlockEnabled(enabled) }
    }

    fun setAutoplay(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoplay(enabled) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _working.value = true
            _messages.send(accountManager.syncNow().message())
            _working.value = false
        }
    }

    fun signOut(keepItems: Boolean) {
        viewModelScope.launch {
            _working.value = true
            accountManager.signOut(keepItems)
            _working.value = false
        }
    }
}
