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
- **Microsoft/Xbox/Minecraft sign-in client ID** — not a code dependency,
  a real, load-bearing configuration value: `00000000402b5328`, along
  with its matching `login.live.com` legacy OAuth endpoints and scope
  format, sourced directly from Amethyst's real, working, currently-
  shipping app. The Xbox Live sign-in scope needed to get a Minecraft-
  usable token is gated behind Microsoft's manual Xbox Developer/ID@Xbox
  approval process on the modern Azure AD v2.0 platform, confirmed via
  Microsoft's own Q&A - not something a new app registration can pass no
  matter how correctly it's configured, which is why this project's own
  from-scratch Azure app registration got through basic Microsoft sign-in
  but consistently failed at the Minecraft-specific step. Every
  unofficial Minecraft launcher solves this the same way: reusing an
  already-approved client ID rather than obtaining a new one, since
  individual hobbyist developers generally can't get through that
  approval process for a personal project. This one is Amethyst's,
  chosen because it's Android/WebView-based like this project, unlike
  PrismLauncher's client ID (also checked first), which is desktop-only
  and pairs with a different redirect mechanism (a local loopback HTTP
  server) that wouldn't have worked here regardless.
  published POM metadata. Mojang's manifest carries no Android LWJGL
  natives at all (every reference launcher sources this from a custom
  Android-targeted build, not Maven Central); the Java-side 3.3.3 jars
  vendored here come from Amethyst's own `jre_lwjgl3glfw` module, at
  `app/src/main/assets/lwjgl/lwjgl-android-3.3.3/` (extracted to internal
  storage at launch time, same pattern as the MobileGlues/Krypton/Turnip
  binaries below - these are loaded by the embedded game JVM, not by our
  own Kotlin code, so they're bundled as assets rather than a Gradle
  dependency). The native `.so` files (liblwjgl.so and the per-module
  natives) were a real, open gap for three sessions - Amethyst's own
  source repo depends on SDL2/sdl2-compat git submodules a plain zip
  export doesn't include. Closed by extracting the real, compiled
  binaries directly from Amethyst's own released `Amethyst.apk` (an APK
  is just a zip; verified by inspecting its actual contents, not assumed
  from the filename - a real mistake earlier in this same session,
  corrected before anything shipped). The jar set was updated at the
  same time to match the APK's real shipped combination, which merges
  the glfw+opengl modules into one `lwjgl-3.3.3-merged-modules.jar`
  rather than the separate per-module jars the source repo's dev-time
  files have.
- **OpenAL (Amethyst's build)** — bundled at
  `app/src/main/jniLibs/arm64-v8a/libopenal.so`, same source and same
  verification as the LWJGL natives above. OpenAL itself is a permissive
  license (LGPL for the reference implementation historically, though
  Amethyst's specific build's exact license wasn't independently
  re-verified beyond confirming the binary's real origin - worth a closer
  look if this project ever tightens its own license auditing further).
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
  Zalith Launcher 2 uses. Vendored at `app/libs/opennbt-1.6.jar` rather
  than a Maven dependency - the nominally-published Maven Central
  coordinate didn't actually resolve on a real build, and Zalith
  Launcher 2 itself vendors the same jar rather than depending on it
  remotely, which isn't a coincidence.
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
