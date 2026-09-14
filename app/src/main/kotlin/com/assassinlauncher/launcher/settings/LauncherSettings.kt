package com.assassinlauncher.launcher.settings

import com.assassinlauncher.launcher.hardware.ManualRendererOverride

/** Launcher-wide defaults. A profile's own settings (GameProfile) always
 * win when set - these only apply where a profile leaves something null,
 * same "null means automatic" convention GameProfile itself already uses. */
data class LauncherSettings(
    val defaultRendererOverride: ManualRendererOverride? = null,
    val defaultMaxRamMb: Int? = null,
    val showDetailedNotification: Boolean = true
)
