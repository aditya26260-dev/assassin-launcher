package com.assassinlauncher.launcher.mods.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Modrinth's v2 API is public and needs no key, unlike CurseForge (6.5).
 * Written against Modrinth's documented, stable schema - correct as far as
 * that documentation goes, but genuinely not verified against a live call,
 * since there's no working network in this sandbox to test it with. Worth
 * a real test as soon as this runs somewhere that has internet.
 */
class ModrinthApiClient {

    private val client = OkHttpClient()
    private val baseUrl = "https://api.modrinth.com/v2"

    suspend fun search(
        query: String,
        projectType: String = "mod",
        minecraftVersion: String? = null,
        loader: String? = null,
        offset: Int = 0,
        limit: Int = 20
    ): Result<ModrinthSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val facets = buildFacets(projectType, minecraftVersion, loader)
            val urlBuilder = StringBuilder("$baseUrl/search?query=${encode(query)}")
                .append("&offset=$offset&limit=$limit&index=relevance")
            if (facets != null) {
                urlBuilder.append("&facets=${encode(facets)}")
            }

            val request = Request.Builder().url(urlBuilder.toString()).build()
            val responseBody = executeForBody(request)
            parseSearchResult(JSONObject(responseBody))
        }
    }

    suspend fun getProject(projectIdOrSlug: String): Result<ModrinthProject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/project/${encode(projectIdOrSlug)}")
                    .build()
                val responseBody = executeForBody(request)
                val json = JSONObject(responseBody)
                ModrinthProject(
                    id = json.getString("id"),
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    body = json.optString("body", ""),
                    iconUrl = json.optString("icon_url").takeIf { it.isNotBlank() }
                )
            }
        }

    suspend fun getVersions(
        projectIdOrSlug: String,
        minecraftVersion: String? = null,
        loader: String? = null
    ): Result<List<ModrinthVersion>> = withContext(Dispatchers.IO) {
        runCatching {
            val urlBuilder = StringBuilder("$baseUrl/project/${encode(projectIdOrSlug)}/version")
            val params = mutableListOf<String>()
            minecraftVersion?.let { params.add("game_versions=${encode("[\"$it\"]")}") }
            loader?.let { params.add("loaders=${encode("[\"$it\"]")}") }
            if (params.isNotEmpty()) urlBuilder.append("?").append(params.joinToString("&"))

            val request = Request.Builder().url(urlBuilder.toString()).build()
            val responseBody = executeForBody(request)
            parseVersionList(JSONArray(responseBody))
        }
    }

    private fun executeForBody(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Modrinth request failed: HTTP ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun buildFacets(projectType: String, minecraftVersion: String?, loader: String?): String? {
        val facetGroups = mutableListOf<String>()
        facetGroups.add("[\"project_type:$projectType\"]")
        minecraftVersion?.let { facetGroups.add("[\"versions:$it\"]") }
        loader?.let { facetGroups.add("[\"categories:$it\"]") }
        return if (facetGroups.isEmpty()) null else "[${facetGroups.joinToString(",")}]"
    }

    private fun parseSearchResult(json: JSONObject): ModrinthSearchResult {
        val hitsArray = json.getJSONArray("hits")
        val hits = (0 until hitsArray.length()).map { i ->
            val hit = hitsArray.getJSONObject(i)
            ModrinthHit(
                projectId = hit.getString("project_id"),
                slug = hit.optString("slug", hit.getString("project_id")),
                title = hit.getString("title"),
                description = hit.optString("description", ""),
                author = hit.optString("author", "unknown"),
                downloads = hit.optInt("downloads", 0),
                iconUrl = hit.optString("icon_url").takeIf { it.isNotBlank() },
                latestVersion = hit.optString("latest_version").takeIf { it.isNotBlank() },
                license = hit.optString("license").takeIf { it.isNotBlank() },
                clientSide = hit.optString("client_side", "unknown"),
                serverSide = hit.optString("server_side", "unknown")
            )
        }
        return ModrinthSearchResult(
            hits = hits,
            offset = json.optInt("offset", 0),
            limit = json.optInt("limit", hits.size),
            totalHits = json.optInt("total_hits", hits.size)
        )
    }

    private fun parseVersionList(json: JSONArray): List<ModrinthVersion> =
        (0 until json.length()).map { i ->
            val version = json.getJSONObject(i)
            val filesArray = version.getJSONArray("files")
            val files = (0 until filesArray.length()).map { j ->
                val file = filesArray.getJSONObject(j)
                ModrinthVersionFile(
                    url = file.getString("url"),
                    fileName = file.getString("filename"),
                    primary = file.optBoolean("primary", false),
                    sizeBytes = file.optLong("size", 0L)
                )
            }
            val gameVersionsArray = version.getJSONArray("game_versions")
            val gameVersions = (0 until gameVersionsArray.length())
                .map { gameVersionsArray.getString(it) }
            val loadersArray = version.getJSONArray("loaders")
            val loaders = (0 until loadersArray.length()).map { loadersArray.getString(it) }

            ModrinthVersion(
                id = version.getString("id"),
                versionNumber = version.getString("version_number"),
                name = version.getString("name"),
                changelog = version.optString("changelog").takeIf { it.isNotBlank() },
                gameVersions = gameVersions,
                loaders = loaders,
                files = files
            )
        }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
