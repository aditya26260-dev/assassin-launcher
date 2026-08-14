package com.assassinlauncher.launcher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.input.CursorSettings
import com.assassinlauncher.launcher.input.CursorSettingsStore
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(device: DeviceProfile, onBack: () -> Unit) {
    var showingCredits by remember { mutableStateOf(false) }
    var cursorSettings by remember { mutableStateOf(CursorSettings()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        cursorSettings = CursorSettingsStore.load(context)
    }

    fun updateCursor(update: CursorSettings) {
        cursorSettings = update
        coroutineScope.launch { CursorSettingsStore.save(context, update) }
    }

    if (showingCredits) {
        CreditsScreen(onBack = { showingCredits = false })
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            SettingsSection(title = "Your device") {
                InfoRow("GPU", device.gpuRenderer)
                InfoRow("GPU family", device.gpuFamily.name)
                InfoRow(
                    "OpenGL ES",
                    if (device.glesVersionMajor > 0) {
                        "${device.glesVersionMajor}.${device.glesVersionMinor}"
                    } else {
                        "unknown"
                    }
                )
                InfoRow(
                    "Vulkan",
                    if (device.deepVulkanApiVersionMajor > 0) {
                        "${device.deepVulkanApiVersionMajor}.${device.deepVulkanApiVersionMinor}" +
                            if (device.meetsMinecraftVulkanFloor()) {
                                " (meets Minecraft's requirement)"
                            } else {
                                " (below Minecraft's requirement)"
                            }
                    } else {
                        "not available"
                    }
                )
                InfoRow("Android version", "API ${device.androidSdkInt}")
            }

            SettingsSection(title = "Cursor") {
                Text(
                    "Size: ${cursorSettings.sizeDp}dp",
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = cursorSettings.sizeDp.toFloat(),
                    onValueChange = { updateCursor(cursorSettings.copy(sizeDp = it.toInt())) },
                    valueRange = 12f..64f
                )

                Text(
                    "Sensitivity: ${"%.1f".format(cursorSettings.dpiSensitivity)}x",
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = cursorSettings.dpiSensitivity,
                    onValueChange = { updateCursor(cursorSettings.copy(dpiSensitivity = it)) },
                    valueRange = 0.25f..3f
                )

                Text("Color", color = MaterialTheme.colorScheme.onBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        0xFFFFFFFF, 0xFF000000, 0xFFD23C3C, 0xFF3C7CD2, 0xFF3CD26E, 0xFFE8C93C
                    )
                    presets.forEach { argb ->
                        val selected = cursorSettings.colorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    shape = CircleShape
                                )
                                .clickable { updateCursor(cursorSettings.copy(colorArgb = argb)) }
                        )
                    }
                }

                Text(
                    "Custom PNG cursors aren't supported yet - needs an image " +
                        "picker, which isn't built.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(title = "About") {
                Text(
                    "Assassin Launcher 0.1.0",
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Open source, GPL-3.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showingCredits = true },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Credits and licenses")
                }
            }

            Text(
                "Everything else the brief describes for this screen - a full " +
                    "renderer default, notification options, and the rest of what " +
                    "other launchers expose here - isn't built yet. Cursor size, " +
                    "color, and sensitivity above are real; custom PNG cursors " +
                    "still need an image picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Divider()
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun CreditsScreen(onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Credits and licenses",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Credits.entries.forEach { entry ->
                Column {
                    Text(entry.name, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        entry.license + (entry.note?.let { " - $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Text("Back")
            }
        }
    }
}
