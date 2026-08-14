package com.assassinlauncher.launcher.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import com.assassinlauncher.launcher.nativebridge.NativeBridge

/**
 * Builds the DeviceProfile that architecture 5.3's rendering decision tree
 * runs on. Two layers: PackageManager's coarse Vulkan version flag (cheap,
 * always safe to call) and the native probe (vulkan_probe.cpp) for the
 * detail Android's Java APIs can't reach - dynamic rendering and push
 * descriptor support specifically.
 */
object DeviceProfiler {

    fun profile(context: Context): DeviceProfile {
        val glInfo = queryGlInfo()
        val (vendor, renderer, glesMajor, glesMinor) = glInfo
            ?: GlInfo("unknown", "unknown", 0, 0)
        val declaredVulkan = queryDeclaredVulkanVersion(context)
        val deepProbe = NativeBridge.probeVulkanCapabilities()
        val (gpuFamily, adrenoModel) = classifyGpu(renderer)

        val turnipDriverManager = TurnipDriverManager(context)
        val matchingVariant = adrenoModel?.let { model ->
            turnipDriverManager.listBundledVariants()
                .firstOrNull { model in it.supportedModels }
        }
        // A real load attempt, not an assumption based on GPU family alone -
        // only bothers attempting it if a variant claiming to cover this
        // exact chip is actually bundled.
        val turnipBuildAvailable = matchingVariant != null &&
            turnipDriverManager.tryLoad(matchingVariant)

        val mobileGluesLoadable = MobileGluesManager(context).tryLoad()
        val kryptonWrapperLoadable = KryptonWrapperManager(context).tryLoad()

        return DeviceProfile(
            androidSdkInt = Build.VERSION.SDK_INT,
            gpuVendor = vendor,
            gpuRenderer = renderer,
            gpuFamily = gpuFamily,
            adrenoModel = adrenoModel,
            glesVersionMajor = glesMajor,
            glesVersionMinor = glesMinor,
            declaredVulkanVersionMajor = declaredVulkan.first,
            declaredVulkanVersionMinor = declaredVulkan.second,
            deepVulkanApiVersionMajor = deepProbe.apiVersionMajor,
            deepVulkanApiVersionMinor = deepProbe.apiVersionMinor,
            vulkanDynamicRendering = deepProbe.dynamicRendering,
            vulkanPushDescriptors = deepProbe.pushDescriptors,
            turnipBuildAvailable = turnipBuildAvailable,
            mobileGluesLoadable = mobileGluesLoadable,
            kryptonWrapperLoadable = kryptonWrapperLoadable
        )
    }

    /** PackageManager's own coarse Vulkan support signal - cheap, and safe on
     * devices with no Vulkan at all, unlike creating an actual Vulkan instance. */
    private fun queryDeclaredVulkanVersion(context: Context): Pair<Int, Int> {
        val packedVersion = context.packageManager.let { pm ->
            val features = pm.systemAvailableFeatures
            features.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                ?.version ?: 0
        }
        if (packedVersion == 0) return 0 to 0
        val major = (packedVersion shr 22) and 0x3FF
        val minor = (packedVersion shr 12) and 0x3FF
        return major to minor
    }

    private val adrenoModelRegex = Regex("""Adreno.*?(\d{3})""")
    private val maliRegex = Regex("""Mali""", RegexOption.IGNORE_CASE)

    /** Returns the family bucket (used for broad decisions throughout
     * RenderPathSelector) alongside the exact model number (used to match
     * a specific bundled Turnip variant to this exact chip). */
    private fun classifyGpu(renderer: String): Pair<GpuFamily, Int?> {
        adrenoModelRegex.find(renderer)?.let { match ->
            val model = match.groupValues[1].toIntOrNull()
                ?: return GpuFamily.ADRENO_OTHER to null
            val family = when (model / 100) {
                6 -> GpuFamily.ADRENO_6XX
                7 -> GpuFamily.ADRENO_7XX
                8 -> GpuFamily.ADRENO_8XX
                else -> GpuFamily.ADRENO_OTHER
            }
            return family to model
        }
        if (maliRegex.containsMatchIn(renderer)) return GpuFamily.MALI to null
        return GpuFamily.OTHER to null
    }

    private data class GlInfo(
        val vendor: String,
        val renderer: String,
        val glesVersionMajor: Int,
        val glesVersionMinor: Int
    )

    private val glesVersionRegex = Regex("""OpenGL ES\s+(\d+)\.(\d+)""")

    private fun parseGlesVersion(versionString: String): Pair<Int, Int> {
        val match = glesVersionRegex.find(versionString) ?: return 0 to 0
        val major = match.groupValues[1].toIntOrNull() ?: 0
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        return major to minor
    }

    /**
     * GL_VENDOR/GL_RENDERER/GL_VERSION only exist once a real GL context is
     * current, so this stands up a throwaway 1x1 pbuffer context just to
     * read them, then tears it down immediately. No visible surface, no
     * lasting state.
     */
    private fun queryGlInfo(): GlInfo? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null

        val versionOut = IntArray(2)
        if (!EGL14.eglInitialize(display, versionOut, 0, versionOut, 1)) return null

        try {
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val configOk = EGL14.eglChooseConfig(
                display, configAttribs, 0, configs, 0, 1, numConfigs, 0
            )
            val config = configs[0]
            if (!configOk || config == null) return null

            // Requesting a GLES2 context is deliberate even though we want to
            // know if GLES 3.x is supported: GLES3 contexts on Android are
            // backward compatible and report their real GL_VERSION either
            // way, and requesting GLES2 here avoids the query itself failing
            // on a device that turns out to only support GLES2.
            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )
            if (eglContext == EGL14.EGL_NO_CONTEXT) return null

            try {
                val pbufferAttribs = intArrayOf(
                    EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE
                )
                val surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
                if (surface == EGL14.EGL_NO_SURFACE) return null

                try {
                    EGL14.eglMakeCurrent(display, surface, surface, eglContext)
                    val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: return null
                    val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: return null
                    val versionString = GLES20.glGetString(GLES20.GL_VERSION) ?: return null
                    val (glesMajor, glesMinor) = parseGlesVersion(versionString)
                    return GlInfo(vendor, renderer, glesMajor, glesMinor)
                } finally {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    EGL14.eglDestroySurface(display, surface)
                }
            } finally {
                EGL14.eglDestroyContext(display, eglContext)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }
}
