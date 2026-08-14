package com.assassinlauncher.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.mods.InstalledMod

@Composable
fun ModsQuickPanel(
    mods: List<InstalledMod>,
    onToggleMod: (InstalledMod, Boolean) -> Unit,
    onRefreshMod: (InstalledMod) -> Unit,
    onManageModsClick: () -> Unit,
    onUpdateAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onManageModsClick) {
                    Text("Manage Mods")
                }
                TextButton(onClick = onUpdateAllClick) {
                    Text("Update all")
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (mods.isEmpty()) {
                Text(
                    text = "No mods installed for this profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(mods, key = { it.id }) { mod ->
                        ModRow(
                            mod = mod,
                            onToggle = { enabled -> onToggleMod(mod, enabled) },
                            onRefresh = { onRefreshMod(mod) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModRow(
    mod: InstalledMod,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Real per-mod icons (extracted from the jar's own manifest, where
        // present) aren't built yet - a generic placeholder mark instead of
        // fabricating one.
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        ) {
            Text(
                text = mod.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            mod.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Check for update",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = mod.enabled, onCheckedChange = onToggle)
    }
}
