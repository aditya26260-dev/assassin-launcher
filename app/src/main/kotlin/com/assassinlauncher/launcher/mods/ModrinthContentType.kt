package com.assassinlauncher.launcher.mods

/** Resource packs and shaders reuse the mod manager's installed/installer
 * pattern per architecture 6.7, scoped by these two values: which Modrinth
 * project type to search, and which folder in the instance they live in. */
enum class ModrinthContentType(val projectType: String, val folderName: String) {
    RESOURCE_PACK("resourcepack", "resourcepacks"),
    SHADER("shader", "shaderpacks")
}
