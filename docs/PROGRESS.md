# Assassin Launcher - Project Status

Open-source (GPL-3.0) Android Minecraft Java Edition launcher. Kotlin + Jetpack Compose UI, C++/JNI native bridge for the embedded JVM. arm64-v8a only. Repo: `aditya26260-dev/assassin-launcher`, branch `master`.

Aditya is not a professional developer - Claude does all research, architecture, and implementation.

## Stack

- Renderers: MobileGlues (MC 1.17+), Krypton Wrapper (older versions), Turnip (Vulkan/Adreno 7xx)
- LWJGL 3.3.3 (Android port) + native `.so`s, all vendored from Amethyst-Android's real released APK
- JDK: Java 21 bundled in the APK (`app/src/main/assets/runtimes/jre21-android-arm64.tar.xz`); other majors download on demand from AngelAuraMC's releases
- JVM launch: `JLI_Launch` called in-process via dlopen, not a spawned `bin/java` process
- Mods: Modrinth only
- Auth: Microsoft (RaphiMC/MinecraftAuth) + offline accounts, both with real multi-account switching
- Reference implementations when stuck: **Amethyst-Android** (primary - AngelAuraMC's actively maintained PojavLauncher successor), ZalithLauncher2 (secondary, same lineage, often has newer/different fixes worth checking)

## Current state

Game launch pipeline reaches real Minecraft startup and keeps going past it: JVM creation, renderer class loading, "Setting user: ...", the render thread starting, LWJGL's backend initializing. The two-JVM `CallbackBridge` SIGSEGV from last time is fully resolved and confirmed on-device (see key facts for what it actually needed - it was two separate problems, not one).

Last active fix in flight: past the rendering-surface issues above, the embedded JVM reaches `net.minecraft.client.main.Main`, runs for several seconds, then crashes inside `libc.so`'s `strcmp`, called from `glfwInit()` by way of a function named `pojavInit` in `libpojavexec.so`. First attempt at this fixed the wrong thing: set `POJAV_NATIVEDIR` (which Amethyst's real code does set unconditionally, and which may matter for something else, so left in) but that never was what this specific crash needed - same exact crash, same offset, afterward. Installed `binutils-aarch64-linux-gnu` and actually disassembled `pojavInitOpenGL` (the function `pojavInit` calls into) with `objdump` rather than guess again from strings alone. It does, unconditionally, `strcmp(getenv("FORCE_VSYNC"), "true")` with no null check - that's the real crash. Right after that check it also does `strncmp(getenv("AMETHYST_RENDERER"), "opengles", 8)` and, on no match, `strcmp(..., "vulkan_zink")` - also unchecked, so fixed that one too before it could cause a second crash. Fix: `jvm_launcher_bridge.cpp` now also sets `FORCE_VSYNC=false`, and `GameLaunchOrchestrator.prepareRenderEnvironment` now sets `AMETHYST_RENDERER` to `"opengles"` or `"vulkan_zink"` depending on the chosen `RenderPath`, before the existing per-renderer setup. Not yet confirmed on-device - next session should pick up here with a fresh logcat.

## What's built and working

- First-launch setup: hardware detection, driver checks, real progress pacing, Java 21 bundled (no download wait)
- Instance creation/editing with per-instance renderer/RAM/Java overrides
- Version content (libraries + client jar) downloads the moment a version is picked, not just at Play time, with a visible progress indicator
- Account management: multiple Microsoft + offline accounts, switch/remove via icons, offline accounts require an existing Microsoft account first
- A `DocumentsProvider` (`AppStorageDocumentsProvider`) exposing the app's own files to any file picker, same as Amethyst
- Global launcher settings - default renderer override, default max RAM, notification detail toggle - confirmed on-device
- Android-side `org.lwjgl.glfw.CallbackBridge` stub letting `libpojavexec.so` load from the Android app's own JVM without crashing - confirmed on-device

## Not built yet

- In-game settings overlay (like Zalith2/Amethyst's floating menu during gameplay)
- Fabric/Forge/NeoForge/Quilt/Optifine installation (the `ModLoader` enum exists, nothing implements installing one)
- Asset (textures/sounds/lang) downloading - never confirmed working end to end
- Custom PNG cursors (needs an image picker)
- UI to create a *second* instance was never directly observed/tested - the data layer supports multiple profiles, only one has ever been confirmed created (the auto default)

## Key facts worth not re-discovering

- `LD_LIBRARY_PATH` order matters to `libjli.so`'s internal re-exec check: the directory containing `libjvm.so` (`lib/server`) must be *first*, not just present
- `JLI_Launch` must never be called twice in one process - concurrent/duplicate launches corrupt its internal state (looks like garbled JVM options, not an obvious "called twice" error). `GameSessionService` now guards against this; don't remove that guard
- Bundled asset extraction (`AndroidLwjglProvider.extractAssetDir`) always overwrites now - it used to skip-if-exists, which meant updating a bundled file in a new build silently did nothing on devices that already had it extracted. Keep it that way
- `android:allowNativeHeapPointerTagging="false"` is required in the manifest - without it, Android 11+ aborts on tagged-pointer mismatches from this JDK build
- Version manifest rule evaluation (`VersionRuleEvaluator`) defaults to *excluded* when a value's rules exist but none match the current platform - getting this backwards silently included macOS/Windows/x86-only flags on every launch
- When a native `.so` needs pairing with a specific jar (e.g. `libpojavexec.so` + `lwjgl-3.3.3-merged-modules.jar`), grabbing "the latest of each" from Amethyst's releases is not safe - their downloadable components can drift out of sync with their own native builds. Verify by checking the actual native's exported symbols (`strings`/`nm`) against what the jar's class file declares
- GitHub's unauthenticated API rate limit gets hit fast in a working session - prefer `git clone`/`codeload.github.com`/raw.githubusercontent.com over `api.github.com` for repeated lookups
- The Android-side `CallbackBridge` stub is two unrelated mechanisms, not one: five plain static methods (`accessAndroidClipboard`, `notifyLauncher`, `onDirectInputEnable`, `onGrabStateChanged`, `getAndroidDPI`) that `libpojavexec.so` finds via `GetStaticMethodID` by name, plus eleven `native` methods (`nativeSetUseInputStackQueue`, `nativeSendChar`, `nativeSendCharMods`, `nativeSendKey`, `nativeSendCursorPos`, `nativeSendMouseButton`, `nativeSendScroll`, `nativeSendScreenSize`, `nativeSetWindowAttrib`, `nativeCreateGamepadButtonBuffer`, `nativeCreateGamepadAxisBuffer`) that it binds via an explicit `RegisterNatives` call in the same `JNI_OnLoad`, and fails on the whole batch if even one is missing. Verified against both the vendored `.so`'s own strings and Amethyst's real `app_pojavlauncher/.../CallbackBridge.java`, which has the exact same seventeen
- `@CriticalNative` is `dalvik.annotation.optimization.CriticalNative` (a platform annotation, built into the SDK, API 26+ and officially public since API 34) - not `androidx.annotation`. Easy mistake since it sits right next to `@Keep` in Amethyst's source and looks like it should be the same package; check every import individually, don't assume from how two annotations are used together
- A `Service` cannot host an Android `Surface`/window under any circumstances - only a live Activity window (or an `AndroidExternalSurface`/`AndroidEmbeddedExternalSurface` inside one) can. `GameSessionService` owning the actual JVM orchestration is fine and doesn't need to change; the Surface just has to be created somewhere inside `MainActivity`'s own Compose tree first, and that creation has to happen, and be handed to native code, before `GameSessionService.start` is called - GLFW's Android port reaches for a native window immediately on `glfwInit()`, not lazily on first draw
- Native methods bound via the automatic `Java_ClassName_Method` JNI convention (unlike `CallbackBridge`'s `RegisterNatives` methods) are tied to the exact package and class name baked into the compiled `.so` - `net.kdt.pojavlaunch.utils.JREUtils` in this case, confirmed via the `.so`'s own strings. That class has to keep Amethyst's exact package name in this repo's own source tree; it can't be renamed to match this project's own `com.assassinlauncher.launcher` convention the way `RegisterNatives`-bound classes could in principle be
- Any native method (`UnsatisfiedLinkError: No implementation found for ...`) needs the library that implements it actually loaded first via `System.load`/`AndroidLwjglProvider.preloadPojavexecForAndroidVm()` - obvious in hindsight, but easy to miss when the call site handing off the Surface (Compose UI code) is in a different file from the call site that happens to preload the library (`GameLaunchOrchestrator`), with no compiler-enforced ordering between them
- Calling `AndroidLwjglProvider.ensureNatives()` + `.preloadPojavexecForAndroidVm()` twice, from two separate provider instances, is NOT the harmless no-op it looks like - `ensureNatives()` re-extracting the same file while the first copy is still mapped and in active use corrupts it, and the corruption doesn't necessarily crash immediately; it showed up later as a SIGSEGV inside `JNI_OnLoad` on an entirely separate, later load of the same library from the embedded game JVM. There should be exactly one preload call site (`GameLaunchOrchestrator`'s existing one); anything else that needs the library loaded first (like handing off the Surface) should happen from there too, not duplicate the preload to get its own ordering right
- `libpojavexec.so` reads several `POJAV_*` environment variables via `getenv()` with no null-checking before use (confirmed the hard way: `POJAV_NATIVEDIR` missing crashed inside `strcmp` on the null result). Amethyst's real `JREUtils.java` sets `POJAV_NATIVEDIR` (their own `ApplicationInfo.nativeLibraryDir`) unconditionally, and sets `POJAV_VSYNC_IN_ZINK` / `POJAV_EMUI_ITERATOR_MITIGATE` / `POJAV_FFMPEG_PATH` / `LIBGL_GLES` / `POJAV_LOAD_TURNIP` only for specific devices or renderer choices - the unconditional one is required regardless of configuration, the conditional ones are presumably fine to leave unset until this project actually implements the specific renderer/feature each one gates

- `libpojavexec.so` has several `strcmp(getenv(...), ...)` / `strncmp(getenv(...), ...)` call sites with no null check, at least in `pojavInitOpenGL`: `FORCE_VSYNC` (compared against `"true"`) and `AMETHYST_RENDERER` (compared against `"opengles"` as an 8-char prefix, then `"vulkan_zink"` exactly) so far - both now always set. If another one of these turns up, `binutils-aarch64-linux-gnu` (`apt-get install`, not present by default in this container) gives a real `aarch64-linux-gnu-objdump`/`aarch64-linux-gnu-nm` that can find it directly (disassemble the function named in the crash frame; find the string literals at the addresses its `adrp`+`add` pairs compute) rather than guessing from `strings` output alone, which is what led to the wasted `POJAV_NATIVEDIR` attempt - that variable is real (Amethyst does set it) but wasn't what this particular crash needed
- The offset in a `hs_err` frame like `libpojavexec.so+0x91c8] pojavInit+0xa4` is the crash PC's offset into the library; `pojavInit+0xa4` separately means that PC is 0xa4 bytes into `pojavInit` specifically - if that instruction is a harmless `mov`/`b` rather than a memory access, it's almost certainly a return address left on the stack from a call `pojavInit` made (here, to `pojavInitOpenGL`), not the crash site itself; disassemble a little before it to find the actual `bl` and follow it into the callee

## How Aditya wants this worked

- **Always** give the logcat commands (`adb logcat -c` before Play, `adb logcat -d > $HOME/crash_log.txt` after) whenever asking for a log - every single time, no exceptions, he's had to repeat this many times
- Code changes: deliver actual files (zip via `present_files`), never copy-paste patch scripts. Always include the full `unzip` + `git add` + `git commit` + `git push` sequence combined into one block
- Keep code comments short and necessary only - no long "here's what I investigated/why" narration in the code itself. That kind of detail belongs in this doc, not inline
- No em dash character anywhere in user-facing (in-app) text, and no other "obviously AI-written" phrasing - write it like a person would
- Don't guess at fixes - verify against real source (clone the actual repo, check the actual binary's symbols) before proposing something, the same way this doc's "key facts" section was built
- One question at a time if something's genuinely ambiguous; otherwise just proceed and say what was assumed
- Termux specifics: no `/tmp`, use `$HOME`. Downloaded files land at `~/storage/downloads/`. Repo checkout is at `~/assassin-launcher-real`
