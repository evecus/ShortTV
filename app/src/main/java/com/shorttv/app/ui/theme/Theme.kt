package com.shorttv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = TvPrimary,
    onPrimary = TvOnPrimary,
    primaryContainer = TvPrimaryContainer,
    onPrimaryContainer = TvOnBackground,
    background = TvBackground,
    onBackground = TvOnBackground,
    surface = TvSurface,
    onSurface = TvOnBackground,
    surfaceVariant = TvSurfaceVariant,
    onSurfaceVariant = TvOnSurfaceVariant,
    outline = TvOutline,
    error = LiveRed
)

private val TvTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp)
)

@Composable
fun ShortTVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // 电视端始终使用深色主题，保证观感一致
    MaterialTheme(
        colorScheme = DarkColors,
        typography = TvTypography,
        content = content
    )
}
