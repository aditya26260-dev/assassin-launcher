package net.kdt.pojavlaunch.utils;

/**
 * libpojavexec.so resolves these two through the automatic Java_ClassName_
 * Method JNI convention, not RegisterNatives, so the package and class name
 * here are load-bearing - they're baked into the compiled .so as
 * Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow and can't be
 * renamed to match this project's own package layout. Confirmed against
 * the .so's own strings and against Amethyst-Android's real JREUtils.java,
 * which declares the same two methods.
 */
public class JREUtils {
    public static native void setupBridgeWindow(Object surface);

    public static native void releaseBridgeWindow();
}
