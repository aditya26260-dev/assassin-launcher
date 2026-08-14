package com.assassinlauncher.launcher.mods.remote

data class ModrinthSearchResult(
    val hits: List<ModrinthHit>,
    val offset: Int,
    val limit: Int,
    val totalHits: Int
)

data class ModrinthHit(
    val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val downloads: Int,
    val iconUrl: String?,
    val latestVersion: String?,
    val license: String?,
    val clientSide: String,
    val serverSide: String
)

data class ModrinthVersion(
    val id: String,
    val versionNumber: String,
    val name: String,
    val changelog: String?,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val files: List<ModrinthVersionFile>
)

data class ModrinthVersionFile(
    val url: String,
    val fileName: String,
    val primary: Boolean,
    val sizeBytes: Long
)

/** Search results only carry a short description - the detail modal (6.6)
 * needs the full one, which lives on the project endpoint instead. */
data class ModrinthProject(
    val id: String,
    val title: String,
    val description: String,
    val body: String,
    val iconUrl: String?
)
