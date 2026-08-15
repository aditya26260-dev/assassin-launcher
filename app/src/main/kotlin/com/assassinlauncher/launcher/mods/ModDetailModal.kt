// ExposedDropdownMenuBox/ExposedDropdownMenuDefaults are still
// @ExperimentalMaterial3Api in this Compose BOM - real opt-in, not a
// suppressed warning, since there's no stable non-experimental
// equivalent yet for this specific component.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.assassinlauncher.launcher.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.mods.remote.ModrinthApiClient
import com.assassinlauncher.launcher.mods.remote.ModrinthHit
import com.assassinlauncher.launcher.mods.remote.ModrinthProject
import com.assassinlauncher.launcher.mods.remote.ModrinthVersion

@Composable
fun ModDetailModal(
    hit: ModrinthHit,
    profile: GameProfile,
    onInstall: (ModrinthHit, ModrinthVersion?) -> Unit,
    onClose: () -> Unit
) {
    val client = remember { ModrinthApiClient() }
    var project by remember(hit.projectId) { mutableStateOf<ModrinthProject?>(null) }
    var versions by remember(hit.projectId) { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var selectedVersion by remember(hit.projectId) { mutableStateOf<ModrinthVersion?>(null) }
    var loading by remember(hit.projectId) { mutableStateOf(true) }

    LaunchedEffect(hit.projectId) {
        loading = true
        project = client.getProject(hit.projectId).getOrNull()
        versions = client.getVersions(hit.projectId, profile.minecraftVersion).getOrDefault(emptyList())
        selectedVersion = versions.firstOrNull()
        loading = false
    }

    // Scrim over whatever's behind, with the actual background blur applied
    // by the caller to its own content - a Dialog window can't blur content
    // it doesn't own, so this is designed to be composed inside the same
    // tree as what it's covering, not as a system Dialog.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.width(560.dp).padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = hit.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "by ${hit.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else {
                    Text(
                        text = project?.body?.takeIf { it.isNotBlank() } ?: hit.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    if (versions.isNotEmpty()) {
                        VersionDropdown(
                            versions = versions,
                            selected = selectedVersion,
                            onSelected = { selectedVersion = it },
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    Button(
                        onClick = { onInstall(hit, selectedVersion) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(if (selectedVersion != null) "Install" else "Install latest")
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionDropdown(
    versions: List<ModrinthVersion>,
    selected: ModrinthVersion?,
    onSelected: (ModrinthVersion) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.versionNumber} (${it.loaders.joinToString()})" }
                ?: "Select a version",
            onValueChange = {},
            readOnly = true,
            label = { Text("Version") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            versions.forEach { version ->
                DropdownMenuItem(
                    text = { Text("${version.versionNumber} (${version.loaders.joinToString()})") },
                    onClick = {
                        onSelected(version)
                        expanded = false
                    }
                )
            }
        }
    }
}
