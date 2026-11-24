package com.ztube.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.update.Release
import com.ztube.data.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
) : ViewModel() {

    val currentVersion: String get() = checker.currentVersion

    private val _release = MutableStateFlow<Release?>(null)
    val release: StateFlow<Release?> = _release

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun checkOnOpen() {
        if (_checking.value || _release.value != null) return
        _checking.value = true
        viewModelScope.launch {
            try {
                _release.value = checker.latestIfDue()
            } finally {
                _checking.value = false
            }
        }
    }

    fun checkNow() {
        if (_checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val latest = checker.latest()
                when {
                    latest == null -> _messages.send("Couldn't check for updates")
                    checker.isNewer(latest) -> _release.value = latest
                    else -> _messages.send("You're on the latest version")
                }
            } finally {
                _checking.value = false
            }
        }
    }

    fun later() {
        val release = _release.value ?: return
        _release.value = null
        viewModelScope.launch { checker.snooze(release) }
    }

    fun dismiss() {
        _release.value = null
    }
}
