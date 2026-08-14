#include <jni.h>
#include <string>

// This file is the seed of the native rendering bridge described in
// architecture doc 5.1 and 5.3: driver loading, the EGL/surface bridge,
// and JNI glue against GL4ES/MobileGlues/Turnip/Zink. None of that is
// built yet - this establishes a working, linked native library with a
// single real entry point, so the Kotlin/native boundary is confirmed
// working before the actual rendering logic is added on top of it.

extern "C" JNIEXPORT jstring JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_nativeBridgeVersion(
        JNIEnv *env, jobject /* this */) {
    std::string version = "assassinlauncher_native/0.1.0";
    return env->NewStringUTF(version.c_str());
}
