package com.globalmmorpg.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GameColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentGold,
    background = BackgroundDark,
    surface = PanelDark,
    onPrimary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun GlobalMMORPGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = GameTypography,
        content = content
    )
}
