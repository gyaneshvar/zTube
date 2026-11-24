package com.ztube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val sponsorBlockEnabled: StateFlow<Boolean> = settings.sponsorBlockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoplay: StateFlow<Boolean> = settings.autoplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSponsorBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setSponsorBlockEnabled(enabled) }
    }

    fun setAutoplay(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoplay(enabled) }
    }
}
