package com.assassinlauncher.launcher.hardware

import android.content.Context
import com.assassinlauncher.launcher.nativebridge.NativeBridge
import org.json.JSONObject
import java.io.File

data class TurnipDriverVariant(
    val assetDir: String,
    val name: String,
    val libraryName: String,
    val minApi: Int,
    val driverVersion: String,
    /** Which exact chip models this variant covers. Parsed from our own
     * asset directory naming convention (e.g. "adreno-7xx-710-720-722"),
     * not from the upstream meta.json's free-text "name" field - that
     * text isn't guaranteed to follow a parseable format across different
     * driver sources, but the directory name is ours to control. */
    val supportedModels: List<Int>
)

private val modelNumberRegex = Regex("""\d{3}""")

private fun parseSupportedModels(assetDirName: String): List<Int> =
    modelNumberRegex.findAll(assetDirName).mapNotNull { it.value.toIntOrNull() }.toList()

/**
 * Resolves a bundled Turnip driver from assets/drivers/ into the internal-
 * storage location libadrenotools requires, and runs a real load attempt
 * through it. Reads each variant's own meta.json rather than hardcoding
 * its facts a second time in Kotlin - the file already has them, and the
 * reference implementation studied (MojoLauncher's adrenotools branch)
 * uses the same schema, not a coincidence.
 */
class TurnipDriverManager(private val context: Context) {

    private val driverInternalDir: File
        get() = File(context.filesDir, "drivers").apply { mkdirs() }

    /** Scans assets/drivers/ for bundled variants. Currently just the one
     * 710/720/722 build, but written to handle more without changes once
     * they're added the same way. */
    fun listBundledVariants(): List<TurnipDriverVariant> {
        val assetManager = context.assets
        val driverDirs = assetManager.list("drivers") ?: return emptyList()
        return driverDirs.mapNotNull { dirName ->
            runCatching {
                val metaJsonText = assetManager.open("drivers/$dirName/meta.json")
                    .bufferedReader().use { it.readText() }
                val json = JSONObject(metaJsonText)
                TurnipDriverVariant(
                    assetDir = dirName,
                    name = json.getString("name"),
                    libraryName = json.getString("libraryName"),
                    minApi = json.optInt("minApi", 28),
                    driverVersion = json.optString("driverVersion", "unknown"),
                    supportedModels = parseSupportedModels(dirName)
                )
            }.getOrNull()
        }
    }

    /** Extracts a variant's driver library from assets to internal storage
     * if it isn't already there, then attempts to actually load it through
     * libadrenotools. Returns whether the load attempt succeeded. */
    fun tryLoad(variant: TurnipDriverVariant): Boolean {
        if (android.os.Build.VERSION.SDK_INT < variant.minApi) return false

        val variantDir = File(driverInternalDir, variant.assetDir).apply { mkdirs() }
        val driverFile = File(variantDir, variant.libraryName)
        if (!driverFile.exists()) {
            context.assets.open("drivers/${variant.assetDir}/${variant.libraryName}")
                .use { input ->
                    driverFile.outputStream().use { output -> input.copyTo(output) }
                }
        }

        val tmpDir = File(context.filesDir, "drivers_tmp").apply { mkdirs() }

        return NativeBridge.tryLoadCustomVulkanDriver(
            hookLibDir = context.applicationInfo.nativeLibraryDir,
            customDriverDir = variantDir.absolutePath,
            customDriverName = variant.libraryName,
            tmpLibDir = tmpDir.absolutePath
        )
    }
}
