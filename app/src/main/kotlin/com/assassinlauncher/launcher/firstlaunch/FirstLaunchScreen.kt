package com.assassinlauncher.launcher.firstlaunch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assassinlauncher.launcher.hardware.RenderPath

@Composable
fun FirstLaunchScreen(
    onFinished: () -> Unit,
    viewModel: FirstLaunchViewModel = viewModel()
) {
    val step by viewModel.step.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Optimizing for your device",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            AnimatedContent(
                targetState = step,
                label = "first-launch-step"
            ) { currentStep ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when (currentStep) {
                        is FirstLaunchStep.DetectingHardware -> {
                            StepIndicator("Reading your GPU and Android version")
                        }
                        is FirstLaunchStep.CheckingVulkanSupport -> {
                            StepIndicator("Checking Vulkan driver support")
                        }
                        is FirstLaunchStep.ChoosingRenderPath -> {
                            StepIndicator("Choosing the right renderer for your hardware")
                        }
                        is FirstLaunchStep.Done -> {
                            ResultSummary(
                                renderPath = currentStep.decision.path,
                                gpuRenderer = currentStep.profile.gpuRenderer,
                                onContinue = onFinished
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(top = 32.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ResultSummary(
    renderPath: RenderPath,
    gpuRenderer: String,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 32.dp).width(480.dp)
    ) {
        Text(
            text = gpuRenderer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = renderPathLabel(renderPath),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = renderPathReason(renderPath),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onContinue) {
            Text("Continue")
        }
    }
}

private fun renderPathLabel(path: RenderPath): String = when (path) {
    is RenderPath.SystemVulkan -> "Using your device's Vulkan driver"
    is RenderPath.VulkanViaTurnip -> "Using the Turnip Vulkan driver"
    is RenderPath.ZinkOverTurnip -> "Using OpenGL via Zink, over Turnip"
    is RenderPath.VulkanViaPanfrost -> "Using the Panfrost Vulkan driver (experimental)"
    is RenderPath.MobileGlues -> "Using MobileGlues"
    is RenderPath.KryptonWrapper -> "Using Krypton Wrapper"
    is RenderPath.BaseGl4es -> "Using GL4ES"
}

private fun renderPathReason(path: RenderPath): String = when (path) {
    is RenderPath.SystemVulkan -> path.reason
    is RenderPath.VulkanViaTurnip -> path.reason
    is RenderPath.ZinkOverTurnip -> path.reason
    is RenderPath.VulkanViaPanfrost -> path.reason
    is RenderPath.MobileGlues -> path.reason
    is RenderPath.KryptonWrapper -> path.reason
    is RenderPath.BaseGl4es -> path.reason
}
