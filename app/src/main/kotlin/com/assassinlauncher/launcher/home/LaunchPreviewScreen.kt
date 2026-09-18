package com.assassinlauncher.launcher.home

import androidx.compose.foundation.AndroidExternalSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.game.GameSessionService
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.launch.LaunchOutcome
import com.assassinlauncher.launcher.launch.LaunchStage
import net.kdt.pojavlaunch.utils.JREUtils

private fun stageLabel(stage: LaunchStage): String = when (stage) {
    LaunchStage.ResolvingAccount -> "Checking account..."
    LaunchStage.FetchingVersionManifest -> "Fetching version info..."
    LaunchStage.ProvisioningJvm -> "Preparing Java runtime..."
    LaunchStage.DownloadingLibraries -> "Downloading libraries..."
    LaunchStage.PreparingRenderer -> "Preparing renderer..."
    LaunchStage.StartingJvm -> "Starting Minecraft..."
}

/**
 * Hosts the actual game rendering surface. Once it exists, this screen
 * only stashes it on GameSessionService.pendingSurface and starts the
 * service - it does not preload libpojavexec.so or call
 * JREUtils.setupBridgeWindow itself. Both of those now happen exactly
 * once, from GameLaunchOrchestrator's own existing preload call site,
 * right after preloadPojavexecForAndroidVm(). An earlier version of
 * this screen called that same preload a second time, from a second
 * AndroidLwjglProvider instance, which re-extracted and re-mapped the
 * already-loaded library while it was still in use - that's what caused
 * a SIGSEGV inside JNI_OnLoad later, on the embedded JVM's own separate
 * load of the same library, not anything about the two-JVM handoff
 * itself. The embedded JVM's GLFW init reaches for a native window
 * immediately on boot, so the surface has to exist and be handed off
 * before JLI_Launch, not after - there was previously nothing anywhere
 * in this app that created a Surface at all, which is what originally
 * SIGSEGV'd inside ANativeWindow_acquire.
 *
 * A launch that actually succeeds ends this app's process when
 * Minecraft closes - see GameSessionService's own doc comment - so the
 * progress overlay only ever needs to cover "in progress" or a failure;
 * once the last known stage is reached it steps aside and whatever the
 * game draws into the surface shows directly, black until then.
 */
@Composable
fun LaunchPreviewScreen(
    profile: GameProfile,
    device: DeviceProfile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val outcome by GameSessionService.launchState.collectAsState()
    var hasStartedLaunch by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { GameSessionService.clearLaunchState() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidExternalSurface(modifier = Modifier.fillMaxSize()) {
            onSurface { surface, _, _ ->
                GameSessionService.pendingSurface = surface
                if (!hasStartedLaunch) {
                    hasStartedLaunch = true
                    GameSessionService.start(context, profile.id)
                }
                surface.onDestroyed {
                    GameSessionService.pendingSurface = null
                    JREUtils.releaseBridgeWindow()
                }
            }
        }

        val showOverlay = when (val current = outcome) {
            null -> true
            is LaunchOutcome.Progress -> current.stage != LaunchStage.StartingJvm
            is LaunchOutcome.Failed -> true
        }

        if (showOverlay) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.padding(48.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
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
        }
    }
}
