package com.assassinlauncher.launcher.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class InstalledContent(val fileName: String, val displayName: String)

/**
 * Resource packs and shaders don't carry the kind of manifest mods do
 * (fabric.mod.json etc.) - Minecraft identifies them by file/folder name
 * alone, so this just lists what's actually present rather than trying to
 * extract metadata that doesn't exist in a standard, parseable form.
 */
object ContentScanner {
    suspend fun scan(dir: File): List<InstalledContent> = withContext(Dispatchers.IO) {
        if (!dir.exists()) return@withContext emptyList()
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile || it.isDirectory }
            .map { InstalledContent(it.name, it.name.removeSuffix(".zip")) }
            .sortedBy { it.displayName.lowercase() }
    }
}
