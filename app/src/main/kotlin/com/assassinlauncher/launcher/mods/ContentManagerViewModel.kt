package com.assassinlauncher.launcher.mods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.mods.remote.ModrinthApiClient
import com.assassinlauncher.launcher.mods.remote.ModrinthHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Same real search/install pattern as ModManagerViewModel, generalized by
 * content type instead of duplicated, for resource packs and shaders (6.7).
 * Simpler than the mod case in one real way: no loader facet applies to
 * either (resource packs and shaders aren't loader-specific), and no
 * enable/disable-via-rename semantics either - Minecraft picks which
 * installed ones are active from its own options, not from file renames.
 */
class ContentManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val modrinthClient = ModrinthApiClient()
    private val httpClient = OkHttpClient()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _installStatus = MutableStateFlow<String?>(null)
    val installStatus: StateFlow<String?> = _installStatus.asStateFlow()

    fun search(query: String, profile: GameProfile, contentType: ModrinthContentType) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            modrinthClient.search(
                query = query,
                projectType = contentType.projectType,
                minecraftVersion = profile.minecraftVersion
            ).fold(
                onSuccess = { result -> _searchState.value = SearchState.Loaded(result.hits) },
                onFailure = { error ->
                    _searchState.value = SearchState.Failed(error.message ?: "Search failed")
                }
            )
        }
    }

    fun install(hit: ModrinthHit, profile: GameProfile, contentType: ModrinthContentType) {
        viewModelScope.launch {
            _installStatus.value = "Finding a matching version of ${hit.title}..."
            val version = modrinthClient.getVersions(hit.projectId, profile.minecraftVersion)
                .getOrNull()?.firstOrNull()
            if (version == null) {
                _installStatus.value =
                    "No version of ${hit.title} matches ${profile.minecraftVersion}"
                return@launch
            }
            val file = version.files.firstOrNull { it.primary } ?: version.files.firstOrNull()
            if (file == null) {
                _installStatus.value = "${hit.title} has no downloadable file for this version"
                return@launch
            }

            _installStatus.value = "Downloading ${file.fileName}..."
            val directoryManager = InstanceDirectoryManager(getApplication())
            val targetDir = when (contentType) {
                ModrinthContentType.RESOURCE_PACK -> directoryManager.resourcePacksDir(profile.id)
                ModrinthContentType.SHADER -> directoryManager.shaderPacksDir(profile.id)
            }
            val success = downloadFile(file.url, File(targetDir, file.fileName))
            _installStatus.value = if (success) {
                "Installed ${hit.title}"
            } else {
                "Failed to download ${hit.title}"
            }
        }
    }

    private suspend fun downloadFile(url: String, destination: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body ?: return@withContext false
                    destination.outputStream().use { output -> body.byteStream().copyTo(output) }
                    true
                }
            }.getOrDefault(false)
        }
}
