package com.ztube.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.ui.components.LocalSnackbar

@Composable
fun GoogleSignInHost(viewModel: GoogleSignInViewModel) {
    val differentAccount by viewModel.differentAccount.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAuthorizationResult(it.data)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.show(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.resolutions.collect { launcher.launch(IntentSenderRequest.Builder(it).build()) }
    }

    differentAccount?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDifferentAccount,
            title = { Text("Switch account?") },
            text = {
                Text(
                    "You're signed in as ${prompt.oldEmail}. What should happen to the channels and playlists " +
                        "synced from it before signing in as ${prompt.newEmail}? " +
                        "Channels and playlists you added in zTube are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDifferentAccount(keepItems = true) }) { Text("Keep synced items") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissDifferentAccount) { Text("Cancel") }
                    TextButton(onClick = { viewModel.confirmDifferentAccount(keepItems = false) }) { Text("Remove them") }
                }
            },
        )
    }
}
