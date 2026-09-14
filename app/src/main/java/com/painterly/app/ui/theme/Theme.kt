package com.painterly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Ochre,
    onPrimary = Ink,
    secondary = StudioTeal,
    onSecondary = Ink,
    tertiary = Terracotta,
    background = Charcoal,
    onBackground = WarmIvory,
    surface = CharcoalSurface,
    onSurface = WarmIvory,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = WarmIvoryDim,
    outline = CanvasEdge,
)

private val LightOnSurfaceVariant = Color(0xFF5A5245)

private val LightColors = lightColorScheme(
    primary = OchreDim,
    onPrimary = Paper,
    secondary = StudioTeal,
    onSecondary = Paper,
    tertiary = Terracotta,
    background = Paper,
    onBackground = Ink,
    surface = WarmIvory,
    onSurface = Ink,
    surfaceVariant = WarmIvoryDim,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = CanvasEdge,
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
