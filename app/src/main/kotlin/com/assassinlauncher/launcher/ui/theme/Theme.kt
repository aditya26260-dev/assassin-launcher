package com.assassinlauncher.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder palette. Phase 3 (visual identity) replaces this with the
// actual considered palette and typography for Assassin Launcher - this
// exists so the app has a working, coherent theme in the meantime rather
// than default Material colors.
private val LauncherBackground = Color(0xFF18181C)
private val LauncherSurface = Color(0xFF222226)
private val LauncherAccent = Color(0xFFD23C3C)

private val LauncherColorScheme = darkColorScheme(
    primary = LauncherAccent,
    background = LauncherBackground,
    surface = LauncherSurface,
)

@Composable
fun AssassinLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColorScheme,
        content = content
    )
}
