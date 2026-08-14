package com.assassinlauncher.launcher.hardware

/** Adreno driver generations need different Turnip builds (architecture 5.3) - Mali and
 * anything else don't split the same way, so this only buckets Adreno further. */
enum class GpuFamily {
    ADRENO_6XX,
    ADRENO_7XX,
    ADRENO_8XX,
    ADRENO_OTHER,
    MALI,
    OTHER
}

fun GpuFamily.isAdreno(): Boolean =
    this == GpuFamily.ADRENO_6XX || this == GpuFamily.ADRENO_7XX ||
        this == GpuFamily.ADRENO_8XX || this == GpuFamily.ADRENO_OTHER

data class DeviceProfile(
    val androidSdkInt: Int,
    val gpuVendor: String,
    val gpuRenderer: String,
    val gpuFamily: GpuFamily,
    /** Exact chip number (710, 730, 840, etc.) when parseable - needed to
     * match a specific bundled Turnip variant, since the family bucket
     * alone isn't precise enough for that. */
    val adrenoModel: Int?,
    val glesVersionMajor: Int,
    val glesVersionMinor: Int,
    val declaredVulkanVersionMajor: Int,
    val declaredVulkanVersionMinor: Int,
    val deepVulkanApiVersionMajor: Int,
    val deepVulkanApiVersionMinor: Int,
    val vulkanDynamicRendering: Boolean,
    val vulkanPushDescriptors: Boolean,
    /** Whether a bundled Turnip variant matching this exact chip actually
     * loaded successfully - a real check via TurnipDriverManager, not an
     * assumption based on GPU family alone. */
    val turnipBuildAvailable: Boolean,
    /** Whether the bundled MobileGlues library actually loaded successfully
     * via MobileGluesManager - a real dlopen check, not just a GLES-version
     * floor check. */
    val mobileGluesLoadable: Boolean,
    /** Same real check as mobileGluesLoadable, for Krypton Wrapper. */
    val kryptonWrapperLoadable: Boolean
) {
    /** The actual question architecture 5.3's decision tree asks first. */
    fun meetsMinecraftVulkanFloor(): Boolean =
        (deepVulkanApiVersionMajor > 1 ||
            (deepVulkanApiVersionMajor == 1 && deepVulkanApiVersionMinor >= 2)) &&
        vulkanDynamicRendering &&
        vulkanPushDescriptors

    /** libadrenotools (the Turnip driver-swap mechanism, architecture 5.3)
     * requires Android 9+ - confirmed at its own repo. Below this, Turnip/
     * Zink are never attempted regardless of anything else about the device. */
    fun supportsCustomDriverLoading(): Boolean = androidSdkInt >= 28

    /** MobileGlues' own stated GLES minimum, combined with the real load
     * check from MobileGluesManager - both have to hold, not just the
     * version floor on paper. */
    fun meetsMobileGluesFloor(): Boolean =
        mobileGluesLoadable &&
            (glesVersionMajor > 3 || (glesVersionMajor == 3 && glesVersionMinor >= 0))
}
