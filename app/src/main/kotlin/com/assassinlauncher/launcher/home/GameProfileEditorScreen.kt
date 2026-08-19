// ExposedDropdownMenuBox/ExposedDropdownMenuDefaults are still
// @ExperimentalMaterial3Api in this Compose BOM - real opt-in, not a
// suppressed warning, since there's no stable non-experimental
// equivalent yet for this specific component.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.assassinlauncher.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.hardware.ManualRendererOverride
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.ui.theme.launcherSwitchColors

private val availableJavaRuntimes = listOf("Auto", "Java 8", "Java 17", "Java 21", "Java 25")

@Composable
fun GameProfileEditorScreen(
    profile: GameProfile,
    onSave: (GameProfile) -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var loaderVersion by remember(profile.id) { mutableStateOf(profile.loaderVersion ?: "") }
    var advancedExpanded by remember { mutableStateOf(false) }
    var ramMb by remember(profile.id) { mutableStateOf(profile.ramAllocationMb ?: 2048) }
    var jvmArgs by remember(profile.id) { mutableStateOf(profile.jvmArgsOverride ?: "") }
    var javaRuntime by remember(profile.id) {
        mutableStateOf(profile.javaRuntimeOverride ?: "Auto")
    }
    var forceSystemDriver by remember(profile.id) {
        mutableStateOf(profile.forceSystemVulkanDriver)
    }
    var rendererOverride by remember(profile.id) {
        mutableStateOf(profile.manualRendererOverride)
    }

    fun currentProfile() = profile.copy(
        name = name,
        loaderVersion = loaderVersion.ifBlank { null },
        ramAllocationMb = ramMb,
        jvmArgsOverride = jvmArgs.ifBlank { null },
        javaRuntimeOverride = javaRuntime.takeIf { it != "Auto" },
        forceSystemVulkanDriver = forceSystemDriver,
        manualRendererOverride = rendererOverride
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit profile",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Icon editing (6.8) isn't built yet - a real image picker/crop
            // flow is its own piece of work, not something to fake here.
            Text(
                text = "Custom icons aren't supported yet - using the default",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = loaderVersion,
                onValueChange = { loaderVersion = it },
                label = { Text("Loader version") },
                placeholder = { Text("Leave blank for the default version") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = advancedExpanded,
                    onCheckedChange = { advancedExpanded = it },
                    colors = launcherSwitchColors()
                )
            }

            if (advancedExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "RAM allocation: $ramMb MB",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Slider(
                        value = ramMb.toFloat(),
                        onValueChange = { ramMb = it.toInt() },
                        valueRange = 512f..8192f,
                        steps = 14
                    )

                    OutlinedTextField(
                        value = jvmArgs,
                        onValueChange = { jvmArgs = it },
                        label = { Text("JVM arguments") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Renderer (ignored while Minecraft's native Vulkan " +
                            "backend is actually running)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RendererDropdown(
                        selected = rendererOverride,
                        onSelected = { rendererOverride = it }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                "Force system Vulkan driver",
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Off uses the bundled Turnip driver when the system " +
                                    "one falls short. Launch still won't fail if this " +
                                    "is on and the system driver doesn't work either.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = forceSystemDriver,
                            onCheckedChange = { forceSystemDriver = it },
                            colors = launcherSwitchColors()
                        )
                    }

                    JavaRuntimeDropdown(
                        selected = javaRuntime,
                        onSelected = { javaRuntime = it }
                    )

                    Text(
                        text = "Further experimental options aren't built yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    onSave(currentProfile())
                    onBack()
                }) {
                    Text("Save")
                }
                Button(onClick = onBack) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun RendererDropdown(
    selected: ManualRendererOverride?,
    onSelected: (ManualRendererOverride?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.name?.let { rendererDisplayName(it) } ?: "Auto"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Renderer") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Auto") }, onClick = {
                onSelected(null)
                expanded = false
            })
            ManualRendererOverride.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(rendererDisplayName(option.name)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun JavaRuntimeDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Java runtime") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableJavaRuntimes.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun rendererDisplayName(enumName: String): String = when (enumName) {
    "ZINK_OVER_TURNIP" -> "Zink over Turnip"
    "MOBILE_GLUES" -> "MobileGlues"
    "KRYPTON_WRAPPER" -> "Krypton Wrapper"
    "BASE_GL4ES" -> "GL4ES"
    else -> enumName
}
