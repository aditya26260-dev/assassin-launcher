package com.assassinlauncher.launcher.input

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.cursorSettingsStore by preferencesDataStore(name = "cursor_settings")

object CursorSettingsStore {

    private object Keys {
        val SIZE_DP = intPreferencesKey("size_dp")
        val CUSTOM_IMAGE_PATH = stringPreferencesKey("custom_image_path")
        val COLOR_ARGB = longPreferencesKey("color_argb")
        val DPI_SENSITIVITY = doublePreferencesKey("dpi_sensitivity")
    }

    suspend fun load(context: Context): CursorSettings {
        val prefs = context.cursorSettingsStore.data.first()
        return CursorSettings(
            sizeDp = prefs[Keys.SIZE_DP] ?: 24,
            customImagePath = prefs[Keys.CUSTOM_IMAGE_PATH],
            colorArgb = prefs[Keys.COLOR_ARGB] ?: 0xFFFFFFFF,
            dpiSensitivity = (prefs[Keys.DPI_SENSITIVITY] ?: 1.0).toFloat()
        )
    }

    suspend fun save(context: Context, settings: CursorSettings) {
        context.cursorSettingsStore.edit { prefs ->
            prefs[Keys.SIZE_DP] = settings.sizeDp
            if (settings.customImagePath != null) {
                prefs[Keys.CUSTOM_IMAGE_PATH] = settings.customImagePath
            } else {
                prefs.remove(Keys.CUSTOM_IMAGE_PATH)
            }
            prefs[Keys.COLOR_ARGB] = settings.colorArgb
            prefs[Keys.DPI_SENSITIVITY] = settings.dpiSensitivity.toDouble()
        }
    }
}
