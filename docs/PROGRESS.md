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

Game launch pipeline is now getting deep into actual Minecraft startup (past JVM creation, past renderer class loading, into "Setting user: ..."). Last active fix in flight: a native SIGSEGV in `nativeInitializeGLFWNativeBridge` inside `libpojavexec.so`, traced to a real two-JVM tracking mechanism in that library (it expects to be loaded once from the Android app's own JVM *and* once from the embedded game JVM, tracking each as a separate `JavaVM*`). A stub `org.lwjgl.glfw.CallbackBridge` was added at `app/src/main/java/org/lwjgl/glfw/CallbackBridge.java` (note: `src/main/java`, NOT `src/main/kotlin` - `.java` files under `kotlin/` silently don't compile in this project) so the Android-side load has something to bind to. Not yet confirmed fixed on-device - next session should pick up here with a fresh logcat.

Also just finished (implemented, not yet delivered/tested): global launcher settings - default renderer override, default max RAM, notification detail toggle - in `LauncherSettings.kt` / `LauncherSettingsStore.kt` / `SettingsScreen.kt`, wired into `GameSessionService` and `GameLaunchOrchestrator`.

## What's built and working

- First-launch setup: hardware detection, driver checks, real progress pacing, Java 21 bundled (no download wait)
- Instance creation/editing with per-instance renderer/RAM/Java overrides
- Version content (libraries + client jar) downloads the moment a version is picked, not just at Play time, with a visible progress indicator
- Account management: multiple Microsoft + offline accounts, switch/remove via icons, offline accounts require an existing Microsoft account first
- A `DocumentsProvider` (`AppStorageDocumentsProvider`) exposing the app's own files to any file picker, same as Amethyst

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

## How Aditya wants this worked

- **Always** give the logcat commands (`adb logcat -c` before Play, `adb logcat -d > $HOME/crash_log.txt` after) whenever asking for a log - every single time, no exceptions, he's had to repeat this many times
- Code changes: deliver actual files (zip via `present_files`), never copy-paste patch scripts. Always include the full `unzip` + `git add` + `git commit` + `git push` sequence combined into one block
- Keep code comments short and necessary only - no long "here's what I investigated/why" narration in the code itself. That kind of detail belongs in this doc, not inline
- No em dash character anywhere in user-facing (in-app) text, and no other "obviously AI-written" phrasing - write it like a person would
- Don't guess at fixes - verify against real source (clone the actual repo, check the actual binary's symbols) before proposing something, the same way this doc's "key facts" section was built
- One question at a time if something's genuinely ambiguous; otherwise just proceed and say what was assumed
- Termux specifics: no `/tmp`, use `$HOME`. Downloaded files land at `~/storage/downloads/`. Repo checkout is at `~/assassin-launcher-real`
