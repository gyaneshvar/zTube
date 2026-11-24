package com.ztube.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun UpdateHost(viewModel: UpdateViewModel, blocked: Boolean) {
    val release by viewModel.release.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(blocked) {
        if (!blocked) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.checkOnOpen() }
        }
    }

    val current = release
    if (current != null && !blocked) {
        AlertDialog(
            onDismissRequest = viewModel::later,
            title = { Text("Update available") },
            text = {
                Column {
                    Text("zTube ${current.version} is available (you have ${viewModel.currentVersion}).")
                    if (current.notes.isNotEmpty()) {
                        Column(Modifier.padding(top = 12.dp).heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            Text(current.notes)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.downloadUrl)))
                    }
                    viewModel.dismiss()
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::later) { Text("Later") }
            },
        )
    }
}
