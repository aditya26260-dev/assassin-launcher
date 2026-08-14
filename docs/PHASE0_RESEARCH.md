# Phase 0 Research — Assassin Launcher

Status: in progress. This is a working document, not the final sign-off version.
Short status lives in PROGRESS.md; this file is where the actual findings and
sources accumulate.

---

## 1. How the game actually renders, today (verified July 30, 2026)

Minecraft Java Edition has used LWJGL/GLFW and OpenGL since launch. That
changed recently:

- Mojang shipped an **experimental native Vulkan renderer** in Java Edition
  26.2, released June 16, 2026 (first snapshot April 7, 2026). It's an
  opt-in toggle in graphics settings — OpenGL is still the default. Mojang's
  stated plan is to remove OpenGL once Vulkan is proven stable and
  performant enough. This is the rendering-backend prerequisite for Vibrant
  Visuals (the lighting/shadow/water overhaul already live on Bedrock)
  coming to Java Edition.
- Stated minimum requirement right now: **Vulkan 1.2 with dynamic rendering
  and push descriptors** (Minecraft Wiki, checked live — the wiki notes this
  may move over time, so this needs to be a runtime check against the
  device's actual driver, not a hardcoded assumption in our code).
- The Vulkan renderer runs on its own dedicated thread, separate from game
  logic/physics/chunk generation — a real architectural change from OpenGL,
  which shared the main thread with game logic.
- Sources: Minecraft Wiki (26.2-snapshot-1 page), Tom's Hardware, TechTimes,
  VideoCardz — cross-checked across independent outlets, all consistent on
  the version number, date, and Vulkan 1.2 requirement.

**Correction to the original brief:** it framed this as "OpenGL-path
versions" vs. "native Vulkan versions," implying a clean split by Minecraft
version. That's not quite right. As of 26.2+, a single version carries
*both* renderers with a toggle. Only pre-26.2 versions are OpenGL-only. Our
render-path selection logic needs to account for "this version has a choice"
as its own state, not just pick one path per version.

## 2. VulkanMod and Sodium's own Vulkan work

- **VulkanMod** is a third-party Fabric mod (separate from Mojang's native
  renderer) — a full rewrite of the renderer targeting Vulkan 1.2, not a
  translation shim like Zink. It explicitly does not coexist with Sodium,
  OptiFine, or Canvas, because all four are alternative full renderer
  replacements competing for the same job. This directly confirms the
  Sodium+VulkanMod conflict the brief named as an example — it's real and
  it's a fundamental incompatibility, not a bug either side is likely to fix.
- **New finding, not in the original brief:** Sodium itself shipped an early
  native Vulkan backend on June 16, 2026 (Sodium 0.9.x, for MC 26.2),
  separate from VulkanMod — presumably targeting Mojang's own Vulkan
  renderer path rather than replacing it. This is about six weeks old as of
  today. Worth tracking closely since it changes the "which renderer does a
  given mod combination actually want" logic, and none of the six reference
  launchers are likely to have caught up with it yet given their bundled
  file timestamps.

## 3. Sodium on Android — what the user's screenshot actually shows

The screenshot supplied is from Sodium's own system requirements page, and
it says plainly: devices using OpenGL translation layers (it names GL4ES and
ANGLE specifically) "are not supported and will have severe performance
issues," and that Sodium only officially supports desktop GPUs with OpenGL
4.5 drivers. This is Sodium's own maintainers' stated position, not
something existing Android launchers invented or a limitation they imposed.

This matters for the brief's request to "confirm or disprove" whether Sodium
is stripped down on Android: it isn't being deliberately stripped by
launcher authors — Sodium is written against desktop OpenGL 4.5 and the
mismatch with what a translation layer can actually provide on mobile GPUs
is a real, upstream-acknowledged gap, not a choice any Android launcher
made. "Full PC feature parity" for Sodium specifically depends on either (a)
Mojang's native Vulkan path maturing and Sodium's new Vulkan backend
following it — which sidesteps the OpenGL-translation problem entirely by
not going through a translation layer at all — or (b) accepting that the
OpenGL path will keep having real ceiling effects that better translation
software can narrow but not eliminate. Framing this honestly to the user
later matters more than promising "full parity" outright.

## 4. Driver and translation layer licensing (verified against source, not memory)

| Component | License | Source checked |
|---|---|---|
| Mesa (includes Turnip/Freedreno, Zink) | MIT | docs.mesa3d.org/license.html, Mesa's own GitLab LICENSE file |
| Uploaded Turnip build (Adreno 710/720/722, Mesa 26.2.0) | MIT (inherits from Mesa) | meta.json bundled in the upload confirms vendor/version; license itself comes from Mesa |
| Panfrost / PanVK (Mali) | MIT (same Mesa project) | same as above; but functionally very early — PanVK is Khronos-conformant only on Mali-G610 as of May 2026, non-conformant elsewhere, and brand-new Mali hardware support ships behind an explicit "broken driver" debug flag from its own developers |
| ANGLE | BSD-3-Clause | confirmed directly at google/angle's own repo (README.chromium: "License: BSD-3-Clause"). FoldCraftLauncher's "All Rights Reserved" credit was simply wrong — not using that as a reason for caution anymore. |
| MobileGlues | LGPL-2.1 | confirmed directly at MobileGL-Dev/MobileGlues-release's own README: "MobileGlues and its plugin application are licensed under GNU LGPL-2.1 License." Matches Amethyst's credits page. |
| GL4ES / HolyGL4ES / Krypton Wrapper (NG-GL4ES) | MIT | confirmed at ptitSeb/gl4es and BZLZHH/NG-GL4ES's own repos |
| Zink | MIT (Mesa) | same basis as Turnip — part of the same Mesa project |
| OpenJDK builds bundled by Amethyst/Mojo | GPL-2.0 with Classpath Exception | standard OpenJDK terms; Classpath Exception is specifically designed to allow bundling a JRE/JDK inside another app without the app inheriting GPL |

## 5. Reference project licensing (verified from the actual uploaded files)

Every one of the six launcher codebases supplied is copyleft:

- PojavLauncher (the common ancestor of Amethyst and Mojo) — LGPL-3.0, per
  DroidBridge's own `OPEN_SOURCE_NOTICES.md`
- Amethyst Launcher — LGPL-3.0 (verified: full license text in its own LICENSE file)
- MojoLauncher — LGPL-3.0 (same)
- FoldCraftLauncher — GPL-3.0 (verified: full license text in its own LICENSE file)
- ZalithLauncher2 — GPL-3.0 (same)
- PrismLauncher — GPL-3.0 (same, plus COPYING.md)
- DroidBridge Launcher — no top-level LICENSE of its own, but ships an
  `OPEN_SOURCE_NOTICES.md` that explicitly flags its own compatibility code
  as "PojavLauncher-derived unless independently documented otherwise" and
  says it "keeps third-party license obligations attached to the code they
  apply to, including... source availability where required." In other
  words, even the one project in this set that looks closest to "write our
  own thing while learning from the others" doesn't come out the other side
  fully proprietary — it's carrying real LGPL obligations for the parts it
  derived, tracked as an open audit item rather than a solved problem.

This is covered in full in PROGRESS.md's "blocking decision" section — flagged
there because it affects Phase 1, not repeated in depth here.

## 6. Translation layers — what's actually out there (verified against each project's own source/docs)

- **GL4ES** (original, ptitSeb) — translates OpenGL 2.1/1.5 down to GLES
  2.0/1.1. MIT license. Broad hardware reach (works on weak/old GLES1.1
  hardware) but a low ceiling — well under Sodium's stated OpenGL 4.5
  requirement and even under vanilla Minecraft's normal OpenGL 3.3 baseline
  in places, which is exactly why Minecraft-specific forks exist.
- **HolyGL4ES** — a GL4ES fork with fixes targeted specifically at Minecraft
  Java Edition. Same MIT lineage.
- **Krypton Wrapper = NG-GL4ES** — these are the same thing. It's a further
  fork of GL4ES (by BZLZHH) adding more advanced OpenGL feature support,
  MIT-licensed. Confirmed via its own repo and cross-checked against
  Amethyst's and FCL's own credits pages. Actively maintained: recent
  releases specifically call out "enabled proper usage of the Sodium mod
  with the GLES 3.0 backend" and shader pack compatibility improvements.
  Known gotcha from its own release notes: on Snapdragon 8 Gen 2 devices,
  or any device without Vulkan 1.2+, a separate non-ANGLE build is needed
  because the regular ANGLE-based build doesn't work right there — a real,
  chip-specific compatibility case to bake into the driver/renderer
  selection logic, not something to find out later.
- **MobileGlues** — a different lineage entirely, not a GL4ES fork. Targets
  host OpenGL ES 3.2 as its baseline (vs. GL4ES's GLES 2.0/1.1), built with
  Minecraft Java Edition specifically in mind. Likely a meaningfully higher
  ceiling for modern rendering features given the higher baseline. License
  is reported inconsistently across sources (LGPL-2.1 per Amethyst's credits
  page) — needs its own direct check against the MobileGL-Dev repo before
  relying on that.
- **ANGLE** — used across this whole ecosystem, but FoldCraftLauncher's own
  credits page lists it as "All Rights Reserved," which conflicts with
  ANGLE being a Google open-source project. Flagging as unresolved rather
  than guessing either way — needs a direct check against Google's ANGLE
  repository before any decision that depends on its license.
- **Zink** — Mesa's OpenGL-over-Vulkan layer, MIT (inherits from Mesa).
  FoldCraftLauncher's own feature list names Zink as one of the renderer
  options its shader support depends on ("VirGL/Zink/MG renderer"),
  confirming it's a live option in this ecosystem, not just a theoretical
  one.

## 7. Real competitor bugs and gaps found so far (from actual issue trackers and release notes, not guesses)

- **Amethyst Launcher** (its own GitHub releases page): recent, specific,
  unresolved-feeling pattern — Vulkan backend crashes tied to floating
  window / auto screen rotation ("you will crash if the game ever goes off
  screen on the Vulkan backend"), while their own notes say the OpenGL
  backend is "completely free of crashes related to windowing." That's a
  real, current example of the Vulkan path being meaningfully less stable
  than OpenGL on Android right now, worth designing around rather than
  assuming Vulkan is simply "the better option" once available.
- **FoldCraftLauncher**: a live, detailed issue (#1180) shows Krypton
  Wrapper failing to launch entirely on a real device (Samsung A56, Exynos
  s5e8855) with exit code 6 — a concrete renderer-selection failure case.
- **ZalithLauncher2**: their own release notes carry an explicit warning to
  users — "If your device does not support Vulkan 1.2, please do not use
  the Vulkan backend" — meaning they don't yet do automatic capability
  detection; it's on the user to know their own device's Vulkan support
  and avoid picking the wrong backend themselves. That's precisely the gap
  the brief's smart-diagnostics idea (section 5.6) is meant to close.
  Their own troubleshooting page also separately confirms Microsoft/Mojang
  login failures as a recurring user pain point, and a large share of their
  open issues get closed as "invalid format" or "need more info" rather
  than fixed, which points to a support/triage gap as much as a code one.
- **MobileGlues specifically**, independent of any one launcher: a crash
  report in a shader-heavy mod's own tracker (Simple Clouds) shows a
  compute-shader load failure when run through MobileGlues on Zalith
  Launcher 2 — a real, concrete example of where the GLES-translation
  approach hits a wall (compute shaders are a known weak spot for GLES
  translation generally, not specific to this one mod).

## 8. The actual rendering/driver decision matrix (this is the core of 5.3)

The single most useful finding this pass, sourced directly from PojavLauncher's
own official wiki: on Android, **Zink (OpenGL-over-Vulkan) paired with Turnip
gets you real OpenGL 4.5 on Adreno GPUs** — meeting Sodium's actual stated
desktop requirement, not just vanilla Minecraft's floor. On Mali, the same
Zink approach only reaches OpenGL 3.1/3.2, because it's gated by Panfrost/PanVK's
much weaker Vulkan support. And critically: **Adreno GPUs without a working
Turnip driver will crash outright when Zink is used** — Zink's ceiling is
entirely a function of the Vulkan driver underneath it, which is exactly why
the brief's "bundle Turnip per GPU family" requirement matters as much as it
does. There's also a specific known-and-abandoned bug: Minecraft 1.16.5 and
earlier can't run through Zink on Mali at all, a driver issue PojavLauncher's
own wiki marks "will not fix."

This changes the shape of the OpenGL-path decision from a single choice into
a real tree:
- **Adreno + working Turnip:** Zink is the strongest option — real OpenGL 4.5,
  closest to actual PC parity, including for Sodium.
- **Adreno without a working Turnip (old/unsupported chip, or Turnip fails):**
  fall back to the GL4ES family or MobileGlues.
- **Mali:** Zink tops out around OpenGL 3.1/3.2 even in the best case, and
  can't run pre-1.17 Minecraft at all — GL4ES family or MobileGlues will be
  the realistic default here, with Zink offered but not assumed.
- **Any GPU, pre-1.17 Minecraft on Mali specifically:** must not default to
  Zink at all, given the confirmed unfixed bug above.

Turnip itself isn't one driver — it's genuinely segmented by GPU generation,
confirmed against current (this week's) community build repos:
- **Adreno 6xx** (Snapdragon 600–800 series, 7 Gen, 8 Gen 1–3): the mature,
  stable tier — described by builders as "the everyday driver for most
  Adreno phones."
- **Adreno 7xx**: mostly an extension of the 6xx work, but not uniformly
  mature — the specific 710/720/722 build the user uploaded is explicitly
  flagged upstream as coming from separate, less-stable patches, distinct
  from mainline 7xx chips like 730/740.
- **Adreno 8xx** (Snapdragon 8 Elite and newer — 810/825/829/830/840): a
  genuinely new architecture ("Slice" design) that broke compatibility with
  the 6xx/7xx driver work and needed its own separate development effort.
  Real, current reports of specific chips in this family being notably slow
  or glitchy even with the newest builds.

Practical effect: driver selection can't be "Adreno vs. not Adreno," it
needs to know the specific chip generation, and the newest flagship chips
are the least safe to assume will just work.

## 9. Remaining competitor findings

- **MojoLauncher**: the standout recurring pattern across its issue tracker
  is Android's scoped storage / file-access restrctions on Android 11+ and
  especially 14+ — multiple independent reports of mod files being
  unmovable, folders showing as readable but not actually writable, and
  users having to fully wipe and reinstall to get file/mod recognition
  working again. That's a real, current, non-rendering gap: proper handling
  of Android's Storage Access Framework for the mods/instances directory
  structure matters as much as anything in the rendering pipeline. Their
  own README also self-reports a concrete GL4ES-family limitation: large
  texture atlases can distort on Holy GL4ES ("stretched/blocky textures in
  modpacks"), consistent with the ceiling issues noted in section 6.
- **DroidBridge Launcher**: confirmed to be a real, actively-developed
  public repo (recent commits, "one person doing this on the side," by the
  developer's own description, over about a week and a half). Directly
  relevant to the licensing question already flagged: the developer's own
  release notes state plainly that they "utilized some of the original
  works of Pojav and Boardwalk back end" — this isn't just the
  `OPEN_SOURCE_NOTICES.md` file being cautious, it's the developer
  confirming it themselves. Also has a live, current bug tied to the same
  Mojang 26.2 Vulkan rollout covered in section 1: their own release notes
  describe "OpenGL is still reporting Vulkan but the game is still NOT
  running with OpenGL," requiring a rewrite of their EGL/surface bridge
  code — a real, current example of how much churn the Vulkan transition is
  causing across this whole ecosystem right now, not a settled target.

## 10. Gap analysis — what this means Assassin Launcher should actually do differently

Backed by sections 1-9 above, not assumption:

1. **Automatic Vulkan capability detection, not a user-facing warning.**
   ZalithLauncher2, the most polished of the six on this front, still just
   tells the user not to pick Vulkan if unsupported. None of the six
   auto-detect and auto-fall-back. This is a real, verified, currently-open
   gap across the whole competitive set.
2. **Zink-over-Turnip as the default OpenGL path on Adreno, not GL4ES-family
   by default.** Every one of the six leans on GL4ES-family and/or
   MobileGlues as their primary renderers. None of the research surfaced any
   of them defaulting to Zink for the OpenGL path on capable Adreno chips,
   despite it being the only path that reaches real OpenGL 4.5. Worth
   double-checking this isn't defaulted elsewhere before leaning on it too
   hard, but it's a real, sourced opportunity, not a guess.
3. **Chip-generation-aware Turnip selection**, not one driver for all Adreno.
   Confirmed real instability specifically at generation boundaries (7xx
   sub-variants, and especially the new 8xx family).
4. **Proper Storage Access Framework handling for mods/instances**, not
   treated as an afterthought. MojoLauncher's issue tracker shows this
   causing real, repeated user pain independent of anything rendering-related.
5. **Design for the Vulkan transition being unfinished, not stable.**
   Two of the six (Amethyst, DroidBridge) have live, current bugs directly
   tied to Mojang's 26.2 Vulkan rollout. Whatever ships first should treat
   the OpenGL path as the dependable default and Vulkan as opt-in and
   closely monitored, matching what Mojang itself is doing upstream.
6. **A real triage/support process**, not just code. A large share of
   ZalithLauncher2's and MojoLauncher's open issues are closed or stuck for
   lack of basic information (device, log, repro steps). Worth designing
   the in-app crash/log capture (5.6) so a usable report is attached
   automatically, rather than relying on users to describe what happened.

## 11. Still to do in Phase 0

- PrismLauncher specifically hasn't gotten a dedicated issue-tracker pass —
  deprioritized since it's a desktop launcher and the other five gave
  strong, consistent, Android-specific signal already. Will circle back if
  Phase 1 turns up a specific question only PrismLauncher's history can
  answer.
- Mod-patch-level research (Create, Distant Horizons, Bobby, Simple Voice
  Chat's Android "module not supported" error) — not started. This is
  implementation-detail research, more useful once Phase 1 architecture is
  locked and we're actually building the compatibility layer, rather than
  something that changes the architecture decision itself.
- Everything else originally listed here is done: translation layer
  landscape and licensing (section 6), the Zink/Turnip/Adreno decision
  matrix (section 8), and the gap analysis synthesis (section 10).

This is enough to move into Phase 1 architecture. The remaining open item
(mod-patch specifics) doesn't block that — it'll get folded in once we're
building the compatibility layer itself.

## 12. Renderer research, round two — corrections and additions from user testing/knowledge

Prompted by the user's own experience with these launchers. Worth documenting
clearly since it changes real parts of section 8 above.

**LTW ("Large Thin Wrapper")** — a real renderer this research had missed
entirely the first pass. By Artdeell, PojavLauncher's own lead developer,
at github.com/artdeell/LTW. Confirmed directly from PojavLauncher's own
release notes: GLES-based ("incomplete OpenGL 3.2, based on OpenGL ES 3.0
with optional 3.1/3.2 features"), explicitly built to bring Zink-requiring
functionality (Sodium, Iris with limited shader support, Immersive Portals,
Create) to devices where Zink doesn't work, via GLES translation instead of
requiring a working Vulkan+Turnip stack. Only works on Minecraft 1.17+, same
floor as MobileGlues. Real bugs found in its own issue tracker: a z-buffer/
xray-style glitch on 1.21.5 specifically (both Adreno 6xx and 7xx reports),
blank-sky issues with specific shaders when combined with Sodium+Iris. Not
yet in PojavLauncher's Play Store build as of the source checked (worth
watching for Phase 4's distribution review). One correction to my own
earlier read: PojavLauncher's wiki page listing LTW, ANGLE, and Zink back
to back reads, when scraped, like it's describing LTW as Vulkan-based -
that's a scraping artifact bleeding Zink's own entry into LTW's. The GitHub
release notes are the reliable source here, and they're clear: LTW is GLES,
Zink is Vulkan. Two different mechanisms aimed at a similar goal.

**MobileGlues vs. LTW vs. Krypton Wrapper - no clean universal winner.**
PojavLauncher's own wiki rates MobileGlues as "comparatively same in
performance as GL4ES," not clearly ahead of it. Real community discussion
shows genuine device-dependent splits - one specific real comparison (a
single user's Poco F3, 1.21.4): Sodium Nightly on LTW hit 1150fps against
OptiFine on GL4ES at 1000fps, but the same source explicitly notes "for
some people OptiFine on GL4ES might be faster than Sodium Nightly on LTW."
Treating this as it actually is: anecdotal, single-device data, not a
settled ranking. All three (MobileGlues, LTW, Krypton Wrapper) stay in the
fallback pool for the OpenGL path rather than one getting crowned default
without real testing across device families.

**Krypton Wrapper's actual scope** - the user's understanding was that it's
used below 1.17 specifically, while my modeling had it purely as a
capability-based fallback (below MobileGlues' GLES floor) regardless of
Minecraft version. Both are true simultaneously, not in conflict: Krypton
Wrapper (NG-GL4ES) naturally covers pre-1.17 versions that MobileGlues/LTW
can't touch at all (their shared 1.17+ floor), and it's also the right
fallback for weak devices on newer versions. Two different axes (version
floor vs. device capability floor) pointing at the same tool. The
version-floor axis needs adding to the render logic - it currently only
reflects the device-capability axis.

**"Freedreno" as a separate native option** - worth being precise. "Freedreno"
is the umbrella open-source Adreno driver project; Turnip is specifically
its Vulkan driver. There's also a native OpenGL driver in the same project
(sometimes just called "Freedreno" for short) that talks to Adreno hardware
directly, without GLES-translation or Vulkan. Flagging honestly as not
properly researched this pass rather than folding in an under-researched
conclusion - worth a dedicated look at whether a direct native-Freedreno-GL
path is viable on Android and how it compares to Zink+Turnip or
GLES-translation.

**Zink's real-world performance, per the user's own testing: poor.** This
matters a lot - it's exactly the real-device signal this project can't get
from research alone. PojavLauncher's wiki claims real OpenGL 4.5 on
Adreno+Turnip via Zink, true as a *capability* claim, but capability isn't
the same as running well - and "Kopper" (Mesa's Zink/SDL2-Vulkan windowing
integration) being singled out suggests the windowing/present layer, not
just GL-translation itself, may be where problems show up. A live open
PojavLauncher issue (#6118, Mali-G615) reports Zink crashing after
previously working, root cause unclear even to the reporter. Net effect:
Zink-over-Turnip stays modeled as the theoretical best-case for real
OpenGL 4.5 coverage, but shouldn't be the assumed default the way section 8
currently treats it - needs validation against real devices before
defaulting to it over MobileGlues/LTW, and should probably ship as an
available option rather than the automatic first choice until validated.

**Turnip driver catalog, from vauzi's actual repo (github.com/Vauzi-17/710)
plus the wider ecosystem it feeds into:**
- Latest release there is **v2.8** (16 Jul 2026) - newer than the v2.6.1
  build originally uploaded. Recommended mode for this specific build:
  `sysmem`, not `gmem` (the repo's own release notes call this out
  explicitly for these three chips).
- Releases are automated twice a month (1st and 15th) - "latest" is a
  moving target worth checking periodically, not a one-time pull.
- Vauzi's specific contribution is hardware-captured "magic register"
  values for Adreno 710/720/722 - chips upstream Mesa doesn't officially
  support. This same work is vendored into other maintained driver repos
  too (whitebelyash/AdrenoToolsDrivers, The412Banner/Banners-Turnip),
  cross-validating it rather than it being one isolated, unreviewed patch.
- Full current Adreno coverage, cross-checked across three actively
  maintained repos: 6xx = broad, mature, direct upstream Mesa support.
  7xx = broad upstream support generally, plus 710/720/722 specifically
  via vauzi's patches. 8xx = specifically 810/825/829/830/840, newer and
  rougher (whitebelyash's original A8xx patchset).
- Mesa branch note directly from vauzi's own README: "Mesa 24.3.4 tends to
  run lower FPS than Mesa 26.x on the same workloads. Try 26.x first unless
  you have a specific compatibility reason to use the legacy branch."
- **Practical gap**: I can identify all of this but can't download the
  actual updated binaries myself, same sandbox network limitation as
  before. Getting v2.8 (and ideally a mainline 6xx build and an 8xx build
  for broader coverage) into the project needs the user to supply them,
  same as the original 710/720/722 build.

**Minecraft's actual Vulkan requirement, re-checked**: still **1.2** with
dynamic rendering and push descriptors, re-confirmed directly against
Minecraft's own official 26.2 announcement and the Minecraft Wiki, both
current as of this check. Also found something genuinely useful for the
diagnostics system (5.6): Mojang's own client exposes structured error
codes for exactly this - `vulkan_device_version_too_low`, and
`backend_failure_missing_capabilities` paired with the specific missing
capability (`VULKAN_CORE_1_2`, `VK_KHR_dynamic_rendering`, etc). Parsing
for these specific strings is far more reliable than generic crash-log
guessing, and belongs directly in the diagnostics system.

## 13. LTW dropped, MobileGlues confirmed at latest version

Per explicit decision: keeping one well-maintained OpenGL-translation
option for the 1.17+ tier (MobileGlues) rather than two overlapping ones
(MobileGlues and LTW). LTW removed from the render path options and code.

MobileGlues' current latest release: **v1.3.5** (11 Jul 2026), maintained
by BZLZHH - the same person behind Krypton Wrapper (NG-GL4ES), interesting
cross-connection, not just coincidence that both show up together across
these launchers. Recent fixes worth knowing: v1.3.5 fixed incorrect Sodium
rendering from a GLSL Vulkan-definition bug and a texture subsystem issue,
and notes that Minecraft 26.3-snapshot-3 and later need "ignore shader/
program error" enabled to run at all. Also changed its own storage
approach from Storage Access Framework to MANAGE_EXTERNAL_STORAGE (broad
file access) for its own files - worth checking exactly what storage
access it actually needs when it's integrated, since that's a broader
permission than what this project's own instance/mod storage design uses
(deliberately app-private, per Phase 0's MojoLauncher scoped-storage
finding).

Also from MobileGlues' own release history: it has an internal ANGLE
toggle with a specific documented exception - "ANGLE is disabled on Adreno
730/740 or when Vulkan 1.2+ isn't available" even when the user's setting
requests it enabled. Another data point in the recurring pattern of 730/740
and similar newer 7xx chips needing special-cased handling across multiple
tools in this ecosystem (Turnip needing separate patches for some 7xx
chips, now MobileGlues doing its own chip-specific override too).
