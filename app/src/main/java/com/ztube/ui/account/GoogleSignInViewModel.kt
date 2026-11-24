package com.ztube.ui.account

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.account.AuthResult
import com.ztube.data.account.GoogleAuth
import com.ztube.data.account.SignInResult
import com.ztube.data.account.YoutubeAccountManager
import com.ztube.data.account.message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DifferentAccountPrompt(val oldEmail: String, val newEmail: String, val token: String)

@HiltViewModel
class GoogleSignInViewModel @Inject constructor(
    private val accountManager: YoutubeAccountManager,
    private val googleAuth: GoogleAuth,
) : ViewModel() {

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working

    private val _differentAccount = MutableStateFlow<DifferentAccountPrompt?>(null)
    val differentAccount: StateFlow<DifferentAccountPrompt?> = _differentAccount

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _resolutions = Channel<PendingIntent>(Channel.BUFFERED)
    val resolutions: Flow<PendingIntent> = _resolutions.receiveAsFlow()

    fun signIn(email: String?) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            handle(googleAuth.authorize(email))
        }
    }

    fun onAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            handle(googleAuth.completeFromIntent(data))
        }
    }

    fun confirmDifferentAccount(keepItems: Boolean) {
        val prompt = _differentAccount.value ?: return
        _differentAccount.value = null
        viewModelScope.launch {
            _working.value = true
            accountManager.signOut(keepItems)
            handleToken(prompt.token)
        }
    }

    fun dismissDifferentAccount() {
        _differentAccount.value = null
    }

    private suspend fun handle(result: AuthResult) {
        when (result) {
            is AuthResult.Token -> handleToken(result.token)
            is AuthResult.NeedsResolution -> _resolutions.send(result.pendingIntent)
            AuthResult.Cancelled -> _working.value = false
            is AuthResult.Failure -> {
                _messages.send(failureMessage(result.error))
                _working.value = false
            }
        }
    }

    private suspend fun handleToken(token: String) {
        _working.value = true
        when (val result = accountManager.onInteractiveTokenReceived(token)) {
            is SignInResult.Done -> _messages.send(result.sync.message())
            is SignInResult.DifferentAccount ->
                _differentAccount.value = DifferentAccountPrompt(result.oldEmail, result.newEmail, token)
            is SignInResult.Failed -> _messages.send(result.message)
        }
        _working.value = false
    }

    private fun failureMessage(error: Exception): String {
        val text = error.message.orEmpty().lowercase()
        return if ("access_denied" in text || "access denied" in text || "access blocked" in text || "test user" in text) {
            "This Google account isn't a tester for this build."
        } else {
            error.message ?: "Sign-in failed, try again"
        }
    }
}
