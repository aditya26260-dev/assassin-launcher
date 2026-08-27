package com.assassinlauncher.launcher.nativebridge

/**
 * Entry point into the native rendering bridge (architecture doc 5.1).
 * Currently exposes only a version check confirming the native library
 * loads and links correctly. Driver loading, the EGL/surface bridge, and
 * the actual GL4ES/MobileGlues/Turnip/Zink JNI glue get added here as
 * that work happens.
 */
object NativeBridge {
    init {
        // libjli.so (extracted at runtime from the AngelAuraMC JDK, not
        // bundled in the APK) depends on libc++_shared.so, which that JDK
        // archive doesn't ship - confirmed by listing the actual archive
        // contents, not assumed. Loading it here, from the app's own
        // bundled copy (see the ANDROID_STL=c++_shared build.gradle.kts
        // change), makes it available to the dynamic linker process-wide
        // before anything later dlopens libjli.so from its own separate
        // files/runtimes/ path - the linker resolves an already-loaded
        // soname regardless of which directory asked for it next.
        System.loadLibrary("c++_shared")
        System.loadLibrary("assassinlauncher_native")
    }

    external fun nativeBridgeVersion(): String

    /**
     * Runs the deep Vulkan capability check from vulkan_probe.cpp - the
     * version/extension detail Android's Java APIs can't reach directly.
     * Safe to call on devices with no Vulkan driver at all; returns a
     * result with vulkanAvailable = false rather than throwing.
     */
    external fun probeVulkanCapabilities(): VulkanCapabilityResult

    /**
     * Attempts to actually load a custom Vulkan driver via libadrenotools.
     * hookLibDir must be context.applicationInfo.nativeLibraryDir exactly -
     * libadrenotools' own docs are explicit that anything else silently
     * fails. customDriverDir must be true internal storage (context.filesDir-
     * based), never external/shared storage. Returns whether the driver
     * actually loaded - a real check, not an assumption based on GPU family
     * alone.
     */
    external fun tryLoadCustomVulkanDriver(
        hookLibDir: String,
        customDriverDir: String,
        customDriverName: String,
        tmpLibDir: String
    ): Boolean

    /**
     * Attempts to load a custom EGL/GL implementation (MobileGlues, and
     * later Krypton Wrapper/GL4ES) via a standard dlopen. Confirmed against
     * Amethyst Launcher's real source that this doesn't need libadrenotools-
     * style linker namespace tricks - EGL loading is controlled by the
     * app's own native code, unlike the Vulkan case.
     */
    external fun tryLoadCustomEglLibrary(customEglPath: String): Boolean

    /**
     * Loads a single JDK shared library by absolute path, in the specific
     * dependency order the JVM embedding sequence needs (libjli.so
     * first, then libjvm.so, then the rest - see GameLaunchOrchestrator
     * for the exact order, verified against a real downloaded JDK build
     * rather than assumed). Uses RTLD_GLOBAL so each subsequently-loaded
     * library can resolve symbols from the ones opened before it.
     */
    external fun dlopenJvmLibrary(absolutePath: String): Boolean

    /**
     * Calls JLI_Launch - the same internal entry point the real java
     * binary itself uses - from an already dlopen'd libjli.so, embedding
     * a full JVM inside this process. Confirmed directly against
     * Amethyst's jre_launcher.c rather than assumed; not a documented
     * public JDK API, but the real mechanism this entire class of
     * launcher depends on.
     *
     * Under normal circumstances this does not return: closing Minecraft
     * closes this app's process too, the same way running `java` from a
     * shell exits the shell's process when your program finishes. See
     * the native-side comment for why, and docs/PROGRESS.md for what
     * that means for "return to the launcher after the game closes."
     */
    external fun launchEmbeddedJvm(
        jliLibraryPath: String,
        args: Array<String>,
        fullVersion: String,
        dotVersion: String
    ): Int
}
