# Native bridge methods are called from C++ by name (JNI) - R8 can't see
# that call site, so it needs to be told not to strip or rename these.
-keepclasseswithmembernames class com.assassinlauncher.launcher.nativebridge.NativeBridge {
    native <methods>;
}
