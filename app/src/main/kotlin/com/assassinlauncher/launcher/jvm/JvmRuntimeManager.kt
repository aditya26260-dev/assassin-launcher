package com.assassinlauncher.launcher.jvm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.File
import java.io.IOException

enum class JavaRuntimeVersion(val majorVersion: Int) {
    JAVA_8(8),
    JAVA_17(17),
    JAVA_21(21),
    JAVA_25(25)
}

/** Java 21 ships bundled in the app; other majors still download from
 * AngelAuraMC's releases on first use. */
class JvmRuntimeManager(private val context: Context) {

    private val client = OkHttpClient()
    private val runtimesRoot: File
        get() = File(context.filesDir, "runtimes").apply { mkdirs() }

    private fun downloadUrl(version: JavaRuntimeVersion): String {
        val v = version.majorVersion
        return "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/" +
            "download/download_jre$v/jre$v-android-arm64.tar.xz"
    }

    private fun runtimeDir(version: JavaRuntimeVersion): File =
        File(runtimesRoot, "jre${version.majorVersion}")

    /** The provisioned runtime's root directory (containing bin/, lib/,
     * etc.) - the JVM-embedding launch path needs this directly, not
     * just the java binary path javaBinary() already exposed. */
    fun runtimeRoot(version: JavaRuntimeVersion): File = runtimeDir(version)

    fun javaBinary(version: JavaRuntimeVersion): File =
        File(runtimeDir(version), "bin/java")

    fun isAvailableLocally(version: JavaRuntimeVersion): Boolean =
        javaBinary(version).exists()

    private fun bundledAssetPath(version: JavaRuntimeVersion): String? = when (version) {
        JavaRuntimeVersion.JAVA_21 -> "runtimes/jre21-android-arm64.tar.xz"
        else -> null
    }

    /** Extracts from the bundled asset if there is one for this version,
     * otherwise downloads. Returns the java binary path. */
    suspend fun ensureAvailable(version: JavaRuntimeVersion): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val binary = javaBinary(version)
                if (binary.exists()) {
                    binary.setExecutable(true, false)
                    return@runCatching binary
                }

                val targetDir = runtimeDir(version)
                targetDir.mkdirs()

                val assetPath = bundledAssetPath(version)
                if (assetPath != null) {
                    context.assets.open(assetPath).use { assetStream ->
                        XZCompressorInputStream(assetStream).use { xzStream ->
                            TarArchiveInputStream(xzStream).use { tarStream ->
                                extractTar(tarStream, targetDir)
                            }
                        }
                    }
                } else {
                    val request = Request.Builder().url(downloadUrl(version)).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException(
                                "Failed to download Java ${version.majorVersion}: HTTP ${response.code}"
                            )
                        }
                        val body = response.body ?: throw IOException("Empty response body")
                        XZCompressorInputStream(body.byteStream()).use { xzStream ->
                            TarArchiveInputStream(xzStream).use { tarStream ->
                                extractTar(tarStream, targetDir)
                            }
                        }
                    }
                }

                if (!binary.exists()) {
                    throw IOException("Extraction completed but java binary wasn't found")
                }
                binary
            }
        }

    private fun extractTar(tarStream: TarArchiveInputStream, targetDir: File) {
        var entry: TarArchiveEntry? = tarStream.nextEntry
        while (entry != null) {
            val outFile = File(targetDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output -> tarStream.copyTo(output) }
                // Tar entries carry their original Unix file mode, but
                // extracting the bytes doesn't apply it automatically -
                // without this, bin/java and every other executable in
                // the runtime would extract without the execute bit set,
                // and the whole runtime would be unusable even though
                // every byte extracted correctly.
                val mode = entry.mode
                outFile.setExecutable((mode and 0b001000000) != 0, false)
                outFile.setReadable((mode and 0b100000000) != 0, false)
                outFile.setWritable((mode and 0b010000000) != 0, false)
            }
            entry = tarStream.nextEntry
        }
    }
}
