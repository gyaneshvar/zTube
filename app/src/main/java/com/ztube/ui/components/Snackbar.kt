package com.ztube.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppSnackbar(
    private val host: SnackbarHostState,
    private val scope: CoroutineScope,
) {

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: suspend () -> Unit = {},
        onDismiss: suspend () -> Unit = {},
    ) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            when (host.showSnackbar(message, actionLabel, duration = SnackbarDuration.Short)) {
                SnackbarResult.ActionPerformed -> onAction()
                SnackbarResult.Dismissed -> onDismiss()
            }
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<AppSnackbar> { error("AppSnackbar not provided") }
