package com.ztube.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztube.data.prefs.YoutubeAccount
import com.ztube.ui.account.GoogleSignInHost
import com.ztube.ui.account.GoogleSignInViewModel
import com.ztube.ui.components.Avatar
import com.ztube.ui.components.LocalSnackbar
import com.ztube.ui.components.SwitchRow
import com.ztube.ui.components.TitleTopBar
import com.ztube.ui.components.formatTimeAgo
import com.ztube.ui.update.UpdateViewModel

@Composable
fun SettingsScreen(
    update: UpdateViewModel,
    viewModel: SettingsViewModel = hiltViewModel(),
    signIn: GoogleSignInViewModel = hiltViewModel(),
) {
    val sponsorBlock by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val autoplay by viewModel.autoplay.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val syncing by viewModel.working.collectAsStateWithLifecycle()
    val signInWorking by signIn.working.collectAsStateWithLifecycle()
    val checkingUpdate by update.checking.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.show(it) }
    }
    LaunchedEffect(Unit) {
        update.messages.collect { snackbar.show(it) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TitleTopBar("You")
        Text(
            "YouTube account",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        val current = account
        if (current == null) {
            SignedOut(working = signInWorking, onSignIn = { signIn.signIn(null) })
        } else {
            SignedIn(
                account = current,
                working = syncing || signInWorking,
                syncing = syncing,
                onSync = viewModel::syncNow,
                onSignInAgain = { signIn.signIn(current.email) },
                onSignOut = viewModel::signOut,
            )
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        SwitchRow(
            title = "Skip sponsor segments",
            checked = sponsorBlock,
            onCheckedChange = viewModel::setSponsorBlockEnabled,
        )
        SwitchRow(
            title = "Autoplay",
            subtitle = "Play the next video when one ends",
            checked = autoplay,
            onCheckedChange = viewModel::setAutoplay,
        )
        TextButton(
            onClick = {
                viewModel.sendDebugReport { report ->
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, "Send debug log"))
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Send debug log") }
        Text(
            "About",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("zTube ${update.currentVersion}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = update::checkNow, enabled = !checkingUpdate) { Text("Check for updates") }
        }
    }

    GoogleSignInHost(signIn)
}

@Composable
private fun SignedOut(working: Boolean, onSignIn: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = onSignIn, enabled = !working) { Text("Sign in with Google") }
        Text(
            "Imports your subscriptions and playlists. Read-only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SignedIn(
    account: YoutubeAccount,
    working: Boolean,
    syncing: Boolean,
    onSync: () -> Unit,
    onSignInAgain: () -> Unit,
    onSignOut: (Boolean) -> Unit,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val name = account.channelTitle ?: account.email

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = account.channelAvatar, name = name, size = 44.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    account.lastSyncedAt?.let { "Last synced ${formatTimeAgo(it)}" } ?: "Not synced yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSync, enabled = !working && !account.needsReauth) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text("Sync now", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (account.needsReauth) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Sign in again to keep syncing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSignInAgain, enabled = !working) { Text("Sign in again") }
                }
            }
        }
        OutlinedButton(onClick = { confirmSignOut = true }, enabled = !working) { Text("Sign out") }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "What should happen to the channels and playlists synced from your YouTube account? " +
                        "Channels and playlists you added in zTube are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut(true)
                    },
                ) { Text("Keep synced items") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            confirmSignOut = false
                            onSignOut(false)
                        },
                    ) { Text("Remove them") }
                }
            },
        )
    }
}
