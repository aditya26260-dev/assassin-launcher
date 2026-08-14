# Credits and Licenses

Assassin Launcher is open source, licensed under GPL-3.0 (see `/LICENSE`).
This document is living — updated as components are actually added.

## On the reference projects
GPL-3.0 is compatible with the LGPL-3.0 and GPL-3.0 material in the six
reference projects (PojavLauncher/Amethyst/MojoLauncher, FoldCraftLauncher,
ZalithLauncher2, PrismLauncher). Where something is genuinely adapted from
one of them rather than independently written from public documentation,
it gets credited here and the original notices preserved, per GPL-3.0's
actual requirements, not folded in silently.

## Confirmed safe to bundle regardless of this project's own license
- **Turnip Vulkan driver** (Adreno GPU driver, Mesa project) — MIT license.
  Currently bundled: 710/720/722 build v3.3 (Mesa 26.3.0, Vulkan 1.4.354,
  by vauzi, 1 Aug 2026) at
  `app/src/main/assets/drivers/adreno-7xx-710-720-722/`.
- **OpenJDK builds** used as the bundled JVM — GPL-2.0 with Classpath
  Exception, which is specifically written to allow this kind of bundling.
- **libadrenotools** (bylaws/libadrenotools) + **liblinkernsbypass**
  (bylaws/liblinkernsbypass) — both BSD-2-Clause, confirmed directly in
  the source. Both now vendored at
  `app/src/main/cpp/third_party/libadrenotools/` and wired into the native
  build via CMake. The mechanism the Turnip driver-swap depends on
  (architecture doc 5.3).
- **MobileGlues** — LGPL-2.1, confirmed at its own repo. v1.3.5 native
  libraries (arm64-v8a) at `app/src/main/assets/drivers/mobileglues-1.3.5/`.
  Loading mechanism confirmed against Amethyst Launcher's actual source
  and implemented for real: a standard dlopen substituting for the app's
  own EGL library, no linker namespace tricks needed (that's specific to
  the Vulkan/Turnip case).
- **GL4ES / HolyGL4ES / Krypton Wrapper (NG-GL4ES)** — MIT, confirmed both
  at the upstream repos and in the bundled license file inside Amethyst's
  own krypton_wrapper-release.aar. Krypton Wrapper's arm64-v8a library
  extracted from that AAR and integrated at
  `app/src/main/assets/drivers/krypton-wrapper/`, same real dlopen
  mechanism confirmed for MobileGlues. Base GL4ES/HolyGL4ES itself not
  obtained yet - lowest-priority true last resort.
- **Zink** — MIT (same Mesa project as Turnip).
- **LWJGL 3** — BSD-3-Clause, confirmed directly on Maven Central's own
  published POM metadata. Mojang's manifest carries no Android LWJGL
  natives at all (every reference launcher sources this from a custom
  Android-targeted build, not Maven Central); the Java-side 3.3.3 jars
  vendored here come from Amethyst's own `jre_lwjgl3glfw` module, at
  `app/src/main/assets/lwjgl/lwjgl-android-3.3.3/` (extracted to internal
  storage at launch time, same pattern as the MobileGlues/Krypton/Turnip
  binaries below - these are loaded by the embedded game JVM, not by our
  own Kotlin code, so they're bundled as assets rather than a Gradle
  dependency). The native `.so` counterpart these jars call into
  (Amethyst's own build depends on the SDL2/sdl2-compat git submodules,
  not included in a plain source zip) is a real, open gap - see
  docs/PROGRESS.md.
- **ANGLE** — BSD-3-Clause, confirmed directly at Google's own repo (an
  earlier pass had this flagged as unresolved over a conflicting "All
  Rights Reserved" credit on one of the reference projects' own pages —
  that credit was simply wrong, corrected here).

## Under review — not yet decided how (or whether) these get used
- PojavLauncher and its lineage (Amethyst Launcher, MojoLauncher) — LGPL-3.0
- FoldCraftLauncher — GPL-3.0
- ZalithLauncher2 — GPL-3.0
- PrismLauncher — GPL-3.0
- DroidBridge Launcher — no top-level license of its own; ships its own
  open-source-notices file flagging PojavLauncher-derived code as an open
  item

Full detail and sources for all of the above: `docs/PHASE0_RESEARCH.md`.

## Not yet reviewed
- CurseForge API — needs an API key, terms of service not yet reviewed.

## Third-party libraries (standard Android dependencies, not vendored)
- OkHttp (Square) — Apache License 2.0. Used for the Modrinth API client
  and mod downloads.
- OpenNBT (Steveice10) — permissive (BSD-3-Clause per Maven Central's own
  metadata; the current GitHub README says MIT, possibly a later
  relicense not yet reflected there - either way, safe as a dependency
  regardless of this project's own license). Real read/write of
  Minecraft's actual servers.dat format, confirmed as the same library
  Zalith Launcher 2 uses.
- Apache Commons Compress — Apache License 2.0. Real tar.xz extraction for
  provisioned JVMs, same library Amethyst Launcher's own code uses.
- org.tukaani:xz — public domain. Commons Compress's underlying XZ codec,
  same dependency pairing confirmed in Amethyst's real, working setup.

## JVM runtimes (architecture 5.2)
Real, verified Java 8/17/21/25 builds for Android ARM64, sourced from
AngelAuraMC/angelauramc-openjdk-build's GitHub Releases - the same
permanent, stable source Amethyst Launcher's own live code downloads from
(confirmed directly in their NewJREUtil.java, not guessed). Verified by
extracting all four directly with system tar/xz: real JDK structure
(bin/java, release file with genuine version strings) confirmed for each.
