#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <adrenotools/driver.h>

// Wraps libadrenotools' adrenotools_open_libvulkan, following its own
// header documentation precisely rather than guessing at usage:
// - hookLibDir MUST be context.applicationInfo.nativeLibraryDir (Kotlin
//   side is responsible for passing the real value, not a guessed path)
// - customDriverDir MUST be true internal storage, not external/sdcard -
//   confirmed both by the header's own security note (dlopen refuses
//   world-writable paths) and by the reference implementation studied,
//   which notes external storage is typically mounted noexec anyway
// - the app must be packaged with useLegacyPackaging = true (already
//   fixed in app/build.gradle.kts) or hookLibDir won't resolve correctly
//
// This only reports whether the driver actually loads - it doesn't yet
// feed the resulting handle into a live Vulkan/JVM session, because that
// launch pipeline doesn't exist in this project yet. What this adds right
// now: RenderPathSelector's "is a matching Turnip build actually usable
// here" check can become a real dlopen attempt instead of an assumption
// based on GPU family alone.

extern "C" JNIEXPORT jboolean JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_tryLoadCustomVulkanDriver(
        JNIEnv *env, jobject /* this */,
        jstring hookLibDir, jstring customDriverDir, jstring customDriverName,
        jstring tmpLibDir) {
    const char *hookLibDirChars = env->GetStringUTFChars(hookLibDir, nullptr);
    const char *customDriverDirChars = env->GetStringUTFChars(customDriverDir, nullptr);
    const char *customDriverNameChars = env->GetStringUTFChars(customDriverName, nullptr);
    const char *tmpLibDirChars = env->GetStringUTFChars(tmpLibDir, nullptr);

    void *handle = adrenotools_open_libvulkan(
            RTLD_NOW,
            ADRENOTOOLS_DRIVER_CUSTOM,
            tmpLibDirChars,
            hookLibDirChars,
            customDriverDirChars,
            customDriverNameChars,
            nullptr,  // fileRedirectDir - not using ADRENOTOOLS_DRIVER_FILE_REDIRECT
            nullptr   // userMappingHandle - not using ADRENOTOOLS_DRIVER_GPU_MAPPING_IMPORT
    );

    env->ReleaseStringUTFChars(hookLibDir, hookLibDirChars);
    env->ReleaseStringUTFChars(customDriverDir, customDriverDirChars);
    env->ReleaseStringUTFChars(customDriverName, customDriverNameChars);
    env->ReleaseStringUTFChars(tmpLibDir, tmpLibDirChars);

    if (handle == nullptr) {
        return JNI_FALSE;
    }

    // Deliberately not calling dlclose here. The header doesn't document
    // whether closing this handle safely tears down whatever hooks/patches
    // were installed during the open call, and that's not something to
    // guess at without a real device to check. This function only runs
    // occasionally (first-launch detection, settings changes), so leaking
    // one handle in that case is a small, acceptable tradeoff against an
    // unverified assumption about safe teardown.
    return JNI_TRUE;
}
