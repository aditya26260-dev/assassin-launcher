package com.assassinlauncher.launcher.jvm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class MinecraftVersionSummary(val id: String, val type: String, val url: String)

data class MinecraftLibrary(val name: String, val downloadUrl: String, val path: String)

data class ClientJar(val downloadUrl: String, val sha1: String)

/** One entry in an asset index's "objects" map - the key (not modeled
 * here, kept as the map key in AssetIndex) is the asset's logical path
 * (e.g. "minecraft/sounds/random/click.ogg"); hash is what actually
 * determines where it lives in objects/ and what URL serves it. */
data class AssetObject(val hash: String, val size: Long)

/** An asset index's full contents, not just its URL - fetched
 * separately from version details since it's a whole additional
 * document, often the single largest fetch in the entire launch
 * sequence for modern versions (thousands of entries). "isVirtual"
 * marks pre-1.7 indexes, which need their objects also copied out to
 * assetsDir/virtual/legacy/<path> using the real path rather than the
 * hash-named layout modern versions read directly - old Minecraft
 * doesn't know how to look assets up by hash at all. */
data class AssetIndex(val id: String, val isVirtual: Boolean, val objects: Map<String, AssetObject>)

/** One entry from the modern (1.13+) arguments.game or arguments.jvm
 * array. Either unconditional (rules == null, every plain-string entry
 * takes this form) or gated behind rules that VersionRuleEvaluator
 * resolves at substitution time - kept raw here rather than
 * pre-evaluated, same convention MinecraftLibrary's caller already uses
 * for library rules. Mojang allows one entry's value to itself be a list
 * (e.g. a flag plus its value in one entry); both shapes end up here as
 * "the tokens to splice in, in order, when this entry is allowed". */
data class ArgumentTemplate(val values: List<String>, val rules: JSONArray?)

data class MinecraftVersionDetails(
    val id: String,
    val mainClass: String,
    val requiredJavaMajorVersion: Int,
    val assetIndexUrl: String,
    /** Short asset-index name (e.g. "17"), distinct from assetIndex.id in
     * principle though equal in every real manifest seen so far - kept
     * separate because --assetsIndex wants this exact field, confirmed
     * against Amethyst's real Tools.java rather than assumed equal. */
    val assets: String,
    val clientJar: ClientJar,
    val libraries: List<MinecraftLibrary>,
    /** Pre-1.13 versions only: a flat, pre-formatted argument string with
     * ${token} placeholders and no rule gating at all. Null on modern
     * versions - use gameArguments/jvmArguments instead. */
    val legacyMinecraftArguments: String?,
    /** Empty on legacy (pre-1.13) versions - GameArgumentBuilder falls
     * back to legacyMinecraftArguments, and to a small hardcoded set of
     * JVM defaults, when these are empty. */
    val gameArguments: List<ArgumentTemplate>,
    val jvmArguments: List<ArgumentTemplate>
)

/**
 * Real client against Mojang's actual, documented endpoints - the same
 * ones every Minecraft launcher uses to know what to download and how to
 * launch a given version. Genuinely untested against a live call, same
 * caveat as every other network client built this session.
 */
class MinecraftVersionClient {

    private val client = OkHttpClient()

    suspend fun fetchVersionManifest(): Result<List<MinecraftVersionSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
                    .build()
                val json = JSONObject(executeForBody(request))
                val versionsArray = json.getJSONArray("versions")
                (0 until versionsArray.length()).map { i ->
                    val entry = versionsArray.getJSONObject(i)
                    MinecraftVersionSummary(
                        id = entry.getString("id"),
                        type = entry.getString("type"),
                        url = entry.getString("url")
                    )
                }
            }
        }

    suspend fun fetchVersionDetails(versionUrl: String): Result<MinecraftVersionDetails> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(versionUrl).build()
                val json = JSONObject(executeForBody(request))

                val librariesArray = json.getJSONArray("libraries")
                val libraries = (0 until librariesArray.length()).mapNotNull { i ->
                    val entry = librariesArray.getJSONObject(i)
                    // Rules gate whether a library applies to this platform
                    // at all (natives for other OSes, mainly) - evaluated
                    // with the same confirmed semantics used everywhere
                    // else rules show up in this format.
                    if (!VersionRuleEvaluator.isAllowed(entry.optJSONArray("rules"))) {
                        return@mapNotNull null
                    }
                    val artifact = entry.optJSONObject("downloads")
                        ?.optJSONObject("artifact") ?: return@mapNotNull null
                    MinecraftLibrary(
                        name = entry.getString("name"),
                        downloadUrl = artifact.getString("url"),
                        path = artifact.getString("path")
                    )
                }

                val clientDownload = json.getJSONObject("downloads").getJSONObject("client")
                val argumentsObject = json.optJSONObject("arguments")

                MinecraftVersionDetails(
                    id = json.getString("id"),
                    mainClass = json.getString("mainClass"),
                    requiredJavaMajorVersion = json.optJSONObject("javaVersion")
                        ?.optInt("majorVersion", 21) ?: 21,
                    assetIndexUrl = json.getJSONObject("assetIndex").getString("url"),
                    assets = json.getString("assets"),
                    clientJar = ClientJar(
                        downloadUrl = clientDownload.getString("url"),
                        sha1 = clientDownload.getString("sha1")
                    ),
                    libraries = libraries,
                    legacyMinecraftArguments = json.optString("minecraftArguments")
                        .takeIf { it.isNotBlank() },
                    gameArguments = parseArgumentArray(argumentsObject?.optJSONArray("game")),
                    jvmArguments = parseArgumentArray(argumentsObject?.optJSONArray("jvm"))
                )
            }
        }

    /** Mojang's arguments.game/arguments.jvm arrays mix two element
     * shapes: plain strings (always included) and {rules, value} objects
     * (value is either a single string or a list of strings). Both
     * normalize to an ArgumentTemplate; malformed entries are skipped
     * rather than failing the whole version fetch over one bad token. */
    private fun parseArgumentArray(array: JSONArray?): List<ArgumentTemplate> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            when (val raw = array.get(i)) {
                is String -> ArgumentTemplate(values = listOf(raw), rules = null)
                is JSONObject -> {
                    val values = when (val value = raw.opt("value")) {
                        is String -> listOf(value)
                        is JSONArray -> (0 until value.length()).map(value::getString)
                        else -> return@mapNotNull null
                    }
                    ArgumentTemplate(values = values, rules = raw.optJSONArray("rules"))
                }
                else -> null
            }
        }
    }

    /** The asset index's actual content - fetchVersionDetails only ever
     * captured its URL. A separate fetch because it's a genuinely
     * separate document, not part of the per-version manifest itself.
     * `id` is passed in rather than parsed - Mojang's index JSON doesn't
     * self-report one, it's only known from the version manifest's
     * separate "assets" short-name field the caller already has. */
    suspend fun fetchAssetIndex(id: String, url: String): Result<AssetIndex> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                val json = JSONObject(executeForBody(request))
                val objectsJson = json.getJSONObject("objects")
                val objects = objectsJson.keys().asSequence().associateWith { path ->
                    val entry = objectsJson.getJSONObject(path)
                    AssetObject(hash = entry.getString("hash"), size = entry.getLong("size"))
                }
                AssetIndex(id = id, isVirtual = json.optBoolean("virtual", false), objects = objects)
            }
        }

    private fun executeForBody(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException(
                    "Request to ${request.url} failed: HTTP ${response.code}"
                )
            }
            return response.body?.string() ?: throw java.io.IOException("Empty response body")
        }
    }
}
