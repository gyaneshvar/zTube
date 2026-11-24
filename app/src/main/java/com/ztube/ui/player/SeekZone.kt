package com.ztube.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SeekZone(
    forward: Boolean,
    onSeek: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentSeek by rememberUpdatedState(onSeek)
    val currentTap by rememberUpdatedState(onTap)
    var seeks by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableStateOf(false) }

    LaunchedEffect(seeks) {
        if (seeks == 0) return@LaunchedEffect
        bubble = true
        delay(BUBBLE_MS)
        bubble = false
    }

    Box(
        modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { currentTap() },
                onDoubleTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentSeek()
                    seeks++
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(bubble, enter = fadeIn(tween(80)), exit = fadeOut(tween(250))) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    if (forward) "+10s" else "-10s",
                    color = Color.White,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

private const val BUBBLE_MS = 600L
