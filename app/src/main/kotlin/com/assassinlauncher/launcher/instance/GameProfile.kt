package com.assassinlauncher.launcher.instance

import com.assassinlauncher.launcher.hardware.ManualRendererOverride

enum class ModLoader {
    VANILLA,
    FABRIC,
    FORGE,
    NEOFORGE,
    QUILT,
    OPTIFINE
}

/**
 * One entry under the shared .minecraft root (architecture 5.7). Anything
 * left null here means "use the automatic choice" - JVM per 5.2, render
 * path per 5.3 - rather than forcing every profile to carry an explicit
 * value for settings most users will never touch.
 */
data class GameProfile(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: ModLoader,
    val loaderVersion: String? = null,
    val iconPath: String? = null,
    val javaRuntimeOverride: String? = null,
    val ramAllocationMb: Int? = null,
    val jvmArgsOverride: String? = null,
    val vulkanEnabled: Boolean = true,
    /** Ignored while the native Vulkan backend is actually active, per 6.8's
     * own note - only applies to the OpenGL-translation path. */
    val manualRendererOverride: ManualRendererOverride? = null,
    /** "System Vulkan driver vs. bundled Turnip fallback" toggle from 6.8.
     * Forcing system driver still has to fall back rather than fail to
     * launch if that driver doesn't actually work - RenderPathSelector
     * enforces that, this field only records the user's preference. */
    val forceSystemVulkanDriver: Boolean = false
) {
    /** Whether this profile's own Minecraft version carries Mojang's
     * native Vulkan option at all (Phase 0 research: 26.2+ only). */
    fun minecraftSupportsNativeVulkan(): Boolean =
        MinecraftVersions.isAtLeast26_2(minecraftVersion)

    /** The confirmed Zink+Mali exception from Phase 0 research. */
    fun isAtMost1_16_5(): Boolean =
        MinecraftVersions.isAtMost1_16_5(minecraftVersion)

    /** MobileGlues/LTW's shared version floor (Phase 0 research, round two). */
    fun isAtLeast1_17(): Boolean =
        MinecraftVersions.isAtLeast1_17(minecraftVersion)
}
