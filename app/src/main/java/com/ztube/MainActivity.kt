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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    .background(Brush.verticalGradient(listOf(Color(0xFFFBF6F2), Color(0xFFFDE7EC))))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            ) {
                SplashContent()
            }
        }
    }

    @Composable
    private fun SplashContent() {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        val photoAlpha by animateFloatAsState(if (started) 1f else 0f, tween(450), label = "photoAlpha")
        val photoScale by animateFloatAsState(if (started) 1f else 0.85f, tween(450), label = "photoScale")
        val textAlpha by animateFloatAsState(
            if (started) 1f else 0f,
            tween(450, delayMillis = 200),
            label = "textAlpha",
        )
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = maxWidth > maxHeight
            val scale = (minOf(maxWidth, maxHeight) / 360.dp).coerceIn(0.85f, 1.35f)
            val photoSize = if (landscape) {
                minOf(maxHeight * 0.6f, maxWidth * 0.36f)
            } else {
                minOf(maxWidth * 0.62f, maxHeight * 0.38f)
            }.coerceAtMost(320.dp)
            if (landscape) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    SplashPhoto(photoSize, photoAlpha, photoScale)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SplashLogo(scale, textAlpha)
                        Spacer(Modifier.height(16.dp))
                        SplashText(scale, textAlpha)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SplashLogo(scale, textAlpha)
                    Spacer(Modifier.height(28.dp))
                    SplashPhoto(photoSize, photoAlpha, photoScale)
                    Spacer(Modifier.height(24.dp))
                    SplashText(scale, textAlpha)
                }
            }
        }
    }

    @Composable
    private fun SplashPhoto(size: Dp, alpha: Float, scale: Float) {
        Image(
            painter = painterResource(R.drawable.splash_ira_face),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .size(size)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape),
        )
    }

    @Composable
    private fun SplashLogo(scale: Float, alpha: Float) {
        Row(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp * scale)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF8A00), Color(0xFFFF2D78), Color(0xFF8E2DE2)),
                        ),
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(1.9f),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "zTube",
                fontSize = 30.sp * scale,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1B4B),
            )
        }
    }

    @Composable
    private fun SplashText(scale: Float, alpha: Float) {
        Column(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Ira's World",
                fontSize = 26.sp * scale,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE91E63),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Little moments. Big smiles.",
                fontSize = 15.sp * scale,
                color = Color(0xFF1E1B4B),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "100% Ad-Free",
                fontSize = 14.sp * scale,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xFFE91E63), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
    }
}
