package com.ztube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFE53935)

private val DarkColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF0F0F0F),
)

private val LightColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
)

@Composable
fun ZTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
