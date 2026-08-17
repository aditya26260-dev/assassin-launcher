package com.assassinlauncher.launcher.mods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.mods.remote.ModrinthHit
import com.assassinlauncher.launcher.ui.theme.LauncherTopBar

@Composable
fun ContentManagerScreen(
    profile: GameProfile,
    contentType: ModrinthContentType,
    onBack: () -> Unit,
    viewModel: ContentManagerViewModel = viewModel()
) {
    var installed by remember(profile.id, contentType) {
        mutableStateOf<List<InstalledContent>>(emptyList())
    }
    val context = LocalContext.current
    val title = if (contentType == ModrinthContentType.RESOURCE_PACK) {
        "Resource Packs"
    } else {
        "Shaders"
    }

    LaunchedEffect(profile.id, contentType) {
        val dir = when (contentType) {
            ModrinthContentType.RESOURCE_PACK ->
                InstanceDirectoryManager(context).resourcePacksDir(profile.id)
            ModrinthContentType.SHADER ->
                InstanceDirectoryManager(context).shaderPacksDir(profile.id)
        }
        installed = ContentScanner.scan(dir)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            LauncherTopBar(title = title, onBack = onBack)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    Text(
                        "Installed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (installed.isEmpty()) {
                        Text(
                            "None installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        LazyColumn {
                            items(installed, key = { it.fileName }) { item ->
                                Text(
                                    item.displayName,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                ContentInstallerPanel(
                    profile = profile,
                    contentType = contentType,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ContentInstallerPanel(
    profile: GameProfile,
    contentType: ModrinthContentType,
    viewModel: ContentManagerViewModel,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsState()
    val installStatus by viewModel.installStatus.collectAsState()

    LaunchedEffect(profile.id, contentType) {
        viewModel.search("", profile, contentType)
    }

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { viewModel.search(query, profile, contentType) }
            )
        )
        Button(
            onClick = { viewModel.search(query, profile, contentType) },
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
                    ContentHitRow(
                        hit = hit,
                        onInstall = { viewModel.install(hit, profile, contentType) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentHitRow(hit: ModrinthHit, onInstall: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = hit.iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
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
