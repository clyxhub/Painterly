package com.painterly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = PaperElevated,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Ink,
    secondary = Sage,
    onSecondary = PaperElevated,
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = InkSoft,
    outline = InkFaint,
    outlineVariant = PaperDeep,
)

private val DarkColors = darkColorScheme(
    primary = TerracottaSoft,
    onPrimary = DeepNight,
    primaryContainer = Terracotta,
    onPrimaryContainer = NightInk,
    secondary = Sage,
    onSecondary = DeepNight,
    background = DeepNight,
    onBackground = NightInk,
    surface = DeepNightElevated,
    onSurface = NightInk,
    surfaceVariant = DeepNightElevated,
    onSurfaceVariant = NightInkSoft,
    outline = NightInkSoft,
    outlineVariant = DeepNightElevated,
)

@Composable
fun PainterlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PainterlyTypography,
        content = content,
    )
}
