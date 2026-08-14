package com.assassinlauncher.launcher.mods

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class InstalledMod(
    val id: String,
    val displayName: String,
    val description: String?,
    val version: String?,
    val fileName: String,
    val enabled: Boolean
)

/**
 * Scans an instance's mods folder and builds real InstalledMod entries.
 * Fabric mods carry a fabric.mod.json inside the jar with real name/
 * description/version - parsed directly, not guessed. Forge/NeoForge use
 * a TOML manifest (mods.toml/neoforge.mods.toml) that isn't parsed yet -
 * no TOML parser in the project currently, so those mods fall back to a
 * cleaned-up filename instead of fabricated metadata. Stated here rather
 * than silently treating a filename guess as if it were real manifest data.
 */
object ModScanner {

    private const val DISABLED_SUFFIX = ".disabled"

    fun scan(modsDir: File): List<InstalledMod> {
        if (!modsDir.exists()) return emptyList()
        val jarFiles = modsDir.listFiles { file ->
            file.name.endsWith(".jar") || file.name.endsWith(".jar$DISABLED_SUFFIX")
        } ?: emptyArray()

        return jarFiles.map { file ->
            val enabled = !file.name.endsWith(DISABLED_SUFFIX)
            val realFileName = if (enabled) file.name else file.name.removeSuffix(DISABLED_SUFFIX)
            val fabricInfo = runCatching { readFabricModInfo(file) }.getOrNull()

            InstalledMod(
                id = fabricInfo?.id ?: realFileName.removeSuffix(".jar"),
                displayName = fabricInfo?.name ?: cleanedFileName(realFileName),
                description = fabricInfo?.description,
                version = fabricInfo?.version,
                fileName = realFileName,
                enabled = enabled
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    /** Renames to/from .disabled rather than deleting, per the brief's own
     * instruction for how mod enable/disable should work throughout. */
    fun setEnabled(modsDir: File, mod: InstalledMod, enabled: Boolean) {
        val currentFile = File(
            modsDir,
            if (mod.enabled) mod.fileName else mod.fileName + DISABLED_SUFFIX
        )
        val targetFile = File(
            modsDir,
            if (enabled) mod.fileName else mod.fileName + DISABLED_SUFFIX
        )
        if (currentFile != targetFile && currentFile.exists()) {
            currentFile.renameTo(targetFile)
        }
    }

    private data class FabricModInfo(
        val id: String?,
        val name: String?,
        val description: String?,
        val version: String?
    )

    private fun readFabricModInfo(jarFile: File): FabricModInfo? {
        ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry("fabric.mod.json") ?: return null
            val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            return FabricModInfo(
                id = json.optString("id").takeIf { it.isNotBlank() },
                name = json.optString("name").takeIf { it.isNotBlank() },
                description = json.optString("description").takeIf { it.isNotBlank() },
                version = json.optString("version").takeIf { it.isNotBlank() }
            )
        }
    }

    /** Best-effort readable name from a filename when no real manifest data
     * exists - "sodium-fabric-0.5.8.jar" becomes "sodium fabric 0.5.8", not
     * a fabricated description. */
    private fun cleanedFileName(fileName: String): String =
        fileName.removeSuffix(".jar").replace(Regex("""[_-]"""), " ")
}
