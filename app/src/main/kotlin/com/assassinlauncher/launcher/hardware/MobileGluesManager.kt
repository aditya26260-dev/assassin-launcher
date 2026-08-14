package com.assassinlauncher.launcher.hardware

import android.content.Context
import java.io.File

/**
 * Resolves the bundled MobileGlues library into internal storage and runs
 * a real load attempt, the same real-check pattern TurnipDriverManager
 * uses for the Vulkan side. Mechanism confirmed against Amethyst
 * Launcher's actual source rather than guessed: a standard dlopen, no
 * linker namespace bypass needed (that's specific to the Vulkan/Turnip
 * case, not a general requirement).
 */
class MobileGluesManager(private val context: Context) {

    companion object {
        private const val ASSET_DIR = "drivers/mobileglues-1.3.5"
        private const val LIBRARY_NAME = "libmobileglues.so"
    }

    private val internalDir: File
        get() = File(context.filesDir, "mobileglues").apply { mkdirs() }

    /** MobileGlues reads its own data/config/shader-cache directory from an
     * environment variable at the point the actual JVM/game process starts
     * (confirmed as MG_DIR_PATH in the reference source) - that wiring
     * happens wherever the launch environment gets built, which doesn't
     * exist in this project yet. This just resolves the path it should
     * eventually point to. */
    val dataDir: File
        get() = File(internalDir, "data").apply { mkdirs() }

    fun isBundled(): Boolean =
        runCatching { context.assets.list(ASSET_DIR)?.contains(LIBRARY_NAME) == true }
            .getOrDefault(false)

    /** Extracts the library to internal storage if needed, then attempts a
     * real load through it. Returns whether the load succeeded. */
    fun tryLoad(): Boolean {
        if (!isBundled()) return false

        val libraryFile = File(internalDir, LIBRARY_NAME)
        if (!libraryFile.exists()) {
            context.assets.open("$ASSET_DIR/$LIBRARY_NAME").use { input ->
                libraryFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        return NativeBridge.tryLoadCustomEglLibrary(libraryFile.absolutePath)
    }
}
