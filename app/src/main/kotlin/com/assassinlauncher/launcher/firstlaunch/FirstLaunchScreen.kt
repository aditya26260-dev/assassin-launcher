package com.assassinlauncher.launcher.firstlaunch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assassinlauncher.launcher.hardware.RenderPath

/**
 * A checklist, not a single spinner with changing developer-reasoning
 * text underneath it - the previous version surfaced RenderPathSelector's
 * internal justification strings (written for future debugging, not for
 * a first-time user) directly in the UI. Every step shown here is real,
 * matching FirstLaunchViewModel's actual sequence, not decorative.
 */
private enum class ChecklistStep(val label: String) {
    HARDWARE("Reading device info"),
    VULKAN("Checking driver support"),
    RENDERER("Choosing the best renderer"),
    READY("Preparing your setup")
}

@Composable
fun FirstLaunchScreen(
    onFinished: () -> Unit,
    viewModel: FirstLaunchViewModel = viewModel()
) {
    val step by viewModel.step.collectAsState()
    val completedCount = when (step) {
        is FirstLaunchStep.DetectingHardware -> 0
        is FirstLaunchStep.CheckingVulkanSupport -> 1
        is FirstLaunchStep.ChoosingRenderPath -> 2
        is FirstLaunchStep.Done -> 4
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Setting things up",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(40.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ChecklistStep.entries.forEachIndexed { index, entry ->
                    ChecklistRow(
                        label = entry.label,
                        state = when {
                            index < completedCount -> RowState.Done
                            index == completedCount -> RowState.InProgress
                            else -> RowState.Pending
                        }
                    )
                }
            }

            val doneStep = step as? FirstLaunchStep.Done
            AnimatedVisibility(visible = doneStep != null, enter = fadeIn()) {
                if (doneStep != null) {
                    ResultSummary(renderPath = doneStep.decision.path, onContinue = onFinished)
                }
            }
        }
    }
}

private enum class RowState { Done, InProgress, Pending }

@Composable
private fun ChecklistRow(label: String, state: RowState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(340.dp)) {
        Row(modifier = Modifier.size(24.dp), horizontalArrangement = Arrangement.Center) {
            when (state) {
                RowState.Done -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
                RowState.InProgress -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                RowState.Pending -> Unit
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (state == RowState.Pending) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            }
        )
    }
}

@Composable
private fun ResultSummary(renderPath: RenderPath, onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 32.dp).width(400.dp)
    ) {
        Text(
            text = renderPathLabel(renderPath),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "You're ready to go. This can be changed later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "For the best experience, connecting a keyboard and mouse " +
                    "is recommended - Minecraft plays closer to its PC self that way.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(onClick = onContinue, modifier = Modifier.padding(top = 20.dp)) {
            Text("Continue")
        }
    }
}

private fun renderPathLabel(path: RenderPath): String = when (path) {
    is RenderPath.SystemVulkan -> "Using your device's built-in Vulkan driver"
    is RenderPath.VulkanViaTurnip -> "Using the Turnip Vulkan driver"
    is RenderPath.ZinkOverTurnip -> "Using OpenGL via Zink"
    is RenderPath.VulkanViaPanfrost -> "Using the Panfrost Vulkan driver"
    is RenderPath.MobileGlues -> "Using MobileGlues for rendering"
    is RenderPath.KryptonWrapper -> "Using Krypton Wrapper for rendering"
    is RenderPath.BaseGl4es -> "Using the compatibility renderer"
}
