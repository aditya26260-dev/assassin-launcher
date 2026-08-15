package com.assassinlauncher.launcher.mods

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.instance.ModLoader
import com.assassinlauncher.launcher.mods.remote.ModrinthHit

@Composable
fun ModManagerScreen(
    profile: GameProfile,
    onBack: () -> Unit,
    onOpenResourcePacks: () -> Unit,
    onOpenShaders: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: ModManagerViewModel = viewModel()
) {
    var installedMods by remember(profile.id) { mutableStateOf<List<InstalledMod>>(emptyList()) }
    var selectedHitForDetail by remember { mutableStateOf<ModrinthHit?>(null) }
    val context = LocalContext.current

    fun rescanInstalled() {
        installedMods = ModScanner.scan(InstanceDirectoryManager(context).modsDir(profile.id))
    }

    LaunchedEffect(profile.id) {
        rescanInstalled()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .let { if (selectedHitForDetail != null) it.blur(16.dp) else it },
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mod Manager",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // Profile context per 6.5 - shown here at the top
                        // instead of bottom-left, same information either way.
                        Text(
                            text = "${profile.name} - ${profile.minecraftVersion} - ${profile.loader.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onOpenResourcePacks) { Text("Resource Packs") }
                    Button(onClick = onOpenShaders) { Text("Shaders") }
                    Button(onClick = onOpenServers) { Text("Servers") }
                    Button(onClick = onBack) { Text("Close") }
                }
                Divider()

                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)
                    ) {
                        Text(
                            "Installed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (installedMods.isEmpty()) {
                            Text(
                                "No mods installed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            LazyColumn {
                                items(installedMods, key = { it.id }) { mod ->
                                    Text(
                                        mod.displayName,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Vanilla and OptiFine profiles show no installer content -
                    // Modrinth doesn't host mods for either, so there'd be
                    // nothing real to search for regardless of UI effort
                    // spent here.
                    if (profile.loader == ModLoader.VANILLA || profile.loader == ModLoader.OPTIFINE) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${profile.loader.name.lowercase()} profiles don't support mods",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        ModInstallerPanel(
                            profile = profile,
                            viewModel = viewModel,
                            onModCardClick = { selectedHitForDetail = it },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        selectedHitForDetail?.let { hit ->
            ModDetailModal(
                hit = hit,
                profile = profile,
                onInstall = { clickedHit, version ->
                    viewModel.installVersion(clickedHit, version, profile)
                    selectedHitForDetail = null
                },
                onClose = { selectedHitForDetail = null }
            )
        }
    }
}

@Composable
private fun ModInstallerPanel(
    profile: GameProfile,
    viewModel: ModManagerViewModel,
    onModCardClick: (ModrinthHit) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsState()
    val installStatus by viewModel.installStatus.collectAsState()

    LaunchedEffect(profile.id) {
        // Empty query -> Modrinth's default popularity-sorted listing,
        // matching 6.5's "default view shows the most popular mods".
        viewModel.search("", profile)
    }

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search mods") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { viewModel.search(query, profile) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Search")
        }

        installStatus?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        when (val state = searchState) {
            is SearchState.Idle -> Unit
            is SearchState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            is SearchState.Failed -> Text(
                text = "Search failed: ${state.message}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
            is SearchState.Loaded -> LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(state.hits, key = { it.projectId }) { hit ->
                    ModHitRow(
                        hit = hit,
                        onCardClick = { onModCardClick(hit) },
                        onInstall = { viewModel.install(hit, profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModHitRow(hit: ModrinthHit, onCardClick: () -> Unit, onInstall: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onCardClick)
        ) {
            Text(
                text = hit.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = hit.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Button(onClick = onInstall) { Text("Install") }
    }
}
