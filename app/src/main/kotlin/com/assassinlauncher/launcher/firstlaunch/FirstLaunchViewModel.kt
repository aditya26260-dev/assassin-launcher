package com.assassinlauncher.launcher.firstlaunch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.hardware.DeviceProfileStore
import com.assassinlauncher.launcher.hardware.DeviceProfiler
import com.assassinlauncher.launcher.hardware.RenderPathDecision
import com.assassinlauncher.launcher.hardware.RenderPathRequest
import com.assassinlauncher.launcher.hardware.RenderPathSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real steps, not a generic spinner - architecture 6.1 asks for "one
 * satisfying, honest progress flow, not a black box", so this tracks what's
 * actually happening rather than faking a progress percentage.
 */
sealed class FirstLaunchStep {
    data object DetectingHardware : FirstLaunchStep()
    data object CheckingVulkanSupport : FirstLaunchStep()
    data object ChoosingRenderPath : FirstLaunchStep()
    data class Done(val profile: DeviceProfile, val decision: RenderPathDecision) :
        FirstLaunchStep()
}

class FirstLaunchViewModel(application: Application) : AndroidViewModel(application) {

    private val _step = MutableStateFlow<FirstLaunchStep>(FirstLaunchStep.DetectingHardware)
    val step: StateFlow<FirstLaunchStep> = _step.asStateFlow()

    init {
        runDetection()
    }

    private fun runDetection() {
        viewModelScope.launch {
            val context = getApplication<Application>()

            _step.value = FirstLaunchStep.DetectingHardware
            val profile = DeviceProfiler.profile(context)

            _step.value = FirstLaunchStep.CheckingVulkanSupport
            // The Vulkan check already happened as part of profile() above -
            // this step exists so the UI can show it as its own honest beat
            // rather than folding two real steps into one line.

            _step.value = FirstLaunchStep.ChoosingRenderPath
            val decision = RenderPathSelector.select(
                RenderPathRequest(
                    device = profile,
                    // First-launch detection isn't tied to a specific
                    // instance yet, so this uses the most common case
                    // (modern Minecraft, Vulkan on) to give a real answer
                    // to show here - actual per-instance decisions happen
                    // again at launch time with that instance's real context.
                    minecraftSupportsNativeVulkan = true,
                    vulkanToggleEnabled = true,
                    minecraftAtMost1_16_5 = false,
                    minecraftAtLeast1_17 = true,
                    turnipBuildAvailable = profile.turnipBuildAvailable
                )
            )

            DeviceProfileStore.save(context, profile)
            _step.value = FirstLaunchStep.Done(profile, decision)
        }
    }
}
