package com.assassinlauncher.launcher.mods

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.instance.ModLoader
import com.assassinlauncher.launcher.mods.remote.ModrinthApiClient
import com.assassinlauncher.launcher.mods.remote.ModrinthHit
import com.assassinlauncher.launcher.mods.remote.ModrinthVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    data class Loaded(val hits: List<ModrinthHit>) : SearchState()
    data class Failed(val message: String) : SearchState()
}

class ModManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val modrinthClient = ModrinthApiClient()
    private val httpClient = OkHttpClient()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _installStatus = MutableStateFlow<String?>(null)
    val installStatus: StateFlow<String?> = _installStatus.asStateFlow()

    /** Empty query returns Modrinth's default popularity-sorted listing,
     * matching 6.5's "default view shows the most popular/downloaded
     * mods" requirement without needing separate logic for it. */
    fun search(query: String, profile: GameProfile) {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            val loaderFacet = loaderFacetFor(profile.loader)
            modrinthClient.search(
                query = query,
                minecraftVersion = profile.minecraftVersion,
                loader = loaderFacet
            ).fold(
                onSuccess = { result -> _searchState.value = SearchState.Loaded(result.hits) },
                onFailure = { error ->
                    _searchState.value = SearchState.Failed(
                        error.message ?: "Search failed"
                    )
                }
            )
        }
    }

    /** Vanilla and OptiFine profiles show no installer content, per 6.5 -
     * Modrinth doesn't host mods for either loader, so there's nothing
     * meaningful to search for regardless. */
    private fun loaderFacetFor(loader: ModLoader): String? = when (loader) {
        ModLoader.FABRIC -> "fabric"
        ModLoader.FORGE -> "forge"
        ModLoader.NEOFORGE -> "neoforge"
        ModLoader.QUILT -> "quilt"
        ModLoader.VANILLA, ModLoader.OPTIFINE -> null
    }

    fun install(hit: ModrinthHit, profile: GameProfile) {
        viewModelScope.launch {
            _installStatus.value = "Finding a matching version of ${hit.title}..."
            val loaderFacet = loaderFacetFor(profile.loader)
            val versionsResult = modrinthClient.getVersions(
                hit.projectId,
                minecraftVersion = profile.minecraftVersion,
                loader = loaderFacet
            )
            val version = versionsResult.getOrNull()?.firstOrNull()
            if (version == null) {
                _installStatus.value =
                    "No version of ${hit.title} matches ${profile.minecraftVersion}"
                return@launch
            }
            installVersion(hit, version, profile)
        }
    }

    /** Used by the detail modal (6.6) when the user picked a specific
     * version/loader themselves, rather than accepting the auto-matched
     * latest one - null version falls back to auto-matching, same as a
     * plain install would. */
    fun installVersion(hit: ModrinthHit, version: ModrinthVersion?, profile: GameProfile) {
        viewModelScope.launch {
            val resolvedVersion = version ?: run {
                val loaderFacet = loaderFacetFor(profile.loader)
                modrinthClient.getVersions(
                    hit.projectId,
                    minecraftVersion = profile.minecraftVersion,
                    loader = loaderFacet
                ).getOrNull()?.firstOrNull()
            }
            if (resolvedVersion == null) {
                _installStatus.value =
                    "No version of ${hit.title} matches ${profile.minecraftVersion}"
                return@launch
            }

            val file = resolvedVersion.files.firstOrNull { it.primary }
                ?: resolvedVersion.files.firstOrNull()
            if (file == null) {
                _installStatus.value = "${hit.title} has no downloadable file for this version"
                return@launch
            }

            _installStatus.value = "Downloading ${file.fileName}..."
            val modsDir = InstanceDirectoryManager(getApplication()).modsDir(profile.id)
            val success = downloadFile(file.url, File(modsDir, file.fileName))
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
                    destination.outputStream().use { output ->
                        body.byteStream().copyTo(output)
                    }
                    true
                }
            }.getOrDefault(false)
        }
}
