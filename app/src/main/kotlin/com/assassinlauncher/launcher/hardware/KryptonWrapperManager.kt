package com.assassinlauncher.launcher.hardware

import android.content.Context
import com.assassinlauncher.launcher.nativebridge.NativeBridge
import java.io.File
 * linker namespace bypass needed) - Krypton Wrapper is GL4ES-family, same
 * lineage, same EGL-substitution mechanism confirmed in Amethyst's actual
 * source (the exact library name below, libng_gl4es.so, matched what's in
 * their fallback chain).
 */
class KryptonWrapperManager(private val context: Context) {

    companion object {
        private const val ASSET_DIR = "drivers/krypton-wrapper"
        private const val LIBRARY_NAME = "libng_gl4es.so"
    }

    private val internalDir: File
        get() = File(context.filesDir, "krypton_wrapper").apply { mkdirs() }

    fun isBundled(): Boolean =
        runCatching { context.assets.list(ASSET_DIR)?.contains(LIBRARY_NAME) == true }
            .getOrDefault(false)

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
