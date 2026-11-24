package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.editor.CodeSyntaxPalette
import com.example.model.AccentPreset
import com.example.model.ThemeMode

@Immutable
data class CodeCanvasExtras(
    val isDark: Boolean,
    val editorBg: Color,
    val editorGutterBg: Color,
    val subtleBorder: Color,
    val secondaryGlow: Color,
    val syntaxPalette: CodeSyntaxPalette
)

val LocalCodeCanvasExtras = staticCompositionLocalOf {
    CodeCanvasExtras(
        isDark = true,
        editorBg = CharcoalBg,
        editorGutterBg = CharcoalEditorGutter,
        subtleBorder = CharcoalBorder,
        secondaryGlow = ElectricViolet,
        syntaxPalette = CodeSyntaxPalette.Dark
    )
}

private val CodeCanvasShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun CodeCanvasTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.ELECTRIC_BLUE,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accentPreset.primaryDark,
            onPrimary = Color(0xFF061022),
            primaryContainer = accentPreset.primaryDark.copy(alpha = 0.18f),
            onPrimaryContainer = accentPreset.primaryDark,
            secondary = accentPreset.secondaryDark,
            onSecondary = Color(0xFF0D0920),
            secondaryContainer = accentPreset.secondaryDark.copy(alpha = 0.18f),
            onSecondaryContainer = accentPreset.secondaryDark,
            tertiary = SuccessEmerald,
            background = CharcoalBg,
            onBackground = CharcoalTextPrimary,
            surface = CharcoalSurface,
            onSurface = CharcoalTextPrimary,
            surfaceVariant = CharcoalSurfaceElevated,
            onSurfaceVariant = CharcoalTextSecondary,
            surfaceContainerLowest = CharcoalBg,
            surfaceContainerLow = CharcoalSurface,
            surfaceContainer = CharcoalSurfaceElevated,
            surfaceContainerHigh = CharcoalSurfaceHigh,
            outline = CharcoalBorder,
            outlineVariant = CharcoalBorder.copy(alpha = 0.65f),
            error = DangerCoral,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentPreset.primaryLight,
            onPrimary = Color.White,
            primaryContainer = accentPreset.primaryLight.copy(alpha = 0.12f),
            onPrimaryContainer = accentPreset.primaryLight,
            secondary = accentPreset.secondaryLight,
            onSecondary = Color.White,
            secondaryContainer = accentPreset.secondaryLight.copy(alpha = 0.12f),
            onSecondaryContainer = accentPreset.secondaryLight,
            tertiary = SuccessEmerald,
            background = LightBg,
            onBackground = LightTextPrimary,
            surface = LightSurface,
            onSurface = LightTextPrimary,
            surfaceVariant = LightSurfaceElevated,
            onSurfaceVariant = LightTextSecondary,
            surfaceContainerLowest = LightSurface,
            surfaceContainerLow = LightBg,
            surfaceContainer = LightSurfaceElevated,
            surfaceContainerHigh = LightSurfaceHigh,
            outline = LightBorder,
            outlineVariant = LightBorder.copy(alpha = 0.7f),
            error = DangerCoral,
            onError = Color.White
        )
    }

    val extras = if (useDark) {
        CodeCanvasExtras(
            isDark = true,
            editorBg = CharcoalBg,
            editorGutterBg = CharcoalEditorGutter,
            subtleBorder = CharcoalBorder,
            secondaryGlow = accentPreset.secondaryDark,
            syntaxPalette = CodeSyntaxPalette.Dark
        )
    } else {
        CodeCanvasExtras(
            isDark = false,
            editorBg = LightSurface,
            editorGutterBg = LightEditorGutter,
            subtleBorder = LightBorder,
            secondaryGlow = accentPreset.secondaryLight,
            syntaxPalette = CodeSyntaxPalette.Light
        )
    }

    CompositionLocalProvider(LocalCodeCanvasExtras provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CodeCanvasTypography,
            shapes = CodeCanvasShapes,
            content = content
        )
    }
}
