package com.assassinlauncher.launcher.nativebridge

/**
 * Result of the native Vulkan capability probe (vulkan_probe.cpp).
 * Field order and types have to match the JNI constructor signature the
 * native side calls exactly: (ZIIZZLjava/lang/String;)V
 */
data class VulkanCapabilityResult(
    val vulkanAvailable: Boolean,
    val apiVersionMajor: Int,
    val apiVersionMinor: Int,
    val dynamicRendering: Boolean,
    val pushDescriptors: Boolean,
    val deviceName: String
) {
    /** Matches the floor Minecraft 26.2+ actually requires (Phase 0 research). */
    fun meetsMinecraftVulkanFloor(): Boolean =
        vulkanAvailable &&
        (apiVersionMajor > 1 || (apiVersionMajor == 1 && apiVersionMinor >= 2)) &&
        dynamicRendering &&
        pushDescriptors
}
