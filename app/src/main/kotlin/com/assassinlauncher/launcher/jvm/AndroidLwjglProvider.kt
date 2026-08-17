package com.assassinlauncher.launcher.jvm

import android.content.Context
import java.io.File

/**
 * Reconciles Mojang's version manifest with the fact that Mojang ships no
 * Android LWJGL natives at all - every reference launcher solves this the
 * same way, by substituting a custom Android-compiled LWJGL build for
 * whatever LWJGL entries the manifest lists, rather than downloading them.
 *
 * Extraction follows the exact same pattern as MobileGluesManager /
 * TurnipDriverManager: bundled as an asset, copied to internal storage on
 * first use. These jars are loaded by the embedded game JVM's own
 * classloader via -cp, never by our own Kotlin/Compose code, which is why
 * they're an asset rather than a Gradle dependency (an earlier version of
 * this file's setup used a Gradle fileTree dependency, which would have
 * merged LWJGL's classes into our own app's DEX instead of producing
 * standalone jar files on disk - caught before it shipped, not after).
 *
 * The native .so files were a real, open gap for three sessions - Amethyst's
 * own source repo builds these from SDL2/sdl2-compat git submodules a plain
 * zip export doesn't include. Closed by extracting the real, compiled
 * binaries directly from Amethyst's own released APK (an APK is just a
 * zip), the same legitimacy as every other vendored binary in this project
 * (JDK tarballs, Turnip driver, Krypton Wrapper AAR): an officially
 * published release artifact. The jar set was updated to match too - the
 * APK's real shipped build merges the glfw+opengl modules into one
 * `lwjgl-3.3.3-merged-modules.jar` rather than keeping them separate the
 * way the source repo's dev-time files did, and that's the proven
 * combination this project now uses rather than a guessed recombination.
 *
 * Only covers LWJGL3 (Minecraft 1.13+, Mojang group "org.lwjgl"). Older
 * versions use LWJGL2 ("org.lwjgl.lwjgl") and aren't handled - there's no
 * vendored Android build of it in hand, and this project's scope so far
 * has been modern versions anyway. isLwjgl2Version exists so a caller can
 * fail with a clear message instead of attempting a broken launch.
 */
class AndroidLwjglProvider(private val context: Context) {

    companion object {
        private const val ASSET_DIR = "lwjgl/lwjgl-android-3.3.3"
        private const val NATIVES_ASSET_DIR = "$ASSET_DIR/natives"
        private const val LWJGL3_GROUP_PREFIX = "org.lwjgl:"
        private const val LWJGL2_GROUP_PREFIX = "org.lwjgl.lwjgl:"
    }

    private val internalDir: File
        get() = File(context.filesDir, "lwjgl-android-3.3.3").apply { mkdirs() }

    /** Where the extracted LWJGL native .so files live, once
     * ensureNatives() has run - referenced by the JVM launch args
     * (java.library.path and the per-library org.lwjgl.*.libname
     * overrides). */
    val nativesDir: File
        get() = File(internalDir, "natives").apply { mkdirs() }

    fun isLwjgl3Version(libraries: List<MinecraftLibrary>): Boolean =
        libraries.any { it.name.startsWith(LWJGL3_GROUP_PREFIX) }

    fun isLwjgl2Version(libraries: List<MinecraftLibrary>): Boolean =
        libraries.any { it.name.startsWith(LWJGL2_GROUP_PREFIX) }

    /** The manifest's library list with every LWJGL entry (of either
     * generation) removed, so the generic downloader never fetches
     * Mojang's desktop-only LWJGL jars - including the natives-linux
     * ones, which would otherwise incorrectly pass VersionRuleEvaluator
     * since we present as "linux" at the JVM level. Real bug, not
     * hypothetical: those jars carry glibc x86_64/aarch64 binaries, not
     * Bionic ARM64, and would fail to load even if downloaded. */
    fun withoutLwjgl(libraries: List<MinecraftLibrary>): List<MinecraftLibrary> =
        libraries.filterNot {
            it.name.startsWith(LWJGL3_GROUP_PREFIX) || it.name.startsWith(LWJGL2_GROUP_PREFIX)
        }

    /** Extracts every vendored jar to internal storage if needed and
     * returns their absolute paths, ready to splice into a classpath. */
    fun classpathJarPaths(): List<String> = extractAssetDir(ASSET_DIR, internalDir) { it.endsWith(".jar") }

    /** Extracts every vendored native .so to nativesDir if needed.
     * Doesn't return paths - callers reference nativesDir itself, since
     * that's what java.library.path and dlopen calls actually need. */
    fun ensureNatives() {
        extractAssetDir(NATIVES_ASSET_DIR, nativesDir) { it.endsWith(".so") }
    }

    private fun extractAssetDir(assetDir: String, outDir: File, filter: (String) -> Boolean): List<String> {
        val assetNames = context.assets.list(assetDir)?.filter(filter) ?: return emptyList()
        return assetNames.map { name ->
            val outFile = File(outDir, name)
            if (!outFile.exists()) {
                context.assets.open("$assetDir/$name").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            outFile.absolutePath
        }
    }
}
