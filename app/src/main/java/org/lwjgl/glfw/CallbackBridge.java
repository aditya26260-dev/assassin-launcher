package org.lwjgl.glfw;

import android.content.res.Resources;
import androidx.annotation.CriticalNative;
import androidx.annotation.Keep;
import java.nio.ByteBuffer;

/**
 * libpojavexec.so's JNI_OnLoad looks up this class, finds the five plain
 * static methods below by name, then calls RegisterNatives to bind the
 * native methods below those. Different java.lang.Class than org.lwjgl.
 * glfw.CallbackBridge in the game's own classpath - two separate JVMs,
 * two separate loads of the same class name. Verified against the .so's
 * own strings, ART's own RegisterNatives failure dump, and Amethyst-
 * Android's real app_pojavlauncher/.../CallbackBridge.java, which binds
 * the same native library the same way.
 */
public class CallbackBridge {
    @Keep
    public static String accessAndroidClipboard(int action, String data) {
        return null;
    }

    @Keep
    public static void onGrabStateChanged(boolean grabbed) {
    }

    @Keep
    public static void onDirectInputEnable() {
    }

    @Keep
    public static boolean notifyLauncher(int type, int[] data) {
        return false;
    }

    @Keep
    public static float getAndroidDPI() {
        return Resources.getSystem().getDisplayMetrics().density;
    }

    @Keep @CriticalNative
    public static native void nativeSetUseInputStackQueue(boolean useInputStackQueue);

    @Keep @CriticalNative
    private static native boolean nativeSendChar(char codepoint);

    @Keep @CriticalNative
    private static native boolean nativeSendCharMods(char codepoint, int mods);

    @Keep @CriticalNative
    private static native void nativeSendKey(int key, int scancode, int action, int mods);

    @Keep @CriticalNative
    private static native void nativeSendCursorPos(float x, float y);

    @Keep @CriticalNative
    private static native void nativeSendMouseButton(int button, int action, int mods);

    @Keep @CriticalNative
    private static native void nativeSendScroll(double xoffset, double yoffset);

    @Keep @CriticalNative
    private static native void nativeSendScreenSize(int width, int height);

    public static native void nativeSetWindowAttrib(int attrib, int value);

    private static native ByteBuffer nativeCreateGamepadButtonBuffer();

    private static native ByteBuffer nativeCreateGamepadAxisBuffer();
}
