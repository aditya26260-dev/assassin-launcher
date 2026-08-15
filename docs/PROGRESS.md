# Assassin Launcher — Progress

Read this file first at the start of every session, before doing anything else.

## Current phase
Phase 0 (research) — done. Phase 1 (architecture) — done. Phase 2 (the app)
— started: project skeleton exists and is written carefully, but NOT
build-verified (no SDK/NDK/Gradle/network in this sandbox — see the Phase 2
section below for exactly what that means in practice).

## Communication note
User explicitly said not to spend a full response on side info (like the
network correction below) — fold it in briefly and keep moving on the main
work instead.

## Decision: open source (confirmed by user)
Project is open source. LICENSE file added (GPL-3.0 — the compatible choice
across both the GPL-3.0 and LGPL-3.0 material in the reference set; LGPL'd
code can always be used under plain GPL terms when combined into a GPL'd
work, not the other direction). No anti-piracy / decompile-hardening work.
Still keeping `/home/claude/competitor-reference/` separate from the actual
project and still crediting/attributing anything actually adapted rather than
quietly folding it in, since GPL-3.0 requires attribution and license
notices regardless of the project itself being open.

## Environment findings (verified, not assumed)
- Sandbox has Java 21 (OpenJDK), no Gradle, no Android SDK, no Android NDK, no
  Rust toolchain installed.
- Network: CORRECTION to an earlier note in this file. My system config
  actually says network egress is enabled with a specific allowed-domains
  list (github.com, pypi.org, npm, crates.io, ubuntu archives, etc.) — I'd
  misread this as fully disabled and only tested one out-of-list domain to
  "confirm" it. The user showed me their settings do have network egress
  and a GitHub connector enabled, which was the right challenge. Re-tested
  properly against every domain my config says should be allowed, with
  verbose output: every single one, including github.com itself, comes back
  `HTTP 403, x-deny-reason: host_not_allowed` — same as a domain picked at
  random. So whatever's supposed to be enabled per config/settings isn't
  actually working in this sandbox right now, for reasons I can't see from
  in here. Practical effect is the same as before (nothing's reachable from
  bash_tool right now), but don't state it as "no internet access" again
  flatly — it's "supposed to allow specific things, empirically doesn't
  right now," which is a config/platform issue on Anthropic's side, not a
  hard limitation of the sandbox itself. Worth the user trying again in a
  future fresh session if this matters later (that would lose everything
  in this sandbox though, so not pushing for it now).
- Practical implication unchanged for now: writing/organizing every file
  here, but compiling an actual installable APK still needs to happen
  outside this sandbox — Android Studio on a PC, or Termux on the phone.
- Disk quota in this sandbox is modest (~10GB free), and the reference zips
  are large (ZalithLauncher2 alone is 562MB unpacked). Extracting reference
  material on demand, not all at once.

## Licensing audit (verified from the actual files, not memory)
| Project | License found | Notes |
|---|---|---|
| PojavLauncher (upstream of Amethyst/Mojo) | LGPL-3.0 | per DroidBridge's own OPEN_SOURCE_NOTICES.md |
| Amethyst Launcher | LGPL-3.0 | full text in repo LICENSE, PojavLauncher-based |
| MojoLauncher | LGPL-3.0 | same lineage as Amethyst |
| FoldCraftLauncher | GPL-3.0 | |
| ZalithLauncher2 | GPL-3.0 | |
| PrismLauncher | GPL-3.0 | |
| DroidBridge Launcher | no explicit top-level LICENSE | ships an OPEN_SOURCE_NOTICES.md flagging its own PojavLauncher-derived code as an open audit item — a real example of the exact risk we're being careful about |
| Turnip driver build (uploaded) | MIT (Mesa project license, verified at docs.mesa3d.org) | clean to bundle as a binary regardless of our own license choice |
| OpenJDK builds (Amethyst/Mojo `_openjdk` zips) | GPL-2.0 + Classpath Exception (standard OpenJDK terms) | Classpath Exception explicitly allows bundling without infecting the rest of the app — clean either way |

Net effect: the driver binary and the JDK builds are safe to use regardless of
which way the open/closed decision goes. The six launcher codebases are not —
all six are copyleft. Kept physically separate in `/home/claude/competitor-reference/`
(outside this project folder) for exactly that reason: study for concepts and
gap analysis, not a source of implementation to copy from, unless we go GPL
ourselves.

## Phase 0 research findings so far
- Mojang shipped an **experimental native Vulkan renderer** in Minecraft Java
  Edition 26.2 (June 16, 2026) — real, confirmed, very recent. It's an opt-in
  toggle; OpenGL is still the default until Vulkan is proven stable, then
  OpenGL gets removed. Current stated requirement: **Vulkan 1.2 with dynamic
  rendering and push descriptors** (Minecraft Wiki says this may change).
  Runs on its own thread, separate from game logic. This is prep for Vibrant
  Visuals coming to Java Edition.
  Correction to the original brief: it's not a clean "these versions use
  OpenGL, these use Vulkan" split. Recent versions (26.2+) carry both, picked
  by a toggle. Older versions only have OpenGL.
- **VulkanMod** (third-party Fabric mod, separate from Mojang's native
  renderer) is a full renderer replacement targeting Vulkan 1.2, and it
  conflicts with Sodium/OptiFine/Canvas since they're all alternative
  renderers competing for the same job — confirms the conflict-detection
  case named in the brief.
  Interesting new fact: **Sodium itself shipped an early native Vulkan
  backend on June 16, 2026** (Sodium 0.9.x, for 26.2) — separate from
  VulkanMod, presumably targeting Mojang's own Vulkan path. This is about six
  weeks old, newer than most of what the reference launchers will have caught
  up with.
- **Turnip** (Adreno Vulkan driver) is part of Mesa, MIT-licensed, confirmed
  at the project's own docs.
- **Panfrost/PanVK** (Mali GPU driver) is genuinely early-stage as of mid-2026
  — PanVK is only Khronos-conformant on Mali-G610, non-conformant elsewhere,
  and even the newest Mali hardware support ships behind a debug flag its own
  developers named `PAN_I_WANT_A_BROKEN_VULKAN_DRIVER`. Mali fallback should
  be scoped as "best effort, real gaps," not presented as equivalent to the
  Adreno/Turnip path.

## Phase 0 additional findings (this session) — Phase 0 now essentially done
- Mapped the full translation layer landscape and resolved every license:
  GL4ES → HolyGL4ES → Krypton Wrapper (same thing as NG-GL4ES) are one MIT
  lineage, capped ceiling. MobileGlues is a separate, newer lineage (LGPL-2.1,
  confirmed at its own repo), GLES 3.2 host baseline, used by essentially
  every one of the six reference projects at this point. ANGLE is BSD-3-Clause
  (confirmed at Google's own repo — FoldCraftLauncher's "All Rights Reserved"
  credit was just wrong). Zink is MIT (Mesa).
- The actual rendering decision matrix: Zink-over-Turnip gets real OpenGL 4.5
  on Adreno (meets Sodium's stated requirement), but only works at all if
  Turnip is present and working — crashes outright otherwise. Mali tops out
  around OpenGL 3.1/3.2 through Zink even in the best case, and can't run
  pre-1.17 Minecraft through Zink at all (an upstream "will not fix" bug).
  Turnip itself is genuinely segmented by Adreno generation (6xx mature,
  7xx uneven — the uploaded 710/720/722 build specifically comes from
  less-stable patches — 8xx brand new and rougher). Full matrix in
  PHASE0_RESEARCH.md section 8.
- Real competitor bugs found via actual issue trackers/release notes across
  five of six reference projects (Amethyst, FoldCraftLauncher, ZalithLauncher2,
  MojoLauncher, DroidBridge — PrismLauncher deprioritized as desktop-focused).
  Highlights: none of the six auto-detect Vulkan capability (ZalithLauncher2
  just warns the user); MojoLauncher has a recurring, unresolved pattern of
  Android scoped-storage failures for mod files; DroidBridge's own developer
  confirmed in their release notes that they used PojavLauncher/Boardwalk
  code directly, and separately has a live bug from Mojang's 26.2 Vulkan
  rollout breaking their EGL bridge. Full detail in PHASE0_RESEARCH.md
  sections 7 and 9.
- Wrote the actual gap-analysis synthesis the brief asks for — six concrete,
  sourced things Assassin Launcher should do differently. PHASE0_RESEARCH.md
  section 10.

## Still open in Phase 0
- Mod-patch-level specifics (Create, Distant Horizons, Bobby, Simple Voice
  Chat's Android error) — deliberately deferred to when we're actually
  building the compatibility layer, doesn't change the architecture.
- Nothing else is blocking. Ready to move into Phase 1.

## Phase 2 progress: project skeleton (first pass)
Built the actual Gradle/Android project structure: settings.gradle.kts, root
and app build.gradle.kts, gradle.properties, manifest, a working Application
class, a minimal but real Compose MainActivity, the GameSessionService from
architecture 5.5 (specialUse foreground service, proper notification, starts
only on explicit user action), and a minimal native bridge (CMakeLists.txt +
one real JNI function + its Kotlin counterpart) confirming the Kotlin/native
boundary is wired correctly, ahead of the actual driver/rendering code.
Package: com.assassinlauncher.launcher. minSdk 26, compileSdk/targetSdk 36,
arm64-v8a only (matches the brief's ARM64 scope).

**Toolchain versions used, and how confident I am in each:**
- AGP 9.1.1, Gradle 9.5.1, JDK 17 minimum — directly verified against
  current Google/Gradle documentation this session, high confidence.
- Compose BOM 2026.04.01 — verified directly against Google's own Compose
  release blog, high confidence.
- core-ktx 1.15.0, activity-compose 1.9.3, lifecycle-runtime-ktx 2.8.7 —
  reasonable recent versions, not independently re-verified to the same
  precision as the above. Android Studio flags outdated dependencies on
  first open, so treat these as a starting point to confirm there, not a
  guarantee they're the exact current patch.

**What I can't verify from here, stated plainly:** I have no Android SDK,
NDK, or Gradle installed in this sandbox, and no working network access
(still true as of last check) — so none of this has actually been
compiled. Everything above is written as carefully and correctly as I can
manage, but "written" isn't "verified" for this kind of file. Also: the
actual `gradle-wrapper.jar` binary can't be created from here (it's a
compiled binary, not a text file) — only the properties file pointing to
Gradle 9.5.1 exists so far. Opening this in Android Studio should
regenerate it automatically; if not, running `gradle wrapper` once (with
any Gradle install) will fix it. First real test of any of this is
whichever environment actually builds it — want to hear what happens.

## Phase 2 progress: hardware detection system
Built the actual detection engine that architecture 5.3's whole rendering
decision tree runs on (this is also what 6.1's first-launch flow will call):
- `DeviceProfiler`/`DeviceProfile` (Kotlin): Android SDK version, GPU vendor/
  renderer string (via a throwaway 1x1 EGL pbuffer context, torn down right
  after reading it), GPU family classification (Adreno 6xx/7xx/8xx vs. Mali
  vs. other, parsed from the renderer string), and PackageManager's coarse
  Vulkan version flag.
- `vulkan_probe.cpp` (native): the deeper check Java can't do directly -
  actual Vulkan API version, dynamic rendering support, push descriptor
  support. Deliberately uses dlopen/dlsym instead of linking libvulkan at
  build time, so it degrades cleanly (`vulkanAvailable = false`) on devices
  with no Vulkan driver at all instead of crashing the native library load.

**Risk-calibration, since I keep being asked to be straight about this:**
the Gradle/manifest/Compose scaffolding from before is fairly low-risk, it's
standard, well-documented patterns. This native Vulkan probe is a real step
up in complexity, manual struct setup, pNext chaining, function-pointer
resolution, and it's exactly the kind of code that's easy to get subtly
wrong without a compiler and a real device to check against. I reviewed it
carefully myself and caught two real mistakes before they went out (missing
`<vector>`/`<string>` includes, a wrong bit-mask width on the Vulkan version
unpacking in the Kotlin side) - but "I checked it myself" is not the same
confidence level as "it compiled and ran correctly on a device." Flagging
this file specifically as the first one to look at closely if a real build
throws errors.

## Phase 2 progress: the actual render path decision logic
Two things this session:

1. **Found the real mechanism for Turnip driver swapping** — a genuine gap
   in the original research. It's **libadrenotools** (bylaws/libadrenotools,
   BSD-2-Clause), the same library Winlator/Skyline/Vita3K/Yuzu-Eden and the
   PojavLauncher family all build on. Confirmed requirement: Android 9+
   (API 28), arm64 only — straight from its own repo and source. Updated
   ARCHITECTURE.md 5.3 with this and the real constraint it adds (Turnip/
   Zink need API 28+, separate from the app's own minSdk 26). Not vendored
   yet — that's real, fragile, low-level native work (linker namespace
   manipulation) that needs an actual compile-and-run cycle, flagged as the
   next native task rather than written blind.
2. **Wrote `RenderPathSelector`** — the architecture 5.3 decision tree as
   actual, real Kotlin logic, not just documentation: takes a DeviceProfile
   plus per-instance context (Minecraft version, Vulkan toggle, the
   confirmed Mali+≤1.16.5 exception) and returns a render path plus whether
   it needs a user prompt or just a toast, matching the silent-fix-vs-prompt
   line drawn in 5.3/5.6. Pure logic, no native calls of its own, so it's
   reasoned about independently of whether the driver loading underneath it
   works yet.

**Known, honest gap**: the OpenGL-path fallback currently only goes
Zink-over-Turnip → MobileGlues. It doesn't yet drop further to Krypton
Wrapper/base GL4ES for devices below MobileGlues' GLES 3.0 floor, because
DeviceProfiler doesn't probe the device's actual GLES version yet, only the
renderer string. Noted in code, not silently missing — will close this when
GLES version probing gets added to DeviceProfiler.

## Phase 2 progress: first-launch flow wired end to end
Closed the GLES-version gap flagged last session (DeviceProfiler now reads
GL_VERSION too, RenderPathSelector properly drops to Krypton Wrapper below
MobileGlues' GLES 3.0 floor instead of stopping at MobileGlues always).

Then built architecture 6.1 for real: `FirstLaunchViewModel` runs
DeviceProfiler → RenderPathSelector as actual sequential steps with real
state, `FirstLaunchScreen` shows each step honestly (not a generic spinner)
and a real result screen with a working Continue button, `DeviceProfileStore`
(DataStore) persists the result so this only runs once. `MainActivity` now
routes: first launch unknown → brief loading gap → not done → FirstLaunchScreen
→ done → the placeholder home screen (still just a placeholder, 6.3 doesn't
exist yet - not dressed up as more than it is).

Added `androidx.datastore:datastore-preferences:1.1.1` and
`androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` to app/build.gradle.kts.

**Still open, stated plainly:** the first-launch decision currently assumes
"modern Minecraft, Vulkan on" as a placeholder context, since there's no
instance system yet to ask for the real Minecraft version/mod list — real
per-instance decisions will re-run this with actual context once instances
exist. Re-detection if a device's driver situation changes later (e.g.
after an OS update) isn't handled yet either, noted in code rather than
silently skipped.

## Phase 2 progress: instance/profile system
Made architecture 5.7 real instead of just documented:
- `GameProfile` — the actual per-instance data model (name, Minecraft
  version, loader, JVM/RAM/render-path overrides, all defaulting to "auto"
  rather than forcing every profile to carry explicit values).
- `InstanceDirectoryManager` — builds the real shared-root-plus-per-instance
  folder layout. Deliberately on the app's own external-files directory,
  not shared storage — direct response to Phase 0's finding that
  MojoLauncher's issue tracker is full of scoped-storage failures.
  App-private storage needs no storage permissions and sidesteps that
  whole bug class from the start.
- `InstanceRepository` — persisted CRUD over profiles (JSON via org.json,
  already part of Android, no new dependency for something that didn't
  need one), and bootstraps a real default "Latest Release" profile on
  first use so the home screen's play button has something to launch once
  it exists.
- `MinecraftVersions` — a deliberately simple version comparator for the
  two specific checks 5.3 needs (native Vulkan availability, the Mali+
  ≤1.16.5 exception). Explicitly not a general version parser — Minecraft's
  real version history between the old 1.21.x scheme and 26.2 isn't
  something I have confident knowledge of, flagged in its own file rather
  than guessed at with false confidence. Should eventually pull Mojang's
  real version manifest instead.

Caught a real bug reviewing my own work before it went further: the
repository's first-run bootstrap was writing a default profile to disk but
then returning the stale pre-bootstrap (empty) list to the caller, so the
very first read would have looked like zero profiles existed. Fixed.

## Phase 2 progress: home screen shell
Built architecture 6.3's layout for real: play button lower-left-center,
edit icon beside it, manage-accounts and wardrobe controls top-left,
settings top-right - matching the brief's actual positioning, not a rough
approximation. Added a minimal `Account`/`AccountRepository` (structurally
real, correctly starts signed-out since Microsoft login isn't wired up -
needs an Azure AD app registration from the user, not something to invent).

The one piece deliberately not attempted: the 3D skin viewer with touch/
mouse-driven rotation. That's a real, separate, significant piece of
graphics work on its own, not something to bolt on alongside everything
else this session without being able to verify it renders anything
sensible. Home screen shows an honest "not signed in" state in its place
for now.

Wired the Play button to something real instead of nothing or a fake
launch: it opens a screen showing the actual render path `RenderPathSelector`
picks for the real active profile's real context - the first genuine use
of that decision tree against real instance data instead of the first-launch
flow's placeholder assumption.

Buttons for the screens that don't exist yet (profile editor, account
sign-in, wardrobe, settings) show a real "not built yet" message on tap
instead of doing nothing silently - a silent no-op reads as broken, this
reads as accurate.

Caught and fixed two smaller things reviewing this before wrapping up: a
missing icon dependency (Checkroom needs material-icons-extended, not the
default core set - added it), and a duplicated/fragile Adreno-family check
that I moved into a single proper shared function instead of two versions
that could drift apart.

## Phase 2 progress: game profile editor (6.8)
Extended `GameProfile` with the two override fields 6.8 actually needs
(manual renderer override, force-system-driver toggle) and extended
`RenderPathSelector` to respect them properly - including the "launch must
not fail even if forced to system driver" guarantee, which now works by
falling through to the OpenGL path rather than returning a broken decision.
A manual override the current hardware can't actually back (e.g. forcing
Zink on a device without a working Turnip) falls through to the automatic
choice instead of being honored blindly into a crash.

Built `GameProfileEditorScreen` for real: name, loader version, RAM slider,
JVM args, the renderer dropdown (correctly noted as ignored while native
Vulkan is actually running), the driver toggle, Java runtime dropdown.
Saves through to `InstanceRepository`. Wired into MainActivity, replacing
the "not built yet" toast that was there before.

Two things named honestly rather than built or faked: custom profile icons
(needs an image picker/crop flow, its own separate piece of work) and the
loader-version field not being backed by a live version list yet (plain
text entry for now, not a dropdown of real Fabric/Forge versions - that
needs a network API integration that doesn't exist yet).

## Phase 2 progress: settings screen (6.9) and credits sync
Built `SettingsScreen` with two real sections: a device/rendering panel
showing the actual DeviceProfile data (GPU, GLES version, Vulkan version
and whether it meets Minecraft's floor, Android API level) rather than
placeholder fields, and a real credits/licenses screen. Named honestly what
6.9 describes that isn't here yet (global renderer default, cursor
customization, notification options) rather than pretending this is the
finished settings screen.

Also caught CREDITS.md had gone stale — it still listed GL4ES/MobileGlues/
Zink/ANGLE as "not yet reviewed" even though a later session actually
confirmed all of those licenses. Fixed it to match reality and to match
the new in-app credits screen, which pulls from a small Kotlin data file
(`Credits.kt`) kept in sync with CREDITS.md by hand rather than parsed at
runtime — simple enough to actually verify correct by reading it.

This closes out everything buildable without Microsoft login: first-launch
detection, home screen, launch preview, profile editor, settings.

## Next action
The remaining home-screen pieces (account sign-in, wardrobe, 3D skin
viewer) all need Microsoft OAuth, which needs the user to register an
Azure AD app and provide a client ID - genuinely can't proceed on those
specific pieces without that. Worth raising directly next rather than
guessing further. Other directions that don't need it: the mod manager
(6.4-6.6), or actually starting to vendor libadrenotools now that there's
a real project to compile it into.

## Microsoft login research (this session)
Answered the live Azure registration screen: "Personal accounts only" for
supported account types (Xbox Live's sign-in scope rejects organizational/
Entra ID accounts outright per Minecraft Wiki - not just unnecessary, would
actually fail), name "Assassin Launcher" is fine as entered, redirect URI
left blank for now since it's genuinely optional at registration time and
better decided once the actual login code exists.

**Important, sobering finding, not resolved yet**: new Azure apps need
separate Microsoft/Xbox approval to actually call the Minecraft API
(api.minecraftservices.com returns 403 without it) - confirmed current via
a real Microsoft Q&A thread from February 2026. The answer given there was
to enroll in the ID@Xbox developer program, not a quick self-service form.
Established launchers (MultiMC, Prism, presumably the others studied in
Phase 0) likely have this from years of standing access, not something a
new hobby project gets automatically or quickly. Registering the Azure app
is still worth doing regardless - it costs nothing and is needed either
way - but login may end up blocked on this approval regardless of how well
the code is built. Worth being upfront about this rather than assuming
registration alone solves it.

## Renderer research, round two (this session)
Substantial new research prompted by the user's own launcher experience.
Full detail in PHASE0_RESEARCH.md section 12. Highlights: found LTW (a real
renderer missed in the first pass, GLES-based despite an earlier scraping
artifact suggesting otherwise), confirmed no clean performance winner among
MobileGlues/LTW/Krypton Wrapper without real device testing, incorporated
the user's own real-world finding that Zink performs poorly despite its
theoretical OpenGL 4.5 capability, found vauzi's actual repo has a newer
Turnip build (v2.8) than what was originally uploaded, and re-confirmed
Minecraft's Vulkan floor is still 1.2 (not 1.3) while finding Mojang's own
structured error codes for Vulkan failures, useful for the diagnostics
system.

**This changes render logic in real ways** - Zink shouldn't be the assumed
default until validated on real hardware, LTW needs adding as an option,
Krypton Wrapper needs the version-floor axis added on top of its existing
capability-floor role. Not yet reflected in RenderPathSelector.kt - that's
the next concrete step, translating this research into the actual code.

## Next action
1. Ask the user for updated Turnip builds (v2.8 for 710/720/722, ideally a
   mainline 6xx and an 8xx build too) since I can't download them myself.
2. Update RenderPathSelector.kt to reflect this session's findings: LTW as
   an option, Zink no longer the automatic default, Krypton Wrapper's
   version-floor role added.
3. Mod manager (6.4-6.6) or libadrenotools vendoring remain open from
   before, independent of the above.

## RenderPathSelector updated to match round-two research
Zink moved to manual-override-only (real testing found it performs poorly
despite reaching real OpenGL 4.5 on paper - not defaulted to until
validated on real devices). LTW added as a real render path and manual
option. MobileGlues and LTW both gained the 1.17+ version floor on top of
their existing GLES floor. Krypton Wrapper now covers both "below the GLES
floor" and "below 1.17 entirely" as reasons to fall back to it. Updated
architecture doc 5.3 to point at this rather than describe the old
behavior. Fixed two exhaustive `when` blocks in FirstLaunchScreen.kt that
the new Ltw case would have otherwise left unhandled.

## Next action
Waiting on updated Turnip driver files from the user (v2.8 for 710/720/722,
ideally a mainline 6xx and an 8xx build). Until then: mod manager
(6.4-6.6), or vendoring libadrenotools now that there's a real project for
it to compile into.

## LTW dropped per explicit decision
Removed from RenderPath, ManualRendererOverride, and all the exhaustive
when-blocks that referenced it (FirstLaunchScreen.kt, GameProfileEditorScreen.kt).
MobileGlues confirmed at its actual latest version (v1.3.5, 11 Jul 2026) -
detail in PHASE0_RESEARCH.md section 13, including a real permission
question worth checking later (MobileGlues moved to MANAGE_EXTERNAL_STORAGE
for its own files, broader than this project's own app-private storage
design) and another confirmed 730/740-specific special case.

## Next action
Same as before: waiting on updated Turnip driver files from the user.
Otherwise open: mod manager (6.4-6.6), or vendoring libadrenotools.

## Updated Turnip driver received and placed
User supplied the actual current latest build: v3.3 (1 Aug 2026, Mesa
26.3.0, Vulkan 1.4.354), newer even than the v2.8 an earlier search had
found - vauzi's twice-monthly cadence had already moved past it. Verified
directly from the file's own meta.json rather than assumed from the
filename. Also confirms `minApi: 28` right in the driver's own metadata,
independently matching the libadrenotools requirement found earlier - good
cross-validation from a second, independent source.

Placed at `app/src/main/assets/drivers/adreno-7xx-710-720-722/` (both the
.so and its meta.json). Not wired into any loading code yet - that's still
gated on vendoring libadrenotools first, same as before. Worth designing
the driver catalog to actually read each variant's meta.json at runtime
(minApi, driverVersion) rather than hardcoding those facts a second time
in Kotlin - the file already has them.

## Azure app registration - succeeded
Real client ID now exists: 1bb23364-a504-4974-9e60-4c71dbbca67a, personal
accounts only (matches the recommendation), no client secret configured
(correct - not needed for this flow). This goes into the login code once
that's built. Not treating the client ID as a secret to hide - it's a
public-client identifier by design, same as it would be visible in the
compiled APK regardless. The tenant/directory ID shown doesn't actually
get used in the Minecraft auth flow itself (that flow uses the fixed
"consumers" endpoint, not a specific tenant ID) - worth remembering so it
doesn't get mistakenly wired in later.

## Next action
Waiting on: libadrenotools source (to vendor it, needed before the new
Turnip driver can actually be loaded) and a MobileGlues release (now the
sole OpenGL-translation choice per the earlier simplification). Both asked
for directly in this response. Krypton Wrapper and base GL4ES are lower
priority fallback-tier needs, not blocking anything yet.

## libadrenotools and MobileGlues received, plus fork research
libadrenotools source received (BSD-2-Clause confirmed directly in the
file) but it's not buildable yet: it depends on a git submodule
(liblinkernsbypass, bylaws' other repo) that a plain zip download doesn't
include - just an empty directory. Need that repo too before this compiles.

MobileGlues v1.3.5 received as an APK; extracted just the arm64-v8a
libraries we actually need (libmobileglues.so + libmobileglues_info_getter.so)
into the project, ignoring the armeabi-v7a/x86/x86_64 copies bundled
alongside them.

Researched the wider AdrenoToolsDrivers ecosystem as asked. Found something
genuinely valuable: whitebelyash (the actual A8xx driver maintainer) has
their own fork of MojoLauncher with a `feat/adrenotools` branch built
specifically to wire libadrenotools + Turnip into a Minecraft launcher -
i.e., someone has already solved the exact integration problem this
project needs to solve. Worth getting a copy of that branch as a reference
before writing this from scratch. Also confirmed AdrenoToolsDrivers' own
7xx coverage only explicitly names 710/720, not 722 - vauzi's dedicated
repo has broader coverage for these three specific chips than the more
general-purpose repo does. One more repo doing similar work found
(StevenMXZ/Adreno-Tools-Drivers) - also extracts official Qualcomm
binaries from a retail device (Quest Ray-Ban Display) as one of its
options, which is a different, murkier licensing situation than the
open-source Mesa builds used so far - not pursuing that path.

## Next action
Waiting on: liblinkernsbypass source (bylaws/liblinkernsbypass), and
optionally the whitebelyash/MojoLauncher feat/adrenotools branch as a
reference implementation. Krypton Wrapper / base GL4ES still open at lower
priority.

## libadrenotools actually vendored and wired into the build
Assembled the complete source (libadrenotools + its liblinkernsbypass
submodule, both BSD-2-Clause) and placed it at
`app/src/main/cpp/third_party/libadrenotools/`. Added it to
CMakeLists.txt via add_subdirectory and linked the app's native target
against it - a real dependency, not just referenced in docs anymore.

**Found and fixed a real, verified bug reading its own header docs**:
`useLegacyPackaging` in app/build.gradle.kts was set to `false`, but
libadrenotools' driver.h is explicit that it must be `true`, or all it
won't be able to correctly resolve where its own driver hook lives. Fixed.
Independently cross-checked against the real MojoLauncher adrenotools
branch supplied - its build.gradle sets exactly the same thing, confirming
this wasn't a misreading.

Studied that reference implementation properly (not copying it, since it's
LGPL-3.0-lineage - reading it to understand the real integration pattern
correctly). Confirmed several things directly: custom drivers gated at API
28+ and Adreno-only, matching what's already in DeviceProfile; driver
packages stored in internal app storage specifically because external/
shared storage is typically mounted noexec, so a bundled .so literally
can't be executed from there regardless of permissions; the meta.json
schema this project already uses independently matches theirs.

Wrote the actual integration code:
- `adreno_driver_bridge.cpp` (native): wraps `adrenotools_open_libvulkan`
  precisely per its documented contract. Deliberately doesn't call dlclose
  on success - the header doesn't document whether that's safe with
  respect to installed hooks, and this function only runs occasionally
  (device detection, settings changes), so a small one-time leak is the
  safer tradeoff over an unverified assumption.
- `TurnipDriverManager` (Kotlin): reads each bundled driver's meta.json,
  extracts its library from assets to the internal-storage path
  libadrenotools requires, and runs a real load-attempt check - not a
  guess based on GPU family alone anymore.

**Not done yet, stated plainly**: RenderPathSelector's `turnipBuildAvailable`
parameter still isn't wired to call `TurnipDriverManager.tryLoad()` for a
real answer - it's still inferred from GPU family the way it always was.
That wiring is the clear next step, not done in the same pass as everything
above.

## Next action
Wire RenderPathSelector to actually call TurnipDriverManager for a real
"is Turnip usable here" answer instead of the GPU-family inference it
currently uses. After that: mod manager, or MobileGlues' actual loading
code (parallel to what Turnip just got).

## RenderPathSelector now gets a real Turnip answer, not an inference
DeviceProfiler now extracts the exact Adreno model number (710, 730, 840,
etc.), not just the family bucket, and actually calls TurnipDriverManager
to check whether a bundled variant claims to cover that exact chip, then
attempts a real load through libadrenotools - `turnipBuildAvailable` on
DeviceProfile is now a genuine result, not GPU-family-based guessing.

Deliberately matching by exact model against our own asset-directory
naming convention (e.g. "adreno-7xx-710-720-722" parses to [710, 720, 722])
rather than parsing the upstream meta.json's free-text "name" field, which
isn't guaranteed to follow a consistent format across different driver
sources.

Updated DeviceProfileStore to persist/restore the two new fields
(adrenoModel, turnipBuildAvailable) and simplified LaunchPreviewScreen and
MainActivity to read turnipBuildAvailable straight from the device profile
instead of each caller inferring it separately - one less place this could
drift out of sync with itself.

## Next action
Mod manager (6.4-6.6), or building MobileGlues' actual loading path
(parallel to what Turnip just got, though MobileGlues uses a different
mechanism entirely - not libadrenotools, it's its own GLES-translation
library, likely a more standard dlopen against our own rendering bridge
once that exists).

## Mods quick panel (6.4) built - and an honest MobileGlues research dead-end
Before this: checked whether MobileGlues' precise loading mechanism could
be written with the same confidence as libadrenotools. It can't, not yet -
no clean developer-facing integration docs turned up, the MojoLauncher
adrenotools branch supplied doesn't include MobileGlues code at all, and a
quick check of FoldCraftLauncher's file names didn't surface anything
either. Not writing speculative loading code without real grounding -
pivoted to the mod manager instead, which doesn't have this problem.

Built for real:
- `ModScanner` - scans an instance's mods folder, reads real Fabric mod
  metadata (name/description/version) straight from fabric.mod.json inside
  the jar where present. Forge/NeoForge use a TOML manifest this project
  doesn't have a parser for yet, so those fall back to a cleaned-up
  filename rather than fabricated metadata - stated in the code, not
  silently guessed at.
- Enable/disable via renaming to `.disabled`, per the brief's own
  instruction, actually wired to real files now.
- `ModsQuickPanel` (6.4) - real installed mods list, added to the right
  side of the home screen. Manage Mods / Update all / per-mod refresh all
  show an honest "not built yet" message, since those need the Modrinth/
  CurseForge API integration that doesn't exist yet - only enable/disable
  is fully real right now.

## Next action
The full mod manager (6.5-6.6) is the natural next piece - Modrinth's API
is public and doesn't need a key, CurseForge does need one, worth deciding
whether to start with Modrinth alone or ask for a CurseForge key upfront.
MobileGlues integration remains open, blocked on finding real grounding
for its loading mechanism (possibly worth extracting a full launcher
source like FCL or Zalith 2 to search properly if that becomes a priority).

## MobileGlues loading mechanism found for real, after checking the right project
Correction to the previous session's dead-end: checked Amethyst's actual
source (which I already had, and which my own earlier Phase 0 research had
already flagged as the MobileGlues-using project) instead of MojoLauncher
(which doesn't use it) or a filename-only check of FCL. Found the real
mechanism directly in Amethyst's JREUtils.java and egl_loader.c:

MobileGlues loading is genuinely simpler than Turnip's - no libadrenotools-
style linker namespace bypass needed at all. It's a standard dlopen
substituting for the app's own EGL library (their code tries the custom
path first, falls back to the real system libEGL.so if it fails). Makes
sense in hindsight: EGL/GL loading is controlled by the app's own native
code calling eglGetDisplay etc., unlike Vulkan where Android's system
loader is more tightly integrated - that's specifically why Turnip needed
the fancier mechanism and this doesn't.

Implemented for real: `egl_loader_bridge.cpp` (native dlopen wrapper,
following the confirmed pattern) and `MobileGluesManager` (Kotlin,
extracts the bundled library to internal storage - same noexec
constraint applies to any dlopen target, not just Vulkan drivers - and
runs a real load check). Wired into DeviceProfile as `mobileGluesLoadable`,
and `meetsMobileGluesFloor()` now requires both the real load success and
the GLES version floor, not just the version check alone like before.

Also picked up real, concrete extra detail worth keeping: Amethyst's own
fallback chain goes MobileGlues -> Krypton Wrapper on load failure,
matching this project's own tier ordering independently; a Turnip-specific
environment variable fix for Samsung OneUI rendering issues
(`FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1`); and the Zink GL version
override trick (`MESA_GL_VERSION_OVERRIDE=4.6COMPAT`) their own developer
comment calls "sketchy but fixes a lot of things" - worth remembering if
Zink ever gets validated enough to use for real.

**Still not wired**: the MG_DIR_PATH-equivalent environment variable
MobileGlues needs at actual runtime isn't set anywhere yet - that's part
of the real JVM-launching pipeline (spawning the game process with a
custom environment), which doesn't exist in this project yet. Resolved
the path it should point to (`MobileGluesManager.dataDir`), not wired to
an actual launch since there isn't one.

## Next action
Full mod manager (6.5-6.6), or start on the actual JVM/game launch
pipeline now that two real renderer loading paths exist and need
somewhere to actually plug into.

## Full mod manager (6.5) - Modrinth half built
Added OkHttp (Apache 2.0, standard, well-established) since a real HTTP
client was needed. Wrote:
- `ModrinthApiClient` - real requests against Modrinth's documented v2 API
  (search, get versions), correct as far as that documentation goes but
  genuinely untested against a live call, no working network in this
  sandbox to verify with. Worth a real test as soon as this runs somewhere
  with internet.
- `ModManagerViewModel` - search + install orchestration, including
  actually downloading the matching file and placing it in the profile's
  real mods folder.
- `ModManagerScreen` (6.5) - installed mods on the left, search/install on
  the right, profile context shown, and vanilla/OptiFine profiles
  correctly show no installer content since Modrinth doesn't host mods for
  either loader.

Caught and fixed one real bug before it went further: used a made-up
`getLocalContext()` function that doesn't exist, needed the real
`LocalContext.current` API instead.

CurseForge half (6.5 also asks for it, merged with Modrinth, duplicates
removed) not started - needs an API key that doesn't exist yet.

## Next action
Ask about a CurseForge API key, or continue with 6.6 (the mod detail
modal) using Modrinth alone for now. Otherwise: the actual JVM/game launch
pipeline remains the big open piece everything else needs to eventually
connect to.

## Mod detail modal (6.6) built
Real modal, not a placeholder: tapping a mod card's icon/name/description
opens it, fetches the actual full description from Modrinth's project
endpoint (search results only carry the short one), and shows a real
version/loader dropdown built from actual getVersions() data. Install
button installs the picked version, or the auto-matched latest if nothing
was picked - matches 6.6's spec directly rather than approximating it.

Background blur applied to the actual screen content behind the modal
(Compose's Modifier.blur, real, not a scrim standing in for it), which
needed restructuring the screen into a Box so the modal renders in the
same composition rather than a separate Dialog window that couldn't blur
content it doesn't own.

Split `ModManagerViewModel.install()` into that (auto-match latest) and a
new `installVersion()` (specific version, used by the modal) - the two
real, distinct flows the brief actually describes rather than one function
awkwardly serving both.

Did a manual brace/paren balance check on the rewritten files given how
much structural rework this needed - not a substitute for a real compile,
but catches the kind of gross mismatch a big rewrite risks.

## Next action
6.7 (resource packs, shaders, servers - same installed/installer pattern,
scoped per profile) is the natural next piece using what's already built.
CurseForge integration and the actual JVM launch pipeline remain open,
waiting on a key and a larger dedicated push respectively.

## CurseForge dropped - Modrinth only, by explicit decision
User's call: most CurseForge mods are on Modrinth anyway, the developer
approval process is a long wait, and CurseForge's API has a reputation for
being slower. Mod manager (6.5-6.6) stays Modrinth-only going forward, not
a temporary state waiting on a key anymore.

## 6.7 built - resource packs, shaders, servers
Generalized the mod manager's Modrinth client to take a project type
instead of hardcoding "mod", so resource packs and shaders reuse it
directly rather than duplicating a client. Built `ContentManagerScreen`
(shared by both, parameterized) with the same installed/installer pattern
6.5-6.6 established - simpler than mods in one real way, since neither
resource packs nor shaders are loader-specific or use enable/disable-via-
rename the way mods do.

Servers: real add/edit/remove UI, scoped per profile. One honest, stated
gap: stored in this project's own JSON for now, not Minecraft's actual
servers.dat format (gzipped NBT binary) - writing real NBT is its own
separate task, not something to fake with a similarly-named but wrong
file. Entries added here won't show up in Minecraft's own multiplayer
list until that conversion step gets built.

Wired all three as reachable from the mod manager screen, the natural hub
for this group, rather than leaving them built but unreachable.

That's all of 6.4-6.7 done for the Modrinth-only direction. Did the same
brace/paren balance check across every file touched this session - all
clean.

## Next action
6.8-6.11 (profile editor, global settings, in-game overlay, input
handling) already partially exist (6.8, 6.9 built earlier) - 6.10 and
6.11 remain. Otherwise the JVM/game launch pipeline is the largest
still-open piece everything eventually needs to connect to.

## Server manager corrected - real NBT now, not a placeholder
Fair pushback from the user on the earlier "separate task" framing.
Checked how DroidBridge and Zalith 2 actually handle this instead of
repeating the claim. DroidBridge turned out not to have its own custom
server UI at all - it just backs up/restores whatever Minecraft itself
writes. Zalith 2 does have real, dedicated server management, and it uses
an existing library (OpenNBT, Steveice10) rather than a from-scratch NBT
parser - confirmed directly in their AllServers.kt, which even cites the
same Minecraft Wiki page for the format.

Rewrote `ServerRepository` to actually read and write real servers.dat:
OpenNBT for the NBT itself, the same safe write pattern Zalith 2 uses
(write to a temp file, back up the existing file to servers.dat_old, then
swap), and fields this project doesn't have UI for yet (icon,
acceptTextures) are preserved on edits rather than dropped. Points at
`<instance>/servers.dat` - the same path Minecraft itself will read once
the launch pipeline points the game's working directory at that instance
folder, so this will actually be in sync once that connection exists, not
a parallel format that never talks to the real game.

Real correction, not just a bigger claim: I was wrong to treat this as too
large to attempt without checking first, and should have looked at the
actual reference projects before making that call the first time.

## Next action
That closes the real gap in 6.7. In-game overlay (6.10) and input handling
(6.11) remain from the home-screen list, or the JVM/game launch pipeline,
which is what would actually make this server sync (and everything else
built so far) matter in practice.

## Krypton Wrapper actually integrated, found in an upload already in hand
Was about to start on the JVM launch pipeline and went looking for actual
JDK binaries in the Amethyst/MojoLauncher uploads first (their filenames
said "_openjdk" but turned out to be full launcher sources, not JDK
bundles - worth actually checking rather than assuming that gap was still
open). No JDK found, but the file listing turned up something real and
useful instead: Amethyst bundles prebuilt AARs for every renderer,
including krypton_wrapper-release.aar - already sitting in an upload from
the very start of this conversation, not something that needed asking for.

Extracted the arm64-v8a library and its bundled MIT license, wired it in
exactly like MobileGlues (same confirmed dlopen mechanism, same lineage).
RenderPathSelector's Krypton Wrapper branches now check a real load result
instead of assuming it works, falling through to base GL4ES if it doesn't.

Also spotted other bundled AARs worth knowing about for later: an ANGLE
AAR (kept despite being phased out on Android per earlier research - worth
checking why before using it), an LTW AAR (dropped per explicit decision,
noting it exists if that ever gets revisited), and a Kopper/Zink AAR
(relevant if Zink ever gets validated enough to move off manual-override-
only).

## Real gap confirmed, not assumed: no bundled JDK anywhere in hand
Checked properly instead of continuing to treat this as unknown. Neither
Amethyst's nor MojoLauncher's uploads contain an actual JDK/JRE binary
distribution despite what their filenames suggested - searched for tar.gz
archives, java binaries, anything JDK-sized, found nothing. This is a real,
confirmed blocker for the JVM launch pipeline specifically, not something
to keep guessing about. Needed: an actual Android-ARM64 OpenJDK build
(FCL's own feature list, from Phase 0, named bundling Java 8/17/21/25 -
any of those, ideally starting with whichever matches Minecraft 26.2's
actual requirement, confirmed as Java 25 in Phase 0's Vulkan research).

## Next action
Ask the user for a real JDK build before attempting the launch pipeline
itself - guessing at this rather than confirming it exists would repeat
the same mistake as the MobileGlues/servers.dat moments. In the meantime:
6.10 (in-game overlay) or 6.11 (input handling) are both still open and
don't have this blocker.

## JDK gap - now fully verified, with the real reason and the real source
Did a full extraction of Amethyst's entire repo this time (812 files, every
nested archive enumerated by name and size), not just a filename-pattern
search - confirms with real confidence there's no JDK/JRE binary anywhere
in that specific zip. Then checked why, instead of just asserting it:
PojavLauncher's own documentation confirms the JRE is never part of the
launcher's own source repo at all - it's a separate artifact, built by a
dedicated project (PojavLauncherTeam/android-openjdk-build-multiarch) and
either downloaded by the app at runtime or fetched separately by whoever's
building from source. That's true across the whole PojavLauncher-family
lineage Amethyst/MojoLauncher/Zalith 2 belong to, not something specific
to what got uploaded here.

Real, concrete source to get one from: PojavLauncherTeam/android-openjdk-
build-multiarch. One honest flag: their own documented list covers Java
8/17/21 - Java 25 (which Minecraft 26.2 actually needs, per Phase 0) isn't
confirmed present there yet. Whatever gets pulled from that source may
still need supplementing for the newest Minecraft versions specifically.

## Cursor customization (6.11) built
Real size/color/sensitivity settings, persisted, actually wired into the
settings screen rather than left as the placeholder note that was there
before. Caught a real bug reviewing it myself before it went further -
used Box in the new color swatches without importing it. Custom PNG
cursor import still needs an image picker, stated plainly rather than
built halfway.

## Next action
6.10 (in-game overlay) remains, though it needs a running game to overlay
onto, which doesn't exist yet - may be worth deferring until closer to the
actual launch pipeline. Otherwise touch/controller input handling (rest of
6.11) is real, substantial native work tied to actually running the game.

## Real Microsoft sign-in built (6.2 / 6.3's account piece)
Given there's a real client ID now and the login mechanics themselves are
useful regardless of whether Minecraft API access ends up approved, built
the actual sign-in flow rather than leaving it blocked on that uncertainty:

- `MinecraftAuthClient` - the real Microsoft -> Xbox Live -> XSTS ->
  Minecraft token chain every Minecraft launcher implements against.
  Same untested-against-a-live-call caveat as the Modrinth client - no
  network here to verify it with, worth a real test as soon as it can run
  somewhere with internet.
- `MicrosoftSignInScreen` - a real WebView loading the actual Microsoft
  login page, intercepting the redirect to pull out the auth code and run
  the token exchange.
- `AccountRepository` rewritten to actually persist real profile data
  (username, UUID, skin URL - none of it sensitive).

**Deliberate, stated gap**: the Microsoft refresh token isn't persisted
anywhere. That's a real credential, storing it in plain JSON/preferences
the way everything else here is stored would be an actual security gap,
not a shortcut worth taking. Signing in again on each app restart is the
honest tradeoff until proper encrypted storage gets added - not silently
storing it insecurely to make the feature look more finished than it is.

**Operational step still needed**: the redirect URI
(`https://login.microsoftonline.com/common/oauth2/nativeclient`) isn't
registered in the Azure app yet - needs adding under Authentication ->
Add a platform -> Web with that exact URI, or the real sign-in flow won't
actually complete. Worth doing before trying this for real.

Wired "Manage accounts" to the real screen, replacing the toast that was
there. Wardrobe still isn't built (needs a signed-in account's skin to
actually edit, makes more sense once sign-in itself is confirmed working).

## Next action
Ask the user to add the redirect URI in Azure, since that's needed before
this can be tested at all. Otherwise: wardrobe (6.3), or back to the JVM
launch pipeline if a JDK build turns up.

## Real JVMs in hand - major unblock for the launch pipeline
User supplied actual working Java 8/17/21/25 builds for Android ARM64.
Verified all four directly (extracted with system tar/xz in this sandbox,
confirmed real bin/java, confirmed real version strings in each release
file - jre25 is genuinely 25.0.3, built 2026-04-21). Also traced exactly
where AngelAuraMC's build-script upload (angelauramc-openjdk-build-
buildjre8) fits in: that's the *builder*, not the built artifact - its own
CI publishes to GitHub Releases, and Amethyst's real, live code downloads
from exactly that same release URL pattern at runtime. Confirmed directly
in their NewJREUtil.java, not assumed.

Implemented `JvmRuntimeManager` for real: downloads from that same
confirmed URL pattern, extracts with Apache Commons Compress + its XZ
codec (the same library pairing Amethyst's own working build.gradle uses,
not guessed at), and explicitly restores executable permissions after
extraction - tar keeps them in its metadata but doesn't apply them
automatically, verified directly against a real supplied archive
(`bin/java` really is `rwxr-xr-x` in there).

Architecture decision updated to match: download-on-demand and cache
locally, not bundle all four JVMs in the APK (would mean 400-600MB of
baseline size for runtimes most installs won't need at once).

## Next action
Was checking PrismLauncher's LaunchProfile.cpp for real reference on
Mojang's version-JSON argument rules before this JDK detour - worth
picking back up. That plus the version manifest client (fetching an
actual version's library list/main class/arguments) is what turns a
provisioned JVM into an actual launch. This is the real next piece of the
launch pipeline now that a JVM to launch with actually exists.

## Version rules engine + Mojang manifest client built
Checked PrismLauncher's actual Rule.cpp before writing this - confirmed
the real semantics (each rule either doesn't apply and is skipped, or
applies and sets the result; last applicable rule in the list wins; no
rules means allowed by default) rather than reconstructing it from general
memory of the format. `VersionRuleEvaluator` implements exactly that.

`MinecraftVersionClient` fetches Mojang's real version manifest and
per-version JSON (main class, required Java version, asset index,
rule-filtered library list). Same untested-against-a-live-call caveat as
every other network client this session.

## Next action
Classpath construction (joining the filtered library list with the right
paths) and actual process spawning are what's left to turn this into a
real launch. Given how much groundwork exists now (JVM provisioning,
version parsing, rendering, accounts), this is genuinely close to a first
real end-to-end attempt.

## Real launch pipeline built - first end-to-end attempt exists now

Starting point: `LibraryDownloader.kt` turned out to already exist with a
working `buildClasspath()`, contradicting the "classpath construction
hasn't started" framing above - it just wasn't referenced anywhere. Real
gap was narrower than stated: the low-level piece existed, the
orchestration tying it to everything else didn't.

**Caught and fixed a real bug**: `MinecraftAuthClient.signInWithAuthorizationCode()`
fetched the actual Minecraft access token, used it once for the profile
lookup, then discarded it - the token a launch's `--accessToken` argument
actually needs. Now returns a `SignInResult` carrying a `MinecraftSession`
(access token, expiry, XUID), threaded through `AccountRepository` as
in-memory-only state keyed by account id - same "don't persist a real
credential to plain JSON" reasoning already established for the Microsoft
refresh token, just extended to cover this token too. A launch attempted
after an app restart now fails with a clear "session expired, sign in
again" rather than silently using a stale or missing token.

**`VersionRuleEvaluator` extended** to evaluate the `features` condition
(e.g. `is_demo_user`, `has_custom_resolution`), on top of the os-based
logic it already had right. This is what the modern `arguments.game`/
`arguments.jvm` rule schema needs beyond what library filtering ever
required. Notably more complete than Amethyst's own real shipped code,
which only handles plain-string game arguments and leaves rule-gated ones
as an acknowledged TODO.

**`MinecraftVersionClient` extended** to parse `arguments.game`,
`arguments.jvm` (modern, 1.13+), the legacy flat `minecraftArguments`
string (pre-1.13), and the `assets` short-name field - none of which it
read before. New `GameArgumentBuilder` resolves these templates against
real substitution values (account, version, directories, classpath) and
the rule engine above, `${token}` by `${token}`.

**The JVM-embedding mechanism is confirmed real, not assumed.** Checked
Amethyst's actual `JREUtils.java` and `jre_launcher.c`: Minecraft doesn't
run as a spawned subprocess, it's a JVM embedded in-process via
`dlopen("libjli.so")` + the internal `JLI_Launch()` entry point - the same
one the real `java` binary itself uses. Confirmed the exact dlopen
dependency order (libjli -> libjvm -> libverify -> libjava -> libnet ->
libnio -> libawt -> libawt_headless -> libfreetype -> libfontmanager, then
a sweep for whatever else a build ships) against a **real downloaded JDK
17 build from our own AngelAuraMC source** - not Amethyst's, ours,
pulled and extracted directly in this sandbox (`release-assets.githubusercontent.com`
is in the allowed network list). That also settled a real layout question:
this build is flat under `lib/` with no `lib/aarch64` subdirectory, unlike
what Amethyst's more defensive multi-arch-oriented code checks for - single-
arch tarball, single-arch code, confirmed rather than copied.

Built `jvm_launcher_bridge.cpp` (the actual `JLI_Launch` call) and wired it
into `NativeBridge`. Version strings passed to it are computed per-runtime
now (`"17.0-internal"`/`"17"` etc.) rather than Amethyst's hardcoded
`"1.8.0-internal"`, which is only correct for their one bundled Java 8 -
would have been wrong for three of this project's four bundled majors.

**Real architectural fact worth its own line**: under normal
circumstances, `launchEmbeddedJvm` does not return. The real `java`
binary's own launch mechanism exits the OS process directly when the
program it ran finishes - JLI_Launch is that same code path, and every
PojavLauncher-lineage project's own code treats a return from it as "the
process needs to fully die now" (Amethyst's own `Tools.fullyExit()`,
called unconditionally right after). In practice: **closing Minecraft
closes this app's process too.** Documented in the native code, in
`NativeBridge`, and in `GameSessionService` - not something to design
around silently. "Return to the launcher after the game closes" needs a
real answer later (a restart trampoline, most likely) - noted as a next
consideration, not solved here.

**`GameLaunchOrchestrator` built** - the real thing tying all of the above
together: resolves account + session, fetches the version manifest,
provisions the right JVM, downloads libraries (Modrinth-style "only
what's missing"), builds the classpath, prepares the render path's
environment, builds the argument lists, dlopens the JDK in order, calls
the native launch. Reports progress through a `Flow<LaunchOutcome>` with
no success case by design - see the architectural fact above for why a
success doesn't come back through Kotlin to report.

**`GameSessionService` and `LaunchPreviewScreen` are wired to this for
real now** - the "Not launching yet" placeholder is gone. Tapping Play
starts the service with the profile id, which resolves the profile and
cached device profile, decides the render path, and runs the real
pipeline, reporting live stage progress or a failure back to the screen
via a same-process `StateFlow` (no need for broadcast/IPC - see the
process-death fact above for why "same process" won't stay true forever).

**LWJGL for Android**: Mojang's manifest has no Android LWJGL natives at
all - confirmed every reference launcher vendors a custom-compiled build
rather than downloading one. Amethyst's own repo bundles working prebuilt
Java-side jars for exactly this (LWJGL 3.3.3 and 3.4.1) - already in an
upload already in hand, no new ask needed, same pattern as the Krypton
Wrapper AAR before it. Vendored the 3.3.3 set.

**Self-caught mistake worth stating plainly**: first attempt bundled these
as a Gradle `fileTree` dependency, which would have merged LWJGL's classes
into this app's own DEX - meaningless, since the embedded game JVM has its
own completely separate classpath (`-cp`, standard `java.lang.ClassLoader`),
unrelated to Android's DEX/ART classpath our own Kotlin code runs on.
Caught before it shipped. Fixed by moving them to
`app/src/main/assets/lwjgl/lwjgl-android-3.3.3/`, extracted to internal
storage at launch time by a new `AndroidLwjglProvider` - the exact same
asset-then-extract pattern `MobileGluesManager`/`TurnipDriverManager`/
`KryptonWrapperManager` already use, checked directly against
`MobileGluesManager.kt` before writing this rather than re-guessing the
convention. `AndroidLwjglProvider` also strips every LWJGL-group library
(both generations - `org.lwjgl:` and the older `org.lwjgl.lwjgl:`) out of
whatever the generic downloader would otherwise fetch, and splices the
vendored jars in instead.

That filtering step matters beyond just "use our own build": Mojang's
`natives-linux` LWJGL entries would otherwise pass `VersionRuleEvaluator`
today, since this project presents itself as `"linux"` at the JVM level -
but those jars carry glibc x86_64/aarch64 binaries, not Bionic ARM64, and
would fail even if downloaded. Real bug avoided, not just a style
preference for using our own build.

**Real, open gap, not glossed over**: the LWJGL jars are in, but the
native `.so` files they call into (the GLFW replacement, OpenAL) aren't.
Amethyst's own build gets these from SDL2 + sdl2-compat as git submodules,
which a plain zip export of their repo doesn't include - confirmed by
checking `.gitmodules` and finding no compiled `.so` anywhere in the
actual tree. `GameLaunchOrchestrator`'s renderer loading and JDK dlopen
sequence are written to be correct the moment these exist (an `if
(file.exists())` away from working), and fail with a clear message rather
than silently without them. Was mid-way through checking whether
MojoLauncher/FoldCraftLauncher/ZalithLauncher2 vendor this differently
(precompiled rather than submodule) when the uploads mount for this
session went empty and stayed empty - what's already extracted
(Amethyst, PrismLauncher) survived on local disk and everything above is
grounded in that, but this specific cross-check is unfinished. Worth
another look if those get reattached.

**Other real, stated gaps, none silently assumed to work**:
- No asset (textures/sounds/lang) downloading yet - a real, separate
  piece of work, not something "classpath and process spawning" was ever
  going to cover. Minecraft will start with an empty assets directory.
- Modloader launches (Fabric/Forge/NeoForge/Quilt/OptiFine) aren't
  handled - `GameLaunchOrchestrator` checks for `ModLoader.VANILLA` and
  fails clearly otherwise, rather than attempting a broken modded launch.
  Real, separate work: modloader version JSONs *inherit from* a vanilla
  version and need merging (libraries concatenated, main class replaced),
  which nothing here does yet.
- The Android Surface -> EGL native window bridge (`setupBridgeWindow` in
  Amethyst's terms) doesn't exist yet either - architecture doc 5.1's own
  listed item, a distinct piece of work from getting the JVM to embed and
  run. Even with the native `.so` gap above closed, there's currently no
  path for a chosen renderer to get an actual window handle to draw into.
- Every network call in this session (Mojang's manifest, library
  downloads, the JVM download-and-extract-and-verify) is genuinely
  untested against this project's own real flow end-to-end in one run -
  same standing caveat as every other network client here, real
  individually-verified pieces, not yet run start-to-finish together.

## Next action
Three real, independent pieces, any of which could come next depending on
what's easiest to make progress on: (1) source the LWJGL native `.so`
files - worth finishing the MojoLauncher/FCL/Zalith2 cross-check once the
upload mount is reliable again, since a precompiled option would avoid
building SDL2 from source; (2) the Surface/EGL native window bridge, which
unblocks actually seeing anything render once (1) is solved; (3) asset
downloading, which is independent of both and could happen in parallel.
None of these block the other two.

## Found the real source for both the LWJGL-native and EGL-bridge gaps

Aditya re-uploaded Amethyst, MojoLauncher, and ZalithLauncher2 after the
upload-mount issue from the previous session. MojoLauncher turned out to
have the same gap as Amethyst - its own `.gitmodules` points `glfw` at a
`MojoLauncherTeam/glfw` submodule, not included in a plain zip either.

ZalithLauncher2 (GPL-3.0, already the project's primary reference for the
server/NBT work) is the one that actually has it all: the complete native
bridge source lives at `ZalithLauncher/src/main/jni/`, built via
`ndkBuild`/`Android.mk` rather than CMake - which is exactly why the first
pass's searches for `CMakeLists.txt` missed it entirely. Real lesson, not
just a lucky find: absence of a build file in one convention isn't
absence of the code.

What's actually there, confirmed by reading it directly rather than
inferring from filenames:
- `egl_bridge.c` - this **is** the Surface/EGL bridge, the second gap
  flagged as separate future work last session.
  `Java_..._ZLBridge_setupBridgeWindow(JNIEnv*, jclass, jobject surface)`
  does exactly what was predicted: `ANativeWindow_fromSurface(env,
  surface)`, valid because everything runs in one process (see the
  process-death fact from last session - same reason this works at all).
- `ctxbridges/` (`gl_bridge.c`, `osm_bridge.c`, `egl_loader.c`,
  `osmesa_loader.c`, `virgl_bridge.c`) - a real "bridge table"
  abstraction dispatching to whichever backend `POJAV_RENDERER` selects
  at runtime. More backends than this project currently has managers
  for (adds Panfrost, VirGL, plain Gallium/Freedreno paths alongside
  what MobileGlues/Turnip/Krypton already cover).
- `jre_launcher.c` - their own JLI_Launch call, same mechanism already
  built and verified last session. Worth a real side-by-side diff before
  building on top of anything else here, not just noted and moved past.
- `lwjgl_dlopen_hook.c` - traces directly to
  `github.com/PojavLauncherTeam/lwjgl3`, `3.3.1`, confirmed by a comment
  in their own source pointing at the exact file. Intercepts LWJGL's own
  internal `ndlopen()` to redirect `libvulkan.so` loads to this project's
  own driver choice instead of the system default - a real mechanism
  neither Amethyst's simpler dlopen sequence nor last session's build
  covers yet.
- `LWJGL/` (a whole separate module) - not the stock 3.3.3 jars vendored
  last session. A genuinely patched fork: `GLFW.java` alone is 1557
  lines, still carrying LWJGL's own original copyright header (real
  upstream source, patched - not written from scratch), importing
  ZalithLauncher2's own `CursorRegistry` class directly. Deeply
  integrated with their app, not a clean drop-in.

**Traced the real upstream lineage** rather than assuming ZalithLauncher2
is the original source: `PojavLauncherTeam/lwjgl3` (BSD-3-Clause) and
`PojavLauncherTeam/PojavLauncher` (LGPL-3.0) on GitHub are where this
actually comes from. PojavLauncherTeam itself is archived/discontinued -
Amethyst-Android is its named iOS-side successor, which is almost
certainly where last session's already-vendored jars were themselves
built from, just without the source attached. The archived repos should
still be fully clonable. This matters because it's a real second option
alongside ZalithLauncher2's copy: the upstream fork is licensed more
permissively and isn't entangled with another launcher's own app-specific
Kotlin classes, which could make it the cleaner adaptation source even
though ZalithLauncher2's copy is more convenient (already in hand, and
GPL-3.0 is fine too - the LGPL-3.0-into-GPL-3.0 reasoning already
established in this project's licensing decisions covers either source).

**One dead-end ruled out, not silently skipped**: `Terracotta`, a module
in the same ZalithLauncher2 repo with a same-named `.so`, looked
promising at first (right kind of name, right kind of native surface
area) but is unrelated - it's EasyTier-based P2P VPN plumbing for LAN-
over-internet multiplayer, AGPL-3.0 licensed. Worth recording specifically
*because* it's a license this project hasn't dealt with before and the
instinct to reach for it was wrong - flagging the near-miss so a future
session doesn't repeat the same reach without rechecking what it actually
is.

**Real, open architectural decision, not resolved unilaterally**: how
much of this to adopt. Full options, roughly in order of effort:
(1) adapt ZalithLauncher2's fork directly, fastest to get to something
working but means porting pieces of their own Kotlin integration too;
(2) go to the cleaner upstream PojavLauncherTeam sources and adapt from
there, more work up front, less entanglement; (3) extract only the
minimum needed (the Surface/EGL bridge + the dlopen hook) and keep this
project's existing simpler TurnipDriverManager/MobileGluesManager/
KryptonWrapperManager split rather than adopting the fuller
POJAV_RENDERER/bridge-table architecture wholesale. Nothing built against
any of these yet - genuinely waiting on which direction to take before
sinking real time into one.

## Next action
Aditya's call on the three-option split above. Once decided: the chosen
LWJGL fork gets vendored/adapted, the EGL/Surface bridge gets built
following `egl_bridge.c`'s confirmed real mechanism, and
`jre_launcher.c` gets diffed against what's already built. Asset
downloading remains untouched and independent of all of this - still a
real, separate piece of work whenever it's picked up.

## First real CI run - and the first real, compiler-confirmed bug

Aditya set up the repo and pushed via the Termux/`gh` steps in
docs/BUILDING.md and sent back the failure logs directly. First real
external confirmation this project has ever gotten, and it found
something in one message that a lot more manual review wouldn't have:
the workflow itself was broken, not the app code.

**Root cause, read directly from the log, not guessed**: `sdkmanager:
command not found`, exit 127, at the "Install NDK" step. GitHub's
`ubuntu-latest` runners do ship a preinstalled Android SDK, but
`sdkmanager` isn't on `PATH` in a plain shell step - the workflow assumed
it would be. Confirmed the real fix rather than guessing a path that
could shift with future runner-image updates: switched to
`nttld/setup-ndk`, a small, widely-used action built specifically for
this, with `link-to-sdk: true` so Gradle sees it correctly. Checked its
real README directly for the exact usage rather than trusting a search
snippet.

Also bumped `actions/checkout` and `actions/setup-java` to v5 - the same
log carried an explicit deprecation warning for both at v4, cheap to fix
while already in the file.

Nothing else in the run points to a problem - checkout and JDK setup both
succeeded cleanly before the NDK step. The actual `./gradlew
assembleDebug` step has still never run even once; this fix should get
the workflow to it for the first time.

## Next action
Aditya re-syncs (unzip the new project state over the existing folder,
`git add . && git commit -m "Fix CI NDK step" && git push`) and checks
the Actions tab again. If it reaches the actual Gradle build step this
time, whatever it reports - green or red - will be the most real signal
this project has had yet. A red result at that point would be genuinely
useful too: real compiler/Gradle errors, not more speculation about them.

## Second real CI run - a foundational gap, not a code bug

NDK step passed clean this time - `nttld/setup-ndk` downloaded, extracted,
and linked to the SDK without incident. New failure, read directly from
the log again: `chmod: cannot access 'gradlew': No such file or
directory`, at the very next step.

**Real root cause, checked rather than assumed**: `gradlew`,
`gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` have never existed
in this project at all - confirmed by checking this session's own working
copy directly. Only `gradle-wrapper.properties` (a small text file naming
which Gradle version to fetch) was ever present. This makes sense given
the project's actual history: the wrapper's jar and scripts are normally
generated by running `gradle wrapper` against a real Gradle install, which
has never happened here - nothing to compile with means nothing ever
generated them. Not a bug introduced by any session's code, a gap that
was always going to surface at exactly this point, the first time
anything real tried to run `./gradlew`.

**Fix**: `gradlew`/`gradlew.bat` are Gradle's own standard, unmodified
bootstrap scripts - identical across every Gradle project, nothing
launcher-specific in them, Apache-2.0. `gradle-wrapper.jar` is a small
compiled bootstrap jar, versioned to the wrapper protocol generation
(distinct from the actual Gradle version it fetches, which
`gradle-wrapper.properties` alone controls). Rather than guess at
compatibility, pulled all three from ZalithLauncher2's own repo, already
in hand - it targets Gradle 9.4.1, closest real match to this project's
already-specified 9.5.1 (confirmed a real, released version, not
guessed). `gradle-wrapper.properties` itself untouched - it was already
correct, just orphaned without the files that use it.

## Next action
Same as above: re-sync and push. This is a real, foundational blocker
that's been sitting underneath everything - genuinely reasonable to
expect this gets past `./gradlew` actually running for the first time.
Whether the real Gradle/Kotlin compile behind it succeeds on the first
try is a separate question this hasn't tested yet.

## Third real CI run - first actual project-configuration error

Wrapper fix worked - Gradle 9.5.1 downloaded, started a daemon, and got
all the way into configuring the `:app` module for the first time ever.
New failure, again read directly from the log rather than guessed at:

> Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is required
> when compose is enabled.

**Real root cause**: this project's `buildFeatures { compose = true }`
has always been there, but the separate Compose compiler Gradle plugin
that Kotlin 2.0+ requires alongside it never was - genuinely never
needed to surface before now, since nothing had compiled far enough to
reach the point that checks for it.

One thing worth being precise about since it looks related but isn't: the
root `build.gradle.kts` comment about AGP 9.x's built-in Kotlin support
(no separate `org.jetbrains.kotlin.android` plugin needed) is *correct*,
confirmed directly against AGP's own release notes rather than doubted
just because a Kotlin-adjacent error showed up near it. The Compose
compiler is a genuinely separate requirement from general Kotlin
compilation - AGP 9.0+ bundles the Kotlin compiler itself, but not the
Compose-specific one.

**Fix**: added `org.jetbrains.kotlin.plugin.compose` at version `2.2.10`
- confirmed, not guessed, as the exact Kotlin version AGP 9.1.1 bundles
internally (cross-checked against two independent sources, since a
mismatched version here is a well-known separate failure mode: the
Compose compiler plugin must match the Kotlin version exactly, the same
kind of exact-match requirement already seen with LWJGL-to-Minecraft-
version pairing elsewhere in this project). No `libs.versions.toml`
catalog in this project, so applied directly by id/version, matching the
style already used for AGP rather than introducing a new pattern as a
side effect of this fix.

## Next action
Re-sync and push again. Nothing else in this run's log points to a
second issue - the failure was purely at project configuration, before
any actual Kotlin source file was touched. If this clears it, the *next*
run is the first one with a real chance of reaching actual compilation of
this project's own code - the real test all of the above was leading up
to.

## CI added; binary-extraction plan for the LWJGL native gap

Aditya asked directly about timeline, about writing an Android-native
LWJGL port from scratch, and about actually compiling this. All three
converged on the same real answer.

**A from-scratch LWJGL-for-Android native port is out of scope for this
project to attempt blind.** Not a capability judgment call made lightly -
the real ecosystem evidence backs it up. AngelAuraMC/Amethyst-Android's
own recent release notes (checked directly, not assumed) show them
*still* fixing real bugs in exactly this layer as of a few months ago:
race conditions in `pojavexec` load timing, incorrect GLFW version-select
logic, Krypton Wrapper environment variable bugs. This is years of
multi-contributor, real-device-tested work, still actively shifting.
Writing a competing version blind, with no NDK toolchain to compile or
device to test against, would produce something that reads plausibly and
has a low real chance of working - worse than not attempting it.

**The better path, and the one actually pursued**: get the real compiled
binary directly from Amethyst's own releases rather than the source-only
zip. `AngelAuraMC/Amethyst-Android`'s GitHub releases ship an actual
working `Amethyst.apk` - an APK is just a zip, so the real, working
`.so` files (whatever currently backs their LWJGL jars, OpenAL included)
are extractable directly from it. Same legitimacy as everything else
vendored so far (JDK tarballs, Turnip driver, Krypton Wrapper AAR): an
officially published release artifact, not scraped source. Confirmed the
releases exist and checked the notes; the actual asset download links are
JS-rendered on GitHub's release page and out of reach of this session's
fetch tool. Next concrete step once picked back up: get a direct asset
URL (either from Aditya, or by finding an API/CDN path that resolves
statically) and pull it directly.

**docs/BUILDING.md and `.github/workflows/android-build.yml` added.**
This project has never been compiled - every file so far has been written
and manually reviewed, never built. That gap matters more than any single
remaining feature at this point: a real compiler will surface errors no
amount of manual review catches. The workflow builds a debug APK on every
push and uploads it (or the failure logs) as a downloadable artifact,
using GitHub's own runners rather than needing local Android Studio.
Recommended as a **private** repo specifically so this doesn't conflict
with the standing "no repo until it's working" call - Actions works the
same either way, and flipping visibility later is a one-click change.


