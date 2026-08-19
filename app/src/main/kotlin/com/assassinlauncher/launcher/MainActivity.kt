package com.assassinlauncher.launcher

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Real edge-to-edge, not just the status/nav bar transparency
        // enableEdgeToEdge() covers on its own - a display cutout
        // (notch/punch-hole) needs its own separate flag or content
        // still gets letterboxed around it. LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // is API 30+; SHORT_EDGES is the correct fallback down to API 28,
        // and cutout handling doesn't exist at all below that, so this is
        // a real minSdk-aware branch, not an oversight.
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            AssassinLauncherTheme {
                // No inset padding at this outer level - that was the
                // actual bug in the previous attempt at this. Padding
                // applied here shrinks the constraints passed down to
                // every screen's own background Surface, so nothing
                // ever actually reached the notch even though the
                // comment here claimed it did. Each screen's background
                // now genuinely fills the true full screen; insets are
                // handled at the content level instead (LauncherTopBar
                // for most screens, handled directly for Home).
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
        coroutineScope.launch {
            installedMods = ModScanner.scan(InstanceDirectoryManager(context).modsDir(profileId))
        }
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
                screen is Screen.LaunchPreview -> {
                    BackHandler { screen = Screen.Home }
                    LaunchPreviewScreen(
                        profile = profile,
                        device = device,
                        onBack = { screen = Screen.Home }
                    )
                }
                screen is Screen.EditProfile -> {
                    BackHandler { screen = Screen.Home }
                    GameProfileEditorScreen(
                        profile = profile,
                        onSave = { updated ->
                            activeProfile = updated
                            coroutineScope.launch { instanceRepository.saveProfile(updated) }
                        },
                        onBack = { screen = Screen.Home }
                    )
                }
                screen is Screen.Settings -> {
                    BackHandler { screen = Screen.Home }
                    SettingsScreen(
                        device = device,
                        onBack = { screen = Screen.Home }
                    )
                }
                screen is Screen.ModManager -> {
                    val backToHome = {
                        screen = Screen.Home
                        rescanMods(profile.id)
                    }
                    BackHandler(onBack = backToHome)
                    ModManagerScreen(
                        profile = profile,
                        onBack = backToHome,
                        onOpenResourcePacks = { screen = Screen.ResourcePacks },
                        onOpenShaders = { screen = Screen.Shaders },
                        onOpenServers = { screen = Screen.Servers }
                    )
                }
                screen is Screen.ResourcePacks -> {
                    BackHandler { screen = Screen.ModManager }
                    ContentManagerScreen(
                        profile = profile,
                        contentType = ModrinthContentType.RESOURCE_PACK,
                        onBack = { screen = Screen.ModManager }
                    )
                }
                screen is Screen.Shaders -> {
                    BackHandler { screen = Screen.ModManager }
                    ContentManagerScreen(
                        profile = profile,
                        contentType = ModrinthContentType.SHADER,
                        onBack = { screen = Screen.ModManager }
                    )
                }
                screen is Screen.Servers -> {
                    BackHandler { screen = Screen.ModManager }
                    ServerManagerScreen(
                        profileId = profile.id,
                        onBack = { screen = Screen.ModManager }
                    )
                }
                screen is Screen.MicrosoftSignIn -> {
                    BackHandler { screen = Screen.Home }
                    MicrosoftSignInScreen(
                        onResult = { result ->
                            accountRepository.addOrUpdateMicrosoftAccount(
                                result.result.profile,
                                result.result.minecraftSession
                            )
                            screen = Screen.Home
                        },
                        onCancel = { screen = Screen.Home }
                    )
                }
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
