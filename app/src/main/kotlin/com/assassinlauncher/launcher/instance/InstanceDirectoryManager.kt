package com.assassinlauncher.launcher.instance

import android.content.Context
import java.io.File

/**
 * Builds the shared `.minecraft`-style root plus per-instance folders
 * (architecture 5.7), on the app's own external-files directory rather
 * than shared/public storage. That's a deliberate choice tied to Phase 0's
 * gap analysis: MojoLauncher's issue tracker showed a recurring pattern of
 * scoped-storage failures on Android 11+ from working in shared storage.
 * App-private external storage needs no storage permissions at all and
 * sidesteps that entire class of bug from the start, rather than needing a
 * fix for it later.
 */
class InstanceDirectoryManager(context: Context) {

    private val root: File = File(context.getExternalFilesDir(null), "minecraft")

    val versionsDir: File get() = File(root, "versions").apply { mkdirs() }
    val librariesDir: File get() = File(root, "libraries").apply { mkdirs() }
    val assetsDir: File get() = File(root, "assets").apply { mkdirs() }
    private val instancesRoot: File get() = File(root, "instances").apply { mkdirs() }

    fun instanceDir(profileId: String): File =
        File(instancesRoot, profileId).apply { mkdirs() }

    fun modsDir(profileId: String): File =
        File(instanceDir(profileId), "mods").apply { mkdirs() }

    fun configDir(profileId: String): File =
        File(instanceDir(profileId), "config").apply { mkdirs() }

    fun resourcePacksDir(profileId: String): File =
        File(instanceDir(profileId), "resourcepacks").apply { mkdirs() }

    fun shaderPacksDir(profileId: String): File =
        File(instanceDir(profileId), "shaderpacks").apply { mkdirs() }

    fun savesDir(profileId: String): File =
        File(instanceDir(profileId), "saves").apply { mkdirs() }

    fun deleteInstance(profileId: String) {
        instanceDir(profileId).deleteRecursively()
    }
}
