package com.assassinlauncher.launcher.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.assassinlauncher.launcher.hardware.ManualRendererOverride
import kotlinx.coroutines.flow.first

private val Context.launcherSettingsStore by preferencesDataStore(name = "launcher_settings")

object LauncherSettingsStore {

    private object Keys {
        val DEFAULT_RENDERER = stringPreferencesKey("default_renderer")
        val DEFAULT_MAX_RAM_MB = intPreferencesKey("default_max_ram_mb")
        val SHOW_DETAILED_NOTIFICATION = booleanPreferencesKey("show_detailed_notification")
    }

    suspend fun load(context: Context): LauncherSettings {
        val prefs = context.launcherSettingsStore.data.first()
        val rendererName = prefs[Keys.DEFAULT_RENDERER]
        return LauncherSettings(
            defaultRendererOverride = rendererName?.let {
                runCatching { ManualRendererOverride.valueOf(it) }.getOrNull()
            },
            defaultMaxRamMb = prefs[Keys.DEFAULT_MAX_RAM_MB],
            showDetailedNotification = prefs[Keys.SHOW_DETAILED_NOTIFICATION] ?: true
        )
    }

    suspend fun save(context: Context, settings: LauncherSettings) {
        context.launcherSettingsStore.edit { prefs ->
            if (settings.defaultRendererOverride != null) {
                prefs[Keys.DEFAULT_RENDERER] = settings.defaultRendererOverride.name
            } else {
                prefs.remove(Keys.DEFAULT_RENDERER)
            }
            if (settings.defaultMaxRamMb != null) {
                prefs[Keys.DEFAULT_MAX_RAM_MB] = settings.defaultMaxRamMb
            } else {
                prefs.remove(Keys.DEFAULT_MAX_RAM_MB)
            }
            prefs[Keys.SHOW_DETAILED_NOTIFICATION] = settings.showDetailedNotification
        }
    }
}
