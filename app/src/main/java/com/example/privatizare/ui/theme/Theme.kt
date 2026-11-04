package com.example.privatizare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoverColorScheme = darkColorScheme(
    primary = CoverRed,
    onPrimary = Color.White,
    secondary = CoverYellow,
    onSecondary = CoverDark,
    tertiary = CoverSky,
    onTertiary = Color.White,
    background = CoverDark,
    onBackground = CoverCream,
    surface = Color(0xFF3E2F2B),
    onSurface = CoverCream,
    surfaceVariant = CoverNight,
    onSurfaceVariant = CoverCream,
    outline = CoverSky,
)

/** Tema jocului: culorile copertei (roșu, galben, albastru de noapte, maro închis). */
@Composable
fun PrivatizareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoverColorScheme,
        typography = Typography,
        content = content,
    )
}
