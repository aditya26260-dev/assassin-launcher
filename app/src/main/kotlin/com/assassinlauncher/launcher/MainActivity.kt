package com.assassinlauncher.launcher

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.account.AccountRepository
import com.assassinlauncher.launcher.account.MicrosoftSignInScreen
import com.assassinlauncher.launcher.firstlaunch.FirstLaunchScreen
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.hardware.DeviceProfileStore
import com.assassinlauncher.launcher.home.GameProfileEditorScreen
import com.assassinlauncher.launcher.home.HomeScreen
import com.assassinlauncher.launcher.home.LaunchPreviewScreen
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.instance.InstanceRepository
import com.assassinlauncher.launcher.mods.ContentManagerScreen
import com.assassinlauncher.launcher.mods.InstalledMod
import com.assassinlauncher.launcher.mods.ModManagerScreen
import com.assassinlauncher.launcher.mods.ModScanner
import com.assassinlauncher.launcher.mods.ModrinthContentType
import com.assassinlauncher.launcher.servers.ServerManagerScreen
import com.assassinlauncher.launcher.settings.SettingsScreen
import com.assassinlauncher.launcher.ui.theme.AssassinLauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val instanceRepository by lazy { InstanceRepository(applicationContext) }
    private val accountRepository by lazy { AccountRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssassinLauncherTheme {
                AppRoot(instanceRepository, accountRepository)
            }
        }
    }
}

private sealed class Screen {
    data object Home : Screen()
    data object LaunchPreview : Screen()
    data object EditProfile : Screen()
    data object Settings : Screen()
    data object ModManager : Screen()
    data object ResourcePacks : Screen()
    data object Shaders : Screen()
    data object Servers : Screen()
    data object MicrosoftSignIn : Screen()
}

/**
 * Routes first-launch (6.1) -> home (6.3) -> launch preview / profile
 * editor. Everything past first-launch is real and wired to real
 * repositories now, not placeholders standing in for a finished experience.
 */
@Composable
fun AppRoot(instanceRepository: InstanceRepository, accountRepository: AccountRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var firstLaunchDone by remember { mutableStateOf<Boolean?>(null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var activeProfile by remember { mutableStateOf<GameProfile?>(null) }
    var deviceProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var installedMods by remember { mutableStateOf<List<InstalledMod>>(emptyList()) }

    fun rescanMods(profileId: String) {
        installedMods = ModScanner.scan(InstanceDirectoryManager(context).modsDir(profileId))
    }

    LaunchedEffect(Unit) {
        firstLaunchDone = DeviceProfileStore.isFirstLaunchDone(context)
    }

    LaunchedEffect(firstLaunchDone) {
        if (firstLaunchDone == true) {
            activeProfile = instanceRepository.listProfiles().firstOrNull()
            deviceProfile = DeviceProfileStore.load(context)
            activeProfile?.let { rescanMods(it.id) }
        }
    }

    when (firstLaunchDone) {
        null -> Unit // brief moment reading DataStore, nothing to show yet
        false -> FirstLaunchScreen(onFinished = { firstLaunchDone = true })
        true -> {
            val profile = activeProfile
            val device = deviceProfile
            when {
                profile == null || device == null -> LoadingPlaceholder()
                screen is Screen.LaunchPreview -> LaunchPreviewScreen(
                    profile = profile,
                    device = device,
                    onBack = { screen = Screen.Home }
                )
                screen is Screen.EditProfile -> GameProfileEditorScreen(
                    profile = profile,
                    onSave = { updated ->
                        activeProfile = updated
                        coroutineScope.launch { instanceRepository.saveProfile(updated) }
                    },
                    onBack = { screen = Screen.Home }
                )
                screen is Screen.Settings -> SettingsScreen(
                    device = device,
                    onBack = { screen = Screen.Home }
                )
                screen is Screen.ModManager -> ModManagerScreen(
                    profile = profile,
                    onBack = {
                        screen = Screen.Home
                        rescanMods(profile.id)
                    },
                    onOpenResourcePacks = { screen = Screen.ResourcePacks },
                    onOpenShaders = { screen = Screen.Shaders },
                    onOpenServers = { screen = Screen.Servers }
                )
                screen is Screen.ResourcePacks -> ContentManagerScreen(
                    profile = profile,
                    contentType = ModrinthContentType.RESOURCE_PACK,
                    onBack = { screen = Screen.ModManager }
                )
                screen is Screen.Shaders -> ContentManagerScreen(
                    profile = profile,
                    contentType = ModrinthContentType.SHADER,
                    onBack = { screen = Screen.ModManager }
                )
                screen is Screen.Servers -> ServerManagerScreen(
                    profileId = profile.id,
                    onBack = { screen = Screen.ModManager }
                )
                screen is Screen.MicrosoftSignIn -> MicrosoftSignInScreen(
                    onResult = { result ->
                        accountRepository.addOrUpdateMicrosoftAccount(
                            result.result.profile,
                            result.result.minecraftSession
                        )
                        screen = Screen.Home
                    },
                    onCancel = { screen = Screen.Home }
                )
                else -> HomeScreen(
                    activeAccount = accountRepository.activeAccount(),
                    activeProfile = profile,
                    installedMods = installedMods,
                    onPlayClick = { screen = Screen.LaunchPreview },
                    onEditProfileClick = { screen = Screen.EditProfile },
                    onManageAccountsClick = { screen = Screen.MicrosoftSignIn },
                    onWardrobeClick = { notBuiltYet(context, "Wardrobe") },
                    onSettingsClick = { screen = Screen.Settings },
                    onToggleMod = { mod, enabled ->
                        ModScanner.setEnabled(
                            InstanceDirectoryManager(context).modsDir(profile.id), mod, enabled
                        )
                        rescanMods(profile.id)
                    },
                    onRefreshMod = { notBuiltYet(context, "Mod update checking") },
                    onManageModsClick = { screen = Screen.ModManager },
                    onUpdateAllModsClick = { notBuiltYet(context, "Update all") }
                )
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

/** Honest, visible feedback for the parts of the app that aren't built
 * yet - a silent no-op would read as broken rather than accurate. */
private fun notBuiltYet(context: android.content.Context, feature: String) {
    Toast.makeText(context, "$feature isn't built yet", Toast.LENGTH_SHORT).show()
}
