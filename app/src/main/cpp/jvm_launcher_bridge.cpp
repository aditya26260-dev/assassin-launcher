#include <jni.h>
#include <dlfcn.h>
#include <csignal>
#include <cstring>
#include <cstdlib>
#include <cerrno>
#include <unistd.h>
#include <vector>
#include <string>
#include <thread>
#include <android/log.h>

#define LOG_TAG "AssassinJvmLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define STDIO_LOG_TAG "MinecraftJvmOutput"

// JLI_Launch's real signature - confirmed against Amethyst's actual
// jre_launcher.c (PojavLauncher lineage), not assumed from memory alone.
// This is the JDK's own internal java-launcher entry point: not a
// documented public API, but the mechanism the entire Android Minecraft
// launcher ecosystem depends on to embed a JVM in-process rather than
// spawning bin/java as a subprocess. In-process is what later lets the
// Android rendering Surface be passed straight to native code with no
// cross-process boundary to cross.
typedef jint(JNICALL *JLI_Launch_t)(
        int argc, char **argv,
        int jargc, const char **jargv,
        int appclassc, const char **appclassv,
        const char *fullversion,
        const char *dotversion,
        const char *pname,
        const char *lname,
        jboolean javaargs,
        jboolean cpwildcard,
        jboolean javaw,
        jint ergo);

// The embedded JVM's own stdout/stderr - including whatever it prints
// when it calls abort() internally, which is usually the actual reason -
// go to this process's real fd 1/2, which nothing was reading. Silently
// lost every time, which is exactly what made the last SIGABRT
// undiagnosable: the handler below logs that an abort happened, but not
// why. This redirects both into logcat instead, via a pipe and a
// detached reader thread, called before JLI_Launch can print anything.
// Standard technique for an embedded-JVM-on-Android situation like this
// one, not exotic - leaving it unredirected was the actual gap.
static void redirectStdioToLogcat() {
    int pipeFds[2];
    if (pipe(pipeFds) != 0) {
        LOGE("Failed to create stdio redirect pipe: %s", strerror(errno));
        return;
    }
    dup2(pipeFds[1], STDOUT_FILENO);
    dup2(pipeFds[1], STDERR_FILENO);
    close(pipeFds[1]);

    int readFd = pipeFds[0];
    std::thread([readFd]() {
        char buffer[1024];
        std::string pending;
        ssize_t bytesRead;
        while ((bytesRead = read(readFd, buffer, sizeof(buffer) - 1)) > 0) {
            buffer[bytesRead] = '\0';
            pending += buffer;
            size_t newlinePos;
            while ((newlinePos = pending.find('\n')) != std::string::npos) {
                std::string line = pending.substr(0, newlinePos);
                if (!line.empty()) {
                    __android_log_write(ANDROID_LOG_INFO, STDIO_LOG_TAG, line.c_str());
                }
                pending.erase(0, newlinePos + 1);
            }
        }
        if (!pending.empty()) {
            __android_log_write(ANDROID_LOG_INFO, STDIO_LOG_TAG, pending.c_str());
        }
    }).detach();
}

// The JVM raises SIGABRT on a fatal internal error rather than a catchable
// exception. Unhandled, Android just silently kills the whole process -
// indistinguishable from any other crash, with nothing logged. This turns
// it into at least a logged, deliberate exit instead. It is not the real
// crash-reporting flow from architecture doc 5.6 (which doesn't exist
// yet) - just making sure a JVM abort is diagnosable rather than silent.
[[noreturn]] static void handleJvmAbort(int signal) {
    LOGE("Embedded JVM raised SIGABRT (signal %d)", signal);
    _exit(134); // 128 + SIGABRT, conventional shell exit-code encoding
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_dlopenJvmLibrary(
        JNIEnv *env, jobject /* this */, jstring absolutePathJ) {
    const char *path = env->GetStringUTFChars(absolutePathJ, nullptr);
    // RTLD_GLOBAL: later libraries in the load sequence (and JLI_Launch
    // itself resolving the JVM's own symbols) need to see symbols from
    // libraries opened earlier in the sequence. This mirrors the real,
    // working load order confirmed against JREUtils.java's
    // initJavaRuntime rather than relying on the dynamic linker to
    // resolve everything transitively on its own - Android's linker has
    // been unreliable about that for app-private library paths in
    // exactly this kind of chained-dependency situation (the same
    // underlying class of problem libadrenotools' namespace bypass
    // exists to work around for the Vulkan driver case).
    void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        LOGE("dlopen failed for %s: %s", path, dlerror());
    }
    env->ReleaseStringUTFChars(absolutePathJ, path);
    return handle != nullptr;
}

// Calls JLI_Launch from an already dlopen'd libjli.so, embedding a full
// JVM inside this process and - under normal circumstances - NOT
// returning at all. This is worth stating plainly: the real java binary
// exits the OS process directly once the program it launched finishes,
// and JLI_Launch is the same code path. Every PojavLauncher-lineage
// project's own code treats any return from this call (success or
// failure) as "the process needs to fully die now" (Amethyst calls this
// Tools.fullyExit()) rather than "control comes back to the launcher UI."
// In practice that means closing Minecraft closes this app's process too
// - a real architectural constraint, not an oversight, and one the
// eventual "return to launcher after the game closes" UX needs to design
// around (a restart trampoline, or accepting relaunch-to-play-again for
// now) rather than something this function can paper over.
extern "C" JNIEXPORT jint JNICALL
Java_com_assassinlauncher_launcher_nativebridge_NativeBridge_launchEmbeddedJvm(
        JNIEnv *env, jobject /* this */,
        jstring jliLibraryPathJ, jobjectArray argsJ,
        jstring fullVersionJ, jstring dotVersionJ) {

    const char *jliLibraryPath = env->GetStringUTFChars(jliLibraryPathJ, nullptr);
    // Already loaded by an earlier dlopenJvmLibrary call, in dependency
    // order; re-opening the same path is a refcount bump that hands back
    // the existing handle, not a second real load.
    void *jliHandle = dlopen(jliLibraryPath, RTLD_NOW | RTLD_GLOBAL);
    env->ReleaseStringUTFChars(jliLibraryPathJ, jliLibraryPath);

    if (jliHandle == nullptr) {
        LOGE("libjli.so not loaded - cannot resolve JLI_Launch: %s", dlerror());
        return -1;
    }

    auto jliLaunch = reinterpret_cast<JLI_Launch_t>(dlsym(jliHandle, "JLI_Launch"));
    if (jliLaunch == nullptr) {
        LOGE("JLI_Launch symbol not found: %s", dlerror());
        return -1;
    }

    jsize argc = env->GetArrayLength(argsJ);
    std::vector<char *> argv(static_cast<size_t>(argc));
    for (jsize i = 0; i < argc; i++) {
        auto jstr = static_cast<jstring>(env->GetObjectArrayElement(argsJ, i));
        const char *chars = env->GetStringUTFChars(jstr, nullptr);
        argv[i] = strdup(chars); // independent copy - safe to release the jstring right after
        env->ReleaseStringUTFChars(jstr, chars);
        env->DeleteLocalRef(jstr);
    }

    const char *fullVersion = env->GetStringUTFChars(fullVersionJ, nullptr);
    const char *dotVersion = env->GetStringUTFChars(dotVersionJ, nullptr);

    signal(SIGABRT, handleJvmAbort);
    signal(SIGPIPE, SIG_IGN); // a closed game-log pipe shouldn't kill the whole VM
    redirectStdioToLogcat();

    // Confirmed straight from Amethyst's actual JREUtils.launchJavaVM, not
    // guessed: it calls chdir() to the game directory immediately before
    // launching, and this project has never called chdir() anywhere.
    // "Current folder is:/" - root, not any real game directory - has been
    // sitting in every single crash log this entire investigation.
    // Reusing HOME rather than adding a new parameter - GameLaunchOrchestrator
    // already sets it to this exact same directory, and ensures it exists
    // (chdir fails outright otherwise) right before calling this function.
    const char *homeDir = getenv("HOME");
    if (homeDir != nullptr && chdir(homeDir) != 0) {
        LOGE("chdir to HOME (%s) failed: %s", homeDir, strerror(errno));
    }

    LOGI("Calling JLI_Launch, argc=%d, fullversion=%s", argc, fullVersion);
    jint exitCode = jliLaunch(
            argc, argv.data(),
            0, nullptr,
            0, nullptr,
            fullVersion, dotVersion,
            "java", "openjdk",
            JNI_FALSE, JNI_FALSE, JNI_FALSE, JNI_FALSE);
    LOGI("JLI_Launch returned %d (see the comment above - this line may "
         "never actually execute)", exitCode);

    env->ReleaseStringUTFChars(fullVersionJ, fullVersion);
    env->ReleaseStringUTFChars(dotVersionJ, dotVersion);
    for (char *arg : argv) {
        free(arg);
    }

    return exitCode;
}
