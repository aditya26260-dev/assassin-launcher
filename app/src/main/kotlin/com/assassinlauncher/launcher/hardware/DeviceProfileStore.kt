package com.assassinlauncher.launcher.hardware

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.deviceProfileStore by preferencesDataStore(name = "device_profile")

/**
 * Persists the first-launch detection result so architecture 6.1's flow
 * only actually runs once, not on every app start. Re-detection (if the
 * user's device driver situation changes after an OS update) is a later
 * concern, not handled yet - noted rather than silently assumed away.
 */
object DeviceProfileStore {

    private object Keys {
        val FIRST_LAUNCH_DONE = booleanPreferencesKey("first_launch_done")
        val GPU_VENDOR = stringPreferencesKey("gpu_vendor")
        val GPU_RENDERER = stringPreferencesKey("gpu_renderer")
        val GPU_FAMILY = stringPreferencesKey("gpu_family")
        val ADRENO_MODEL = intPreferencesKey("adreno_model")
        val GLES_MAJOR = intPreferencesKey("gles_major")
        val GLES_MINOR = intPreferencesKey("gles_minor")
        val VULKAN_API_MAJOR = intPreferencesKey("vulkan_api_major")
        val VULKAN_API_MINOR = intPreferencesKey("vulkan_api_minor")
        val VULKAN_DYNAMIC_RENDERING = booleanPreferencesKey("vulkan_dynamic_rendering")
        val VULKAN_PUSH_DESCRIPTORS = booleanPreferencesKey("vulkan_push_descriptors")
        val TURNIP_BUILD_AVAILABLE = booleanPreferencesKey("turnip_build_available")
        val MOBILEGLUES_LOADABLE = booleanPreferencesKey("mobileglues_loadable")
        val KRYPTON_WRAPPER_LOADABLE = booleanPreferencesKey("krypton_wrapper_loadable")
    }

    suspend fun isFirstLaunchDone(context: Context): Boolean =
        context.deviceProfileStore.data.first()[Keys.FIRST_LAUNCH_DONE] ?: false

    suspend fun save(context: Context, profile: DeviceProfile) {
        context.deviceProfileStore.edit { prefs ->
            prefs[Keys.FIRST_LAUNCH_DONE] = true
            prefs[Keys.GPU_VENDOR] = profile.gpuVendor
            prefs[Keys.GPU_RENDERER] = profile.gpuRenderer
            prefs[Keys.GPU_FAMILY] = profile.gpuFamily.name
            profile.adrenoModel?.let { prefs[Keys.ADRENO_MODEL] = it }
            prefs[Keys.GLES_MAJOR] = profile.glesVersionMajor
            prefs[Keys.GLES_MINOR] = profile.glesVersionMinor
            prefs[Keys.VULKAN_API_MAJOR] = profile.deepVulkanApiVersionMajor
            prefs[Keys.VULKAN_API_MINOR] = profile.deepVulkanApiVersionMinor
            prefs[Keys.VULKAN_DYNAMIC_RENDERING] = profile.vulkanDynamicRendering
            prefs[Keys.VULKAN_PUSH_DESCRIPTORS] = profile.vulkanPushDescriptors
            prefs[Keys.TURNIP_BUILD_AVAILABLE] = profile.turnipBuildAvailable
            prefs[Keys.MOBILEGLUES_LOADABLE] = profile.mobileGluesLoadable
            prefs[Keys.KRYPTON_WRAPPER_LOADABLE] = profile.kryptonWrapperLoadable
        }
    }

    suspend fun load(context: Context): DeviceProfile? {
        val prefs = context.deviceProfileStore.data.first()
        val vendor = prefs[Keys.GPU_VENDOR] ?: return null
        val familyName = prefs[Keys.GPU_FAMILY] ?: return null
        val family = runCatching { GpuFamily.valueOf(familyName) }.getOrElse { GpuFamily.OTHER }

        return DeviceProfile(
            androidSdkInt = android.os.Build.VERSION.SDK_INT,
            gpuVendor = vendor,
            gpuRenderer = prefs[Keys.GPU_RENDERER] ?: "unknown",
            gpuFamily = family,
            adrenoModel = prefs[Keys.ADRENO_MODEL],
            glesVersionMajor = prefs[Keys.GLES_MAJOR] ?: 0,
            glesVersionMinor = prefs[Keys.GLES_MINOR] ?: 0,
            declaredVulkanVersionMajor = 0,
            declaredVulkanVersionMinor = 0,
            deepVulkanApiVersionMajor = prefs[Keys.VULKAN_API_MAJOR] ?: 0,
            deepVulkanApiVersionMinor = prefs[Keys.VULKAN_API_MINOR] ?: 0,
            vulkanDynamicRendering = prefs[Keys.VULKAN_DYNAMIC_RENDERING] ?: false,
            vulkanPushDescriptors = prefs[Keys.VULKAN_PUSH_DESCRIPTORS] ?: false,
            turnipBuildAvailable = prefs[Keys.TURNIP_BUILD_AVAILABLE] ?: false,
            mobileGluesLoadable = prefs[Keys.MOBILEGLUES_LOADABLE] ?: false,
            kryptonWrapperLoadable = prefs[Keys.KRYPTON_WRAPPER_LOADABLE] ?: false
        )
    }
}
