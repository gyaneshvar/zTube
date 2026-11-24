package com.ztube

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.ztube.playback.PipState
import com.ztube.ui.ZTubeRoot
import com.ztube.ui.theme.ZTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pipState: PipState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log("Main", "onCreate restored=${savedInstanceState != null}")
        enableEdgeToEdge()
        val showSplashInitially = savedInstanceState == null
        setContent {
            ZTubeTheme {
                var splashVisible by rememberSaveable { mutableStateOf(showSplashInitially) }
                var crashReport by remember { mutableStateOf<String?>(null) }
                var crashChecked by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    crashReport = withContext(Dispatchers.IO) { CrashReporter.read(applicationContext) }
                    crashChecked = true
                }
                Box(Modifier.fillMaxSize()) {
                    ZTubeRoot(splashVisible || !crashChecked || crashReport != null)
                    SplashOverlay(splashVisible) { splashVisible = false }
                }
                val report = crashReport
                if (report != null && !splashVisible) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("zTube closed unexpectedly") },
                        text = { Text("Send this report so the problem can be fixed.") },
                        confirmButton = {
                            TextButton(onClick = {
                                crashReport = null
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, report)
                                startActivity(Intent.createChooser(send, "Share report"))
                            }) { Text("Share report") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                crashReport = null
                            }) { Text("Dismiss") }
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            pipState.canEnter.collect { setPictureInPictureParams(pipParams(it)) }
        }
    }

    override fun onDestroy() {
        DebugLog.log("Main", "onDestroy changingConfig=$isChangingConfigurations")
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DebugLog.log("Main", "onConfigurationChanged")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        DebugLog.log("Main", "onTrimMemory level=$level")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipState.canEnter.value) {
            enterPictureInPictureMode(pipParams(false))
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        DebugLog.log("Main", "pip=$isInPictureInPictureMode changingConfig=$isChangingConfigurations")
        pipState.inPip.value = isInPictureInPictureMode
    }

    private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    @Composable
    private fun SplashOverlay(visible: Boolean, onDismiss: () -> Unit) {
        LaunchedEffect(Unit) {
            delay(1500)
            onDismiss()
        }
        AnimatedVisibility(visible = visible, exit = fadeOut(tween(300))) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFBF6F2))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_ira),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
