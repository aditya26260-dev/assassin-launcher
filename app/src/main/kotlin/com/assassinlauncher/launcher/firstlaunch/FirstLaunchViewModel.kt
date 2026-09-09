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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FirstLaunchStep {
    data object DetectingHardware : FirstLaunchStep()
    data object CheckingVulkanSupport : FirstLaunchStep()
    data object ChoosingRenderPath : FirstLaunchStep()
    data object SavingProfile : FirstLaunchStep()
    data class Done(val profile: DeviceProfile, val decision: RenderPathDecision) :
        FirstLaunchStep()
}

private const val STEP_PACING_MS = 550L

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
            delay(STEP_PACING_MS)

            _step.value = FirstLaunchStep.CheckingVulkanSupport
            delay(STEP_PACING_MS)

            _step.value = FirstLaunchStep.ChoosingRenderPath
            val decision = RenderPathSelector.select(
                RenderPathRequest(
                    device = profile,
                    minecraftSupportsNativeVulkan = true,
                    vulkanToggleEnabled = true,
                    minecraftAtMost1_16_5 = false,
                    minecraftAtLeast1_17 = true,
                    turnipBuildAvailable = profile.turnipBuildAvailable
                )
            )
            delay(STEP_PACING_MS)

            _step.value = FirstLaunchStep.SavingProfile
            DeviceProfileStore.save(context, profile)
            delay(STEP_PACING_MS)

            _step.value = FirstLaunchStep.Done(profile, decision)
        }
    }
}
