package com.assassinlauncher.launcher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.account.Account
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.mods.InstalledMod

@Composable
fun HomeScreen(
    activeAccount: Account?,
    activeProfile: GameProfile?,
    installedMods: List<InstalledMod>,
    onPlayClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    onWardrobeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleMod: (InstalledMod, Boolean) -> Unit,
    onRefreshMod: (InstalledMod) -> Unit,
    onManageModsClick: () -> Unit,
    onUpdateAllModsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(32.dp)) {

                // Top-left: account header (manage accounts + wardrobe +
                // who's signed in). Real signed-out state below, not a
                // stand-in for one - Microsoft sign-in isn't wired up yet,
                // so this is what an actual first-time user sees too.
                Column(
                    modifier = Modifier.align(Alignment.TopStart),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onManageAccountsClick) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = "Manage accounts",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = onWardrobeClick) {
                            Icon(
                                imageVector = Icons.Filled.Checkroom,
                                contentDescription = "Wardrobe",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    SkinAndUsername(activeAccount)
                }

                // Top-right: settings.
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Lower-left-center: play button plus the profile edit icon
                // beside it.
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = onPlayClick,
                        shape = CircleShape,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onEditProfileClick,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = activeProfile?.name ?: "No profile",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            ModsQuickPanel(
                mods = installedMods,
                onToggleMod = onToggleMod,
                onRefreshMod = onRefreshMod,
                onManageModsClick = onManageModsClick,
                onUpdateAllClick = onUpdateAllModsClick
            )
        }
    }
}

/**
 * The real 3D skin render (touch/mouse-driven rotation, per 6.3) isn't
 * built yet - that's its own significant piece of work, not something to
 * fake here. This shows the actual current state honestly: nothing to
 * render without a signed-in account yet.
 */
@Composable
private fun SkinAndUsername(activeAccount: Account?) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = activeAccount?.username ?: "Not signed in",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (activeAccount == null) {
            Text(
                text = "Sign in to see your skin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
