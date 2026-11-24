package com.ztube.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztube.data.account.YoutubeAccountManager
import com.ztube.data.prefs.YoutubeAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountTabViewModel @Inject constructor(
    accountManager: YoutubeAccountManager,
) : ViewModel() {

    val account: StateFlow<YoutubeAccount?> =
        accountManager.account.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
