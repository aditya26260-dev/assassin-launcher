package org.lwjgl.glfw;

/**
 * Confirmed needed by checking libpojavexec.so's own strings directly:
 * its JNI_OnLoad looks up this exact class and these exact methods from
 * whichever JVM calls it first (see AndroidLwjglProvider's own doc
 * comment on preloadPojavexecForAndroidVm for why that matters here).
 * This is a different java.lang.Class object than org.lwjgl.glfw.
 * CallbackBridge in the game's own classpath - two separate JVMs, two
 * separate loads of the same class name - so the native method
 * declarations that class needs don't belong here; libpojavexec.so only
 * calls back into these six methods when running as "the Android side."
 */
public class CallbackBridge {
    public static String accessAndroidClipboard(int action, String data) {
        return null;
    }

    public static void onGrabStateChanged(boolean grabbed) {
    }

    public static void onCursorShapeChanged(int shape) {
    }

    public static void onGraphicOutput() {
    }

    public static void onDirectInputEnable() {
    }

    public static boolean notifyLauncher(int type, int[] data) {
        return false;
    }
}
