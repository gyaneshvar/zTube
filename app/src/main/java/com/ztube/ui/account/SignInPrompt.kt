package com.ztube.ui.account

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ztube.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SignInPromptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible

    private var checked = false

    fun check() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            val version = withContext(Dispatchers.IO) {
                PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
            }
            if (settings.youtubeAccount.first() != null || settings.signInPromptVersion() >= version) return@launch
            settings.setSignInPromptVersion(version)
            _visible.value = true
        }
    }

    fun dismiss() {
        _visible.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInPrompt(
    splashVisible: Boolean,
    viewModel: SignInPromptViewModel = hiltViewModel(),
    signIn: GoogleSignInViewModel = hiltViewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()

    LaunchedEffect(splashVisible) {
        if (!splashVisible) viewModel.check()
    }

    GoogleSignInHost(signIn)

    if (visible) {
        ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Bring your YouTube library", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Sign in with Google to import your subscriptions and playlists. Read-only — zTube never " +
                        "changes your account. Works for accounts invited to this build.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        viewModel.dismiss()
                        signIn.signIn(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in with Google") }
                TextButton(onClick = viewModel::dismiss, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
            }
        }
    }
}
