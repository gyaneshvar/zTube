package com.ztube.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFFF3B30)

private val DarkColors = darkColorScheme(
    primary = Red,
    onPrimary = Color(0xFF2B0000),
    primaryContainer = Color(0xFF4A1612),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFF2C2C2F),
    onSecondaryContainer = Color(0xFFF1F1F1),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0B0B0C),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272729),
    onSurfaceVariant = Color(0xFFA8A8AD),
    surfaceContainerLowest = Color(0xFF080809),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF18181A),
    surfaceContainerHigh = Color(0xFF222224),
    surfaceContainerHighest = Color(0xFF2C2C2F),
    outline = Color(0xFF6E6E73),
    outlineVariant = Color(0xFF38383B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC8160D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF1B1B1D),
    background = Color.White,
    onBackground = Color(0xFF0F0F10),
    surface = Color.White,
    onSurface = Color(0xFF0F0F10),
    surfaceVariant = Color(0xFFEDEDEF),
    onSurfaceVariant = Color(0xFF5F5F64),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8F9),
    surfaceContainer = Color(0xFFF3F3F5),
    surfaceContainerHigh = Color(0xFFEDEDEF),
    surfaceContainerHighest = Color(0xFFE6E6E8),
    outline = Color(0xFF8B8B90),
    outlineVariant = Color(0xFFDCDCE0),
)

private val Base = Typography()

private val AppTypography = Typography(
    titleLarge = Base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun ZTubeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
