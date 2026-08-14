package com.assassinlauncher.launcher.hardware

/** The renderer/driver combination actually chosen for a launch. */
sealed class RenderPath {
    data class SystemVulkan(val reason: String) : RenderPath()
    data class VulkanViaTurnip(val reason: String) : RenderPath()
    data class ZinkOverTurnip(val reason: String) : RenderPath()
    data class VulkanViaPanfrost(val reason: String) : RenderPath()
    data class MobileGlues(val reason: String) : RenderPath()
    data class KryptonWrapper(val reason: String) : RenderPath()
    data class BaseGl4es(val reason: String) : RenderPath()
}

/** The OpenGL-path renderer choices a user can manually pick in the
 * advanced settings (6.8) - null in a GameProfile means "let
 * RenderPathSelector decide automatically" instead of one of these. */
enum class ManualRendererOverride {
    ZINK_OVER_TURNIP,
    MOBILE_GLUES,
    KRYPTON_WRAPPER,
    BASE_GL4ES
}

/**
 * Whether a choice should be applied silently (with a toast explaining
 * what changed) or needs to actually ask the user first - the same
 * silent-fix-vs-prompt line drawn throughout architecture 5.3 and 5.6.
 */
data class RenderPathDecision(
    val path: RenderPath,
    val needsUserPrompt: Boolean,
    val toastMessage: String?
)

/** Per-instance inputs the decision needs, on top of the device profile. */
data class RenderPathRequest(
    val device: DeviceProfile,
    val minecraftSupportsNativeVulkan: Boolean, // true for 26.2+
    val vulkanToggleEnabled: Boolean,            // per-instance user setting
    val minecraftAtMost1_16_5: Boolean,          // the confirmed Mali+Zink exception
    val minecraftAtLeast1_17: Boolean,           // MobileGlues/LTW's shared version floor
    val turnipBuildAvailable: Boolean,           // a matching bundled build exists and loaded
    /** 6.8's "system Vulkan driver vs. bundled Turnip fallback" toggle. When
     * true, Turnip is never used even if the system driver falls short -
     * the launch still has to succeed, so that case falls through to the
     * OpenGL path below instead of failing outright. */
    val forceSystemVulkanDriver: Boolean = false,
    /** 6.8's manual renderer selector. Only applies on the OpenGL path -
     * ignored while native Vulkan is actually in use, per 6.8's own note. */
    val manualRendererOverride: ManualRendererOverride? = null
)

/**
 * Implements architecture doc 5.3's decision tree exactly. Kept as pure
 * logic with no Android/JNI calls of its own, so it can be reasoned about
 * (and eventually unit tested) independent of whether the native driver
 * loading underneath it actually works yet.
 */
object RenderPathSelector {

    fun select(request: RenderPathRequest): RenderPathDecision {
        if (request.minecraftSupportsNativeVulkan && request.vulkanToggleEnabled) {
            selectVulkanPath(request)?.let { return it }
            // No viable Vulkan path (or the user forced system-driver-only
            // and it doesn't meet the floor) - fall through to OpenGL
            // instead of failing to launch, with a toast explaining why.
            return openGlPath(request, explainedFallbackFromVulkan = true)
        }

        return openGlPath(request, explainedFallbackFromVulkan = false)
    }

    /** Returns null when there's no viable Vulkan path, so the caller falls
     * through to the OpenGL tree - that fallthrough is what actually
     * satisfies "launch must not fail even if forced to system driver". */
    private fun selectVulkanPath(request: RenderPathRequest): RenderPathDecision? {
        val device = request.device

        if (device.meetsMinecraftVulkanFloor()) {
            return RenderPathDecision(
                path = RenderPath.SystemVulkan(
                    "System driver already meets Minecraft's Vulkan requirement"
                ),
                needsUserPrompt = false,
                toastMessage = null
            )
        }

        // System driver falls short. Forcing system-driver-only means
        // Turnip is off the table by the user's own choice - not a failure,
        // just nothing left to try on the Vulkan side.
        if (request.forceSystemVulkanDriver) return null

        if (device.gpuFamily.isAdreno() && device.supportsCustomDriverLoading() &&
            request.turnipBuildAvailable
        ) {
            return RenderPathDecision(
                path = RenderPath.VulkanViaTurnip(
                    "System Vulkan driver doesn't meet Minecraft's requirement"
                ),
                needsUserPrompt = false,
                toastMessage = "Switched to Turnip driver: system Vulkan driver " +
                    "doesn't meet Minecraft's requirement"
            )
        }

        if (device.gpuFamily == GpuFamily.MALI) {
            // Panfrost/PanVK is a real tradeoff, not a clearly-safe auto-fix
            // (Phase 0: only reliably conformant on Mali-G610) - prompt,
            // never switch silently.
            return RenderPathDecision(
                path = RenderPath.VulkanViaPanfrost(
                    "Mali Vulkan fallback is experimental - your call"
                ),
                needsUserPrompt = true,
                toastMessage = null
            )
        }

        return null
    }

    private fun openGlPath(
        request: RenderPathRequest,
        explainedFallbackFromVulkan: Boolean
    ): RenderPathDecision {
        val device = request.device
        val fallbackToast = if (explainedFallbackFromVulkan) {
            "No working Vulkan path on this device - using OpenGL instead"
        } else {
            null
        }

        // Hard exception confirmed in Phase 0: Mali + Minecraft <=1.16.5 must
        // never attempt Zink, unfixed upstream driver bug. A manual override
        // asking for Zink here still gets refused for the same reason -
        // this isn't a preference to honor, it's a driver bug that crashes.
        val mayAttemptZink = !(device.gpuFamily == GpuFamily.MALI && request.minecraftAtMost1_16_5)
        val zinkViable = mayAttemptZink && device.gpuFamily.isAdreno() &&
            device.supportsCustomDriverLoading() && request.turnipBuildAvailable

        // MobileGlues shares this 1.17+ floor (Phase 0 research, round two)
        // on top of its own GLES floor - below 1.17 it's not an option at
        // all, not just de-prioritized. (LTW was considered as a second
        // option in the same tier but dropped per an explicit call to keep
        // just one well-maintained choice here rather than two overlapping
        // ones - MobileGlues, kept at its current latest version.)
        val mobileGluesViable = request.minecraftAtLeast1_17 && device.meetsMobileGluesFloor()

        // Manual override, when it's actually usable on this device -
        // an override the hardware/version can't back is not honored
        // silently, it falls through to the automatic choice below instead.
        when (request.manualRendererOverride) {
            // Zink is deliberately manual-override-only for now: real user
            // testing found it performs poorly despite reaching real OpenGL
            // 4.5 on paper (Phase 0, round two) - not defaulted to until
            // that's validated across more devices.
            ManualRendererOverride.ZINK_OVER_TURNIP -> if (zinkViable) {
                return RenderPathDecision(
                    path = RenderPath.ZinkOverTurnip("Manually selected"),
                    needsUserPrompt = false,
                    toastMessage = fallbackToast
                )
            }
            ManualRendererOverride.MOBILE_GLUES -> if (mobileGluesViable) {
                return RenderPathDecision(
                    path = RenderPath.MobileGlues("Manually selected"),
                    needsUserPrompt = false,
                    toastMessage = fallbackToast
                )
            }
            ManualRendererOverride.KRYPTON_WRAPPER -> if (device.kryptonWrapperLoadable) {
                return RenderPathDecision(
                    path = RenderPath.KryptonWrapper("Manually selected"),
                    needsUserPrompt = false,
                    toastMessage = fallbackToast
                )
            }
            ManualRendererOverride.BASE_GL4ES -> return RenderPathDecision(
                path = RenderPath.BaseGl4es("Manually selected"),
                needsUserPrompt = false,
                toastMessage = fallbackToast
            )
            null -> Unit
        }

        if (mobileGluesViable) {
            return RenderPathDecision(
                path = RenderPath.MobileGlues(
                    "Broadest real-world adoption among the 1.17+ options " +
                        "(Phase 0) - not a claimed performance win, just the " +
                        "most validated default until real device testing says otherwise"
                ),
                needsUserPrompt = false,
                toastMessage = fallbackToast
            )
        }

        // Below MobileGlues' GLES floor, or below Minecraft 1.17 entirely -
        // Krypton Wrapper (NG-GL4ES) is the more capable GL4ES-family fork,
        // if it actually loads on this device - base GL4ES/HolyGL4ES is the
        // true last resort otherwise, for the weakest/oldest hardware (Phase 0).
        val reason = if (!request.minecraftAtLeast1_17) {
            "Below Minecraft 1.17 - MobileGlues isn't an option at all here"
        } else {
            "Device is below MobileGlues' GLES 3.0 floor"
        }
        if (device.kryptonWrapperLoadable) {
            return RenderPathDecision(
                path = RenderPath.KryptonWrapper(reason),
                needsUserPrompt = false,
                toastMessage = fallbackToast
            )
        }
        return RenderPathDecision(
            path = RenderPath.BaseGl4es("$reason, and Krypton Wrapper didn't load either"),
            needsUserPrompt = false,
            toastMessage = fallbackToast
        )
    }
}
