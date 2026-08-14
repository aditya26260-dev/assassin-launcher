# Assassin Launcher — Roadmap

The shape of the whole project, phase by phase.

## Phase 0 — Research (done)
Confirm how Minecraft actually renders today, study the six launchers you
gave me for real bugs and gaps instead of guessing, and work out which
translation layer / driver fits which Minecraft version and GPU family.
Full writeup: `docs/PHASE0_RESEARCH.md`.

## Phase 1 — Architecture (done)
Turn the research into actual decisions: Kotlin/Java vs. native code split,
which JVM to bundle, the rendering/driver selection strategy, mod
compatibility approach, background stability approach, directory layout.
Full writeup, each decision with its reason: `docs/ARCHITECTURE.md`.

## Phase 2 — The app itself (starting now)
The big one. Home screen, account login, the mods/resource packs/shaders
manager, game profile editor, global settings, in-game overlay, input
handling. Most of the actual work happens here, delivered piece by piece
rather than as one giant drop at the end.

## Phase 3 — Look and feel
Logo, fonts, colors, animation quality, the README. Done to the standard
the brief asks for, not templated-looking.

## Phase 4 — Legal and shipping
Going open source means no DRM/anti-piracy work. What's left: making sure
GPL-3.0 compliance is actually clean (credits, notices preserved), and
reviewing Mojang's and Microsoft's usage guidelines before any public
release, since that part matters regardless of the source being open.

## After that
Getting it compiled into an actual APK, which happens outside this sandbox
since there's no real internet access in here, and testing on real
hardware, since that has to be your device, not mine.
