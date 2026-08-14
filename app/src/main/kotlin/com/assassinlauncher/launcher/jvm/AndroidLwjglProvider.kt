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
 * Only covers LWJGL3 (Minecraft 1.13+, Mojang group "org.lwjgl"). Older
 * versions use LWJGL2 ("org.lwjgl.lwjgl") and aren't handled - there's no
 * vendored Android build of it in hand, and this project's scope so far
 * has been modern versions anyway. isLwjgl2Version exists so a caller can
 * fail with a clear message instead of attempting a broken launch.
 */
class AndroidLwjglProvider(private val context: Context) {

    companion object {
        private const val ASSET_DIR = "lwjgl/lwjgl-android-3.3.3"
        private const val LWJGL3_GROUP_PREFIX = "org.lwjgl:"
        private const val LWJGL2_GROUP_PREFIX = "org.lwjgl.lwjgl:"
    }

    private val internalDir: File
        get() = File(context.filesDir, "lwjgl-android-3.3.3").apply { mkdirs() }

    /** Where the LWJGL native .so files would live once sourced. Referenced
     * by the JVM launch args (java.library.path and the per-library
     * org.lwjgl.*.libname overrides) regardless of whether anything is
     * actually in it yet - see docs/PROGRESS.md for that gap. */
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
    fun classpathJarPaths(): List<String> {
        val assetNames = context.assets.list(ASSET_DIR)?.filter { it.endsWith(".jar") }
            ?: return emptyList()

        return assetNames.map { name ->
            val outFile = File(internalDir, name)
            if (!outFile.exists()) {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            outFile.absolutePath
        }
    }
}
