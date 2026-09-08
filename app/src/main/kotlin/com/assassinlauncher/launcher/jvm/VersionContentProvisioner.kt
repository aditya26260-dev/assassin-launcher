package com.assassinlauncher.launcher.jvm

import android.content.Context
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager

/**
 * Pulls the version-specific download step out of GameLaunchOrchestrator so
 * it can be triggered when a profile is created/edited (instance settings),
 * not only when the user actually hits Play. GameLaunchOrchestrator's own
 * calls to ensureLibraries/ensureClientJar/ensureNatives are unchanged and
 * stay in place - they're already "skip if already present" (see
 * LibraryDownloader and AndroidLwjglProvider's own doc comments), so calling
 * this earlier just means Play-time hits the fast, already-downloaded path
 * instead of downloading for the first time. If this fails or is skipped
 * for any reason, launch() still works exactly as it does today - this is
 * an earlier trigger for the same idempotent work, not a replacement for it.
 *
 * Deliberately doesn't touch JVM provisioning - that's not version-specific
 * (one JRE serves every instance), so it belongs in first-launch setup
 * instead. See FirstLaunchViewModel.
 */
class VersionContentProvisioner(private val context: Context) {

    private val instanceDirs = InstanceDirectoryManager(context)
    private val libraryDownloader = LibraryDownloader(instanceDirs.librariesDir, instanceDirs.versionsDir)
    private val versionClient = MinecraftVersionClient()
    private val lwjglProvider = AndroidLwjglProvider(context)

    /** Best-effort: failures here aren't surfaced as a blocking error to
     * the person editing their instance settings, since GameLaunchOrchestrator
     * will retry the exact same idempotent calls at Play time regardless -
     * worst case a network hiccup here just means the download happens at
     * Play time like it always used to, not a broken instance. */
    suspend fun ensureVersionContent(minecraftVersion: String): Result<Unit> = runCatching {
        val summaries = versionClient.fetchVersionManifest().getOrThrow()
        val summary = summaries.firstOrNull { it.id == minecraftVersion }
            ?: error("Version $minecraftVersion not found in the manifest")
        val details = versionClient.fetchVersionDetails(summary.url).getOrThrow()

        if (lwjglProvider.isLwjgl2Version(details.libraries)) {
            // Same restriction as GameLaunchOrchestrator - nothing to
            // provision for a version this project can't launch anyway.
            return@runCatching
        }

        val nonLwjglLibraries = lwjglProvider.withoutLwjgl(details.libraries)
        libraryDownloader.ensureLibraries(nonLwjglLibraries).getOrThrow()
        libraryDownloader.ensureClientJar(details).getOrThrow()
        lwjglProvider.classpathJarPaths() // extracts the vendored LWJGL jars if not already present
        lwjglProvider.ensureNatives()
    }
}
