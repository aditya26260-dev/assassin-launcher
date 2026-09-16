package org.lwjgl.glfw;

import android.content.res.Resources;

/**
 * libpojavexec.so's JNI_OnLoad looks up this class and these five static
 * methods from whichever JVM loads it first. Different java.lang.Class
 * than org.lwjgl.glfw.CallbackBridge in the game's own classpath - two
 * separate JVMs, two separate loads of the same class name. Verified
 * against the .so's own strings and against Amethyst-Android's real
 * app_pojavlauncher/.../CallbackBridge.java, which has the same five.
 */
public class CallbackBridge {
    public static String accessAndroidClipboard(int action, String data) {
        return null;
    }

    public static void onGrabStateChanged(boolean grabbed) {
    }

    public static void onDirectInputEnable() {
    }

    public static boolean notifyLauncher(int type, int[] data) {
        return false;
    }

    public static float getAndroidDPI() {
        return Resources.getSystem().getDisplayMetrics().density;
    }
}
