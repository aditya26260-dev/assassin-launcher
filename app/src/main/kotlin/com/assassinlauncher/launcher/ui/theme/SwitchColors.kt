package com.assassinlauncher.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * The unchecked/off state of a default Material3 Switch leans on
 * colorScheme.outline for its border, which is this theme's deliberately
 * subtle hairline color - fine for dividers, too close to the background
 * to read as an interactive control. A real, confirmed contrast bug on a
 * real device, not a hypothetical: "the toggle in its off state barely
 * shows up" - fixed once, here, rather than per call site.
 */
@Composable
fun launcherSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
)
