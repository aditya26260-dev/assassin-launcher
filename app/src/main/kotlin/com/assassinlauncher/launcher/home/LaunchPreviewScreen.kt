package com.assassinlauncher.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.game.GameSessionService
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.launch.LaunchOutcome
import com.assassinlauncher.launcher.launch.LaunchStage

private fun stageLabel(stage: LaunchStage): String = when (stage) {
    LaunchStage.ResolvingAccount -> "Checking account..."
    LaunchStage.FetchingVersionManifest -> "Fetching version info..."
    LaunchStage.ProvisioningJvm -> "Preparing Java runtime..."
    LaunchStage.DownloadingLibraries -> "Downloading libraries..."
    LaunchStage.PreparingRenderer -> "Preparing renderer..."
    LaunchStage.StartingJvm -> "Starting Minecraft..."
}

/**
 * Starts GameSessionService (the real launch pipeline, as of this
 * session) and shows its live progress. A launch that actually succeeds
 * ends this app's process when Minecraft closes - see
 * GameSessionService's own doc comment - so in practice this screen only
 * ever shows "in progress" or a failure; there's no success state to
 * render because nothing is left running to render it.
 */
@Composable
fun LaunchPreviewScreen(
    profile: GameProfile,
    device: DeviceProfile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val outcome by GameSessionService.launchState.collectAsState()

    LaunchedEffect(profile.id) {
        GameSessionService.start(context, profile.id)
    }

    DisposableEffect(Unit) {
        onDispose { GameSessionService.clearLaunchState() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val current = outcome) {
                null -> {
                    CircularProgressIndicator()
                    Text(
                        text = "Starting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                is LaunchOutcome.Progress -> {
                    CircularProgressIndicator()
                    Text(
                        text = stageLabel(current.stage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                is LaunchOutcome.Failed -> {
                    Text(
                        text = "Couldn't launch",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
                    )
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            }
        }
    }
}
