package com.assassinlauncher.launcher.instance

import com.assassinlauncher.launcher.hardware.ManualRendererOverride
import org.json.JSONArray
import org.json.JSONObject

internal fun GameProfile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("minecraftVersion", minecraftVersion)
    put("loader", loader.name)
    put("loaderVersion", loaderVersion)
    put("iconPath", iconPath)
    put("javaRuntimeOverride", javaRuntimeOverride)
    put("ramAllocationMb", ramAllocationMb)
    put("jvmArgsOverride", jvmArgsOverride)
    put("vulkanEnabled", vulkanEnabled)
    put("manualRendererOverride", manualRendererOverride?.name)
    put("forceSystemVulkanDriver", forceSystemVulkanDriver)
}

internal fun JSONObject.toGameProfile(): GameProfile = GameProfile(
    id = getString("id"),
    name = getString("name"),
    minecraftVersion = getString("minecraftVersion"),
    loader = runCatching { ModLoader.valueOf(getString("loader")) }
        .getOrDefault(ModLoader.VANILLA),
    loaderVersion = optStringOrNull("loaderVersion"),
    iconPath = optStringOrNull("iconPath"),
    javaRuntimeOverride = optStringOrNull("javaRuntimeOverride"),
    ramAllocationMb = if (has("ramAllocationMb") && !isNull("ramAllocationMb")) {
        getInt("ramAllocationMb")
    } else {
        null
    },
    jvmArgsOverride = optStringOrNull("jvmArgsOverride"),
    vulkanEnabled = optBoolean("vulkanEnabled", true),
    manualRendererOverride = optStringOrNull("manualRendererOverride")?.let {
        runCatching { ManualRendererOverride.valueOf(it) }.getOrNull()
    },
    forceSystemVulkanDriver = optBoolean("forceSystemVulkanDriver", false)
)

internal fun List<GameProfile>.toJsonArray(): JSONArray =
    JSONArray().apply { forEach { put(it.toJson()) } }

internal fun JSONArray.toGameProfileList(): List<GameProfile> =
    (0 until length()).map { getJSONObject(it).toGameProfile() }

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
