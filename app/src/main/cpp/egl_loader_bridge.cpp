#include <jni.h>
#include <dlfcn.h>
#include <string>

// EGL library substitution for MobileGlues (and, if added later, other
// GLES-translation renderers that work the same way - GL4ES-family,
// Krypton Wrapper). Confirmed against Amethyst Launcher's actual source
// (egl_loader.c / loader_dlopen.c): this needs nothing like libadrenotools'
// linker namespace bypass. EGL/GL loading is controlled by the app's own
// native code calling eglGetDisplay etc., so simply choosing which library
// to dlopen those symbols from is enough - the more involved mechanism
// Turnip needs is specific to how Android's system Vulkan loader is
// integrated, not a general requirement for every custom renderer.
//
// Same internal-storage requirement as the Turnip driver applies here too:
// dlopen requires an executable filesystem, and Android's external/shared
// storage is typically mounted noexec regardless of which library is being
// loaded - this isn't specific to Vulkan driver loading, it applies here
// just as much.

extern "C" JNIEXPORT jboolean JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_tryLoadCustomEglLibrary(
        JNIEnv *env, jobject /* this */, jstring customEglPath) {
    const char *pathChars = env->GetStringUTFChars(customEglPath, nullptr);

    void *handle = dlopen(pathChars, RTLD_LOCAL | RTLD_LAZY);

    env->ReleaseStringUTFChars(customEglPath, pathChars);

    // Deliberately not calling dlclose on success, same reasoning as the
    // Turnip driver check: this library needs to stay resident for actual
    // rendering use once that pipeline exists, and it's not documented
    // whether tearing it down after a validation-only check is safe.
    return handle != nullptr ? JNI_TRUE : JNI_FALSE;
}
