# Phase 1 Architecture — Assassin Launcher

Status: in progress. Each decision below has a one-line reason, per the brief.
Not all of section 5 is covered yet — see the bottom for what's left.

---

## 5.1 Language and runtime split

**Kotlin:** UI/activities, account and login flow, mod/resource/shader
manager (Modrinth/CurseForge API calls), settings, instance/profile
management, the foreground service's lifecycle, crash-log parsing for the
diagnostics system.

**C/C++ (JNI):** the actual rendering bridge — driver loading (choosing and
loading the right Turnip/Panfrost/system Vulkan .so, or GL4ES/MobileGlues/
Zink .so), the EGL/surface bridge between Android's surface system and the
JVM's native calls, and JNI glue directly against those libraries.

Reason specific to this project: every library actually doing the
performance-critical work (GL4ES, MobileGlues, Turnip, Zink, OpenJDK's own
native parts) is an existing C/C++ codebase we're linking against, not
writing from scratch. JNI-to-C++ is the direct path to that. Everything
else — account flows, API calls, settings, file management — never touches
the render loop, so there's no performance case for it to be native, and
Kotlin is simpler to write and maintain. Not using Rust for the native
layer: it would add a Rust-to-C boundary on top of the JNI boundary without
removing any translation overhead, since every library we're bridging to is
already C/C++. That could change later for a genuinely new, isolated native
component with no C dependency, but that's not this layer.

## 5.2 JVM strategy

**Updated - real and implemented, not just planned.** Bundling all four
Java versions (8/17/21/25) directly in the APK would mean 400-600MB of
baseline app size for runtimes most installs won't need at once. Instead:
download-on-demand per Minecraft version's actual requirement, from
AngelAuraMC/angelauramc-openjdk-build's GitHub Releases - the same
permanent source Amethyst Launcher's own real, working code downloads
from (confirmed directly in their code, not assumed). Cached locally in
internal storage once fetched (same noexec-storage reasoning as the
Turnip driver and MobileGlues/Krypton Wrapper - a JVM binary has to live
somewhere actually executable). `JvmRuntimeManager` implements this.
Extraction uses Apache Commons Compress + its XZ codec, the same library
pairing confirmed in Amethyst's real build.gradle, with the executable
permission bits explicitly restored after extraction - tar preserves them
in its metadata but doesn't apply them automatically, confirmed by
checking a real supplied archive directly (`bin/java` really is `rwxr-xr-x`
in the tar entry, and that bit has to be reapplied by hand after writing
the file).

Auto-selection per Minecraft version still needs to be wired to the actual
version manifest/loader logic once that exists - `JvmRuntimeManager`
provisions a requested version, it doesn't yet decide which one a given
instance needs.

**Not locking in specific JVM flags yet.** The brief is explicit that flag
lists shouldn't be copied without verification, and actually verifying which
flags measurably help per device tier needs real benchmarking on real
devices — not something to guess at from here. This stays open until there's
a build to test and your phone(s) to test it on.

## 5.7 Directory and instance structure

One shared `.minecraft`-style root: `versions/`, `libraries/`, and `assets/`
shared across all instances (avoids duplicating the same version files per
instance), plus a per-instance folder holding that instance's `mods/`,
`config/`, `resourcepacks/`, `shaderpacks/`, `saves/`, and its own options/
overrides. This is the same model PrismLauncher and MultiMC-family desktop
launchers use, and it's what makes mods/configs/resource packs/worlds behave
exactly like they do on desktop, which is what the brief asks for directly.

## 5.3 Rendering strategy — automatic selection logic

**Updated after Phase 0 research round two** (docs/PHASE0_RESEARCH.md section
12): Zink is no longer auto-selected — real user testing found it performs
poorly despite reaching real OpenGL 4.5 on paper, so it's manual-override-
only until validated on real devices. LTW was considered as a second
option alongside MobileGlues but dropped by explicit decision — keeping one
well-maintained choice (MobileGlues, tracked at its current latest version)
rather than two overlapping ones. MobileGlues gained the 1.17+ version
floor it actually has, on top of its existing GLES capability floor. Code
in `RenderPathSelector.kt` reflects this; the description below is the
current state, not the original first draft.

Inputs cached once per device (re-checked if Android version or GPU driver
string changes): GPU vendor and exact chip, system Vulkan driver's version
and feature support (dynamic rendering, push descriptors), Android API level.
Evaluated per instance at launch, given that instance's Minecraft version
and mod list.

**Step 1 — mod conflict check runs first, before any hardware logic.**
Sodium + VulkanMod together is a known-incompatible combination (Phase 0):
show the popup from 5.6 (disable A / disable B via `.disabled` rename /
cancel) before touching renderer selection at all.

**Step 2 — if this instance is Minecraft 26.2+ and Vulkan is toggled on:**
- System driver already meets Minecraft's actual floor (Vulkan 1.2 +
  dynamic rendering + push descriptors) → use it directly, no Turnip needed.
- System driver falls short, device is Adreno with a matching bundled Turnip
  build → auto-switch, toast per 5.6 ("switched to Turnip: system Vulkan
  driver doesn't meet Minecraft's requirement").
- System driver falls short, device is Mali → Panfrost/PanVK is only
  realistically viable on Mali-G610 today (Phase 0). This is a real
  tradeoff, not a clearly-safe auto-fix, so this one prompts the user
  rather than silently switching, per the brief's own distinction in 5.6.
- No viable Vulkan path at all → fall back to Step 3, with a toast
  explaining why.

**Step 3 — OpenGL path (pre-26.2 versions, or Vulkan unavailable/off):**
- Adreno, and a matching Turnip build loads and initializes successfully →
  **Zink over Turnip.** This is the only path that reaches real OpenGL 4.5
  on Android (Phase 0, PojavLauncher's own wiki) — meets Sodium's actual
  stated requirement, not just vanilla's floor. Turnip has to actually
  verify as initialized first, since Zink crashes outright without a
  working Vulkan driver underneath it (Phase 0) — never attempt Zink on an
  unverified driver.
- Turnip unavailable/fails to init, or non-Adreno, or Mali → **MobileGlues**
  if the device meets its GLES 3.0+ minimum (best at 3.2). This is the
  modern default across essentially the whole competitive set now, and the
  one built specifically with Minecraft in mind, including Iris/OptiFine
  shader support and mods like Create.
- Below GLES 3.0, or MobileGlues fails to init → **Krypton Wrapper
  (NG-GL4ES)** first, falling back further to base GL4ES/HolyGL4ES for the
  weakest/oldest hardware. Broadest reach, lowest ceiling, last resort
  rather than default, given the real ceiling and texture-atlas issues
  Phase 0 found here.
- **Hard exception:** Mali + Minecraft ≤1.16.5 must never attempt Zink —
  confirmed unfixed upstream driver bug (Phase 0). Skip straight to
  MobileGlues/GL4ES-family for that specific combination.

**Turnip build selection isn't one file for all Adreno** (Phase 0 section 8):
6xx family gets the mature mainline build; 7xx is split, with specific
chips (like the 710/720/722 the user supplied) needing the separately
patched build rather than mainline 7xx; 8xx gets its own newer, rougher
branch, flagged to the user as less mature rather than presented as equal
footing to 6xx/7xx. An unrecognized Adreno chip tries the closest
generation match, and if Turnip fails to initialize at all, the whole tree
above falls through to Step 3's non-Adreno path rather than erroring out.

**How the driver swap actually happens, mechanism now identified** (this
was a real gap before now). The established, industry-standard approach —
used by Winlator, Skyline, Vita3K, Yuzu/Eden, and what PojavLauncher-family
launchers build on too — is **libadrenotools** (bylaws/libadrenotools,
BSD-2-Clause, confirmed at its own repo). It loads a custom driver file as
a replacement for the system's libvulkan.so without root, through Android's
linker namespace mechanism. Its own stated support window is **Android 9+
(API 28), arm64 only** — confirmed both in its README and its source (it
explicitly bails out below API 28). That's a real constraint this project
inherits on top of everything above: the Turnip-swap path specifically
needs API 28+, even though the app's own minSdk is 26 for the
OpenGL-translation path. Below API 28, or anywhere libadrenotools can't
load a driver, the tree falls through to the non-Turnip options rather than
attempting Zink at all. Actually vendoring and wiring up libadrenotools is
real, fragile, low-level native work (linker namespace manipulation)
that needs a genuine compile-and-run cycle to get right rather than being
written blind — the next concrete native task, pulling in the real
upstream library rather than reimplementing its approach.

**Manual override** (6.8/7.11): every automatic choice above stays visible
and changeable per instance. If the user forces system Vulkan and it
doesn't actually work, launch still has to succeed by falling back rather
than failing outright — matches the brief's requirement directly.

## 5.4 Mod compatibility and patch layer

**Sodium/VulkanMod parity.** Not a promise made in the abstract — it's what
5.3 already does. Zink-over-Turnip reaching real OpenGL 4.5 on Adreno is the
actual mechanism that gets close to PC parity for Sodium specifically,
because it sidesteps the GLES-translation ceiling Sodium's own docs warn
about, rather than translating around it.

**Create** — works today through MobileGlues, which lists it by name as a
supported mod with custom rendering routines. It has had real, specific,
recently-fixed bugs (rendering issues when DSA is enabled) — meaning this
needs to track MobileGlues' own fix history and stay current, not a
one-time integration.

**Creamy Keys** — turns out to be a simple client-side cosmetic mod
(mechanical-keyboard sound effects on keypress), already used on
PojavLauncher-based launchers with no special native dependency. No
particular patch needed, just normal mod support.

**Distant Horizons** — the real, well-documented case. PojavLauncher's own
issue tracker shows a consistent pattern: DH does its own OpenGL capability
check at startup and fails cleanly with a real error message when a weaker
GLES-translation setup reports back below its OpenGL 3.2+ floor — so part of
the fix is just making sure DH sees an accurate capability report instead of
a translation layer's degraded one. Separately, there's a specific,
still-open crash combining Zink + Forge + DH together during world creation,
independent of the capability-check issue. Worth checking PojavLauncher's
own tracker directly when this gets built — some reports are tagged
"fix/patch available in the issue, not yet implemented in the launcher,"
meaning there's already-identified fixes sitting there unused.

**Bobby** — no real compatibility problem found. Confirmed to run alongside
Distant Horizons without conflict (they solve different problems — cached
chunks vs. generated far terrain), so this one's mostly just normal mod
support plus making sure it's excluded from any chunk-related conflict
warnings the diagnostics system might otherwise flag.

**Simple Voice Chat.** The "module not supported on this OS" pattern traces
to the mod expecting a desktop-style audio backend that has no Android
implementation out of the box — confirmed by the fact a community mod
("Android Audio Shim") already exists solely to bridge SVC's microphone/
speaker calls to Android's actual audio APIs. Rather than requiring users to
install that separately, the same bridging approach belongs directly in the
launcher's native layer, paired with a proper Android runtime microphone
permission request at the point SVC actually needs it (not upfront at
install).

**"Vulkan Android Libraries" / "Vulkan Dreno"-style mods.** Now concretely
identified: "Vulkan Android Libraries" supplies the Android-native binaries
VulkanMod needs to run on a phone at all (VulkanMod is originally PC-only).
"Vulkan Dreno" separately patches VulkanMod's Vulkan queue-family detection,
which newer Adreno 7xx chips (730, 740, and similar) expose differently than
VulkanMod's own code expects, causing crashes without it. Both problems are
solvable at the launcher level instead of requiring a mod: ship the
Android-native libs VulkanMod needs directly, and apply the same
queue-family fallback logic ourselves. If a user has either mod installed
anyway, our own fix needs to be safe to sit alongside it rather than assume
it's the only thing touching that code path.

## 5.5 Background stability

A genuine Android foreground service, started at the moment the user taps
Play (a direct foreground user action — matters because Android 15 now
restricts starting foreground services from the background, so this needs
to start from an active user gesture, not some indirect trigger). Stopped
promptly when the game session actually ends, not left lingering.

**Service type:** none of Android's predefined foreground service types
(mediaPlayback, location, camera, microphone, dataSync, phoneCall, etc.)
actually fit "keep an active game session running" — `specialUse` is
exactly what that category exists for. Confirmed current as of this month:
it requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation string in the
manifest, and that explanation gets reviewed by Google at Play Console
submission time — worth writing that explanation carefully when we get
there, ties into the Phase 4 distribution review. Runtime permission
requirements are tied to whatever type gets declared; the exact permission
name gets nailed down when the manifest is actually written in Phase 2
rather than guessed at here.

**What this doesn't need:** WorkManager is the wrong tool here — that's for
deferred, non-time-critical work, and an active game session is neither.
Also not assuming an aggressive wake lock is needed by default — the user
switching to another app doesn't mean the screen is off, so the normal case
is the foreground service classification alone keeping the process from
being deprioritized. Worth actually verifying this on a real device once
there's a build, rather than asserting it now.

**What code alone can't fix:** several Android OEM skins (MIUI, ColorOS,
FuntouchOS, OneUI, among others) layer their own, more aggressive
background-app-killing behavior on top of stock Android's, regardless of
the foreground service being correctly declared. The practical mitigation
is an in-app guided flow that walks the user to their specific device's
battery-optimization/autostart exemption screen, since this can't be solved
from inside the app itself. Worth including from the start rather than
treating it as a later polish item, since it's a real, common cause of
exactly the lag-when-backgrounded problem this section exists to prevent.

## 5.6 Smart diagnostics and auto-configuration

The conflict-detection and crash-parsing rules aren't generic — they're
built directly from what Phase 0 actually found, so the list grows as real
patterns turn up rather than staying a fixed set written once:

**Known conflicts to flag** (popup: disable A / disable B via `.disabled` /
cancel, per the brief): Sodium+VulkanMod, and more broadly any pairing of
Sodium/OptiFine/Canvas/VulkanMod together, since Phase 0 confirmed they're
all alternative full-renderer replacements competing for the same job.
**Known non-conflicts, so the system doesn't false-positive them:**
Bobby+Distant Horizons — confirmed compatible in Phase 0, they solve
different problems.

**Crash log patterns to auto-recognize**, from real examples Phase 0 turned
up: Distant Horizons' own explicit "openGL version 3.2+: false <- REQUIRED"
message (means the renderer in use is reporting a degraded capability floor
to the mod — the fix is making sure DH sees the real device capability, per
5.4); a missing native library at `dlopen` (means a runtime/driver file
didn't get bundled or load correctly for that specific device); a Turnip or
Vulkan driver initialization failure (the case 5.3's auto-switch logic
already handles). More patterns get added here as they turn up during
Phase 2 testing — this list isn't meant to be exhaustive on day one.

**Silent-fix vs. prompt, the same line drawn in 5.3:** system driver not
meeting Minecraft's Vulkan floor with a working Turnip fallback available =
clearly correct, fix silently with a toast. Mali Vulkan fallback = a real
tradeoff (Phase 0: genuinely unreliable), always prompt, never silent.

**Usable crash reports, not just detection.** Phase 0's gap analysis (point
6) found that a large share of competitor bug reports are unusable for lack
of basic info. The in-app crash/report flow should auto-attach the device
profile (chip, Android version, renderer in use) and the relevant log
excerpt automatically, rather than relying on the user to describe what
happened.

## 5.8 Extensibility seams (for later, not now)

Per the brief, nothing gets built here yet — just architectural notes so
Phase 2 doesn't accidentally close the door on it:
- Keep the native rendering bridge (5.1) modular enough that a future
  overlay could composite on top of the render surface later, rather than
  assuming a single, exclusive full-screen surface.
- Keep the mod-compatibility patches (5.4) applied in a way that's specific
  to the mods they target, not hard-coded assumptions that would conflict
  with a future first-party overlay occupying similar territory.
- That's the extent of it for now — deliberately not designing the overlay
  itself.

---

**Phase 1 is now complete.** All eight items in section 5 of the brief are
written up above, each with its reasoning. Ready to move into Phase 2 (the
actual app) per the roadmap, pending nothing further from Phase 0 or 1.
