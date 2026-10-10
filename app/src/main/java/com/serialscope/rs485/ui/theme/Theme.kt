package com.serialscope.rs485.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object ScopeColors {
    val Background = Color(0xFF08111B)
    val Surface = Color(0xFF101C28)
    val SurfaceHigh = Color(0xFF172534)
    val Outline = Color(0xFF25384A)
    val Grid = Color(0xFF1E3247)
    val GridStrong = Color(0xFF2C4A63)
    val TextPrimary = Color(0xFFE6F0F7)
    val TextSecondary = Color(0xFF8FA6BA)
    val Accent = Color(0xFF38E1B0)
    val AccentDim = Color(0xFF1F7F66)
    val Warning = Color(0xFFFFC44D)
    val Danger = Color(0xFFFF7A8B)
    val Info = Color(0xFF6FA8FF)
}

private val ScopeDarkScheme = darkColorScheme(
    primary = ScopeColors.Accent,
    onPrimary = Color(0xFF00281F),
    primaryContainer = ScopeColors.AccentDim,
    onPrimaryContainer = ScopeColors.TextPrimary,
    secondary = ScopeColors.Info,
    onSecondary = Color(0xFF00182E),
    background = ScopeColors.Background,
    onBackground = ScopeColors.TextPrimary,
    surface = ScopeColors.Surface,
    onSurface = ScopeColors.TextPrimary,
    surfaceVariant = ScopeColors.SurfaceHigh,
    onSurfaceVariant = ScopeColors.TextSecondary,
    outline = ScopeColors.Outline,
    error = ScopeColors.Danger,
    onError = Color(0xFF3A0009)
)

private val ScopeTypography = androidx.compose.material3.Typography(
    bodyMedium = TextStyle(fontSize = 14.sp, color = ScopeColors.TextPrimary),
    bodySmall = TextStyle(fontSize = 12.sp, color = ScopeColors.TextSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, color = ScopeColors.TextSecondary),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = ScopeColors.TextPrimary
    )
)

/** Monospace style used for numbers so digits do not jump around. */
val MonoNumberStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    color = ScopeColors.TextPrimary
)

val MonoSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    color = ScopeColors.TextSecondary
)

@Composable
fun ScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScopeDarkScheme,
        typography = ScopeTypography,
        content = content
    )
}