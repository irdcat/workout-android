package com.github.irdcat.workout.application.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Shapes as MaterialShapes

private val ColorScheme = darkColorScheme(
    primary = Colors.Primary,
    onPrimary = Colors.OnPrimary,
    primaryContainer = Colors.PrimaryPressed,
    onPrimaryContainer = Colors.OnPrimary,

    secondary = Colors.Secondary,
    onSecondary = Colors.OnPrimary,

    tertiary = Colors.Tertiary,
    onTertiary = Colors.OnPrimary,

    background = Colors.Canvas,
    onBackground = Colors.TextPrimary,

    surface = Colors.Surface1,
    onSurface = Colors.TextPrimary,
    surfaceVariant = Colors.Surface2,
    onSurfaceVariant = Colors.TextSecondary,
    surfaceContainer = Colors.Surface1,
    surfaceContainerHigh = Colors.Surface2,
    surfaceContainerHighest = Colors.Surface3,

    outline = Colors.Border,
    outlineVariant = Colors.Surface3,

    error = Colors.Critical,
    onError = Colors.OnPrimary,
)

/** Material components (dialogs, menus, text fields, text buttons) pick these up. */
private val MaterialTypography = Typography(
    headlineLarge = KineticType.headlineLg,
    headlineMedium = KineticType.headlineMd,
    headlineSmall = KineticType.headlineMd,
    titleLarge = KineticType.headlineMd,
    titleMedium = KineticType.rowTitle,
    titleSmall = KineticType.bodyNoteStrong,
    bodyLarge = KineticType.bodyLg,
    bodyMedium = KineticType.bodyMd,
    bodySmall = KineticType.bodySm,
    labelLarge = KineticType.bodyNoteStrong,
    labelMedium = KineticType.bodySm,
    labelSmall = KineticType.labelCaps,
)

private val MaterialShapeScale = MaterialShapes(
    extraSmall = Shapes.md,
    small = Shapes.lg,
    medium = Shapes.xl,
    large = Shapes.xl,
    extraLarge = Shapes.xl,
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = MaterialTypography,
        shapes = MaterialShapeScale,
        content = content,
    )
}
