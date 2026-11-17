package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanPrimary,
    secondary = AmberAccent,
    onSecondary = StudioVoid,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberAccent,
    tertiary = VioletTrack,
    onTertiary = StudioVoid,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = VioletTrack,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioSurfaceHighest,
    error = RoseError,
    onError = StudioVoid,
    errorContainer = RoseContainer,
    onErrorContainer = RoseError
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VoxCutStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
