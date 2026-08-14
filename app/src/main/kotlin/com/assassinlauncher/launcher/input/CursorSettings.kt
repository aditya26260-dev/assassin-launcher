package com.assassinlauncher.launcher.input

data class CursorSettings(
    val sizeDp: Int = 24,
    val customImagePath: String? = null,
    val colorArgb: Long = 0xFFFFFFFF,
    val dpiSensitivity: Float = 1.0f
)
