package com.assassinlauncher.launcher.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assassinlauncher.launcher.jvm.MinecraftVersionClient
import com.assassinlauncher.launcher.jvm.MinecraftVersionSummary
import com.assassinlauncher.launcher.ui.theme.launcherSwitchColors

/**
 * Full-screen dialog rather than a dropdown or a second Screen entry -
 * Mojang's real manifest has hundreds of entries once snapshots are
 * included, which rules out a plain DropdownMenu (unusable without
 * search), and a dialog means GameProfileEditorScreen's own in-progress
 * field state (name, loader version, RAM slider, etc.) survives opening
 * and closing this rather than being torn down by top-level navigation.
 *
 * Fetches fresh every time it opens rather than caching - simplest
 * correct behavior for now; worth revisiting only if that turns out to
 * feel slow or wasteful in practice.
 */
@Composable
fun MinecraftVersionPickerDialog(
    currentVersion: String,
    onVersionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var summaries by remember { mutableStateOf<List<MinecraftVersionSummary>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSnapshots by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MinecraftVersionClient().fetchVersionManifest()
            .onSuccess { summaries = it }
            .onFailure { loadError = it.message ?: "Couldn't reach Mojang's version manifest" }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select version",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show snapshots", color = MaterialTheme.colorScheme.onBackground)
                    Switch(
                        checked = showSnapshots,
                        onCheckedChange = { showSnapshots = it },
                        colors = launcherSwitchColors()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    loadError != null -> Text(
                        text = "Couldn't load versions: $loadError",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    summaries == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> {
                        val filtered = summaries.orEmpty().filter { summary ->
                            (showSnapshots || summary.type == "release") &&
                                (searchQuery.isBlank() || summary.id.contains(searchQuery, ignoreCase = true))
                        }
                        if (filtered.isEmpty()) {
                            Text(
                                text = "No versions match \"$searchQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 24.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(filtered, key = { it.id }) { summary ->
                                    VersionRow(
                                        summary = summary,
                                        isSelected = summary.id == currentVersion,
                                        onClick = {
                                            onVersionSelected(summary.id)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(summary: MinecraftVersionSummary, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = summary.id,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
            if (summary.type != "release") {
                Text(
                    text = summary.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
