package com.assassinlauncher.launcher.jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Downloads what a version actually needs (client jar + filtered library
 * list) into the shared directory structure architecture 5.7 already
 * defines, and builds the real classpath string from what lands there.
 * Skips re-downloading anything already present - same "only fetch what's
 * missing" approach as JvmRuntimeManager.
 */
class LibraryDownloader(
    private val librariesDir: File,
    private val versionsDir: File
) {
    private val client = OkHttpClient()

    suspend fun ensureClientJar(version: MinecraftVersionDetails): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val versionDir = File(versionsDir, version.id).apply { mkdirs() }
                val jarFile = File(versionDir, "${version.id}.jar")
                if (!jarFile.exists()) {
                    downloadTo(version.clientJar.downloadUrl, jarFile)
                }
                jarFile
            }
        }

    suspend fun ensureLibraries(libraries: List<MinecraftLibrary>): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                libraries.map { library ->
                    val target = File(librariesDir, library.path)
                    if (!target.exists()) {
                        target.parentFile?.mkdirs()
                        downloadTo(library.downloadUrl, target)
                    }
                    target
                }
            }
        }

    /** Real Java classpath, colon-separated (Linux/Android convention -
     * this project never targets Windows, so there's no need to handle
     * its semicolon separator). Client jar goes last, matching the
     * convention Mojang's own version JSON implies by listing it
     * separately from the library list. */
    fun buildClasspath(libraryFiles: List<File>, clientJar: File): String =
        (libraryFiles.map { it.absolutePath } + clientJar.absolutePath).joinToString(":")

    private fun downloadTo(url: String, destination: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download $url: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body for $url")
            destination.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
    }
}
