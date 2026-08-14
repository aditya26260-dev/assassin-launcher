package com.assassinlauncher.launcher.launch

import android.content.Context
import android.system.Os
import com.assassinlauncher.launcher.account.AccountRepository
import com.assassinlauncher.launcher.hardware.DeviceProfile
import com.assassinlauncher.launcher.hardware.KryptonWrapperManager
import com.assassinlauncher.launcher.hardware.MobileGluesManager
import com.assassinlauncher.launcher.hardware.RenderPath
import com.assassinlauncher.launcher.hardware.TurnipDriverManager
import com.assassinlauncher.launcher.hardware.TurnipDriverVariant
import com.assassinlauncher.launcher.instance.GameProfile
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import com.assassinlauncher.launcher.instance.ModLoader
import com.assassinlauncher.launcher.jvm.AndroidLwjglProvider
import com.assassinlauncher.launcher.jvm.JavaRuntimeVersion
import com.assassinlauncher.launcher.jvm.JvmRuntimeManager
import com.assassinlauncher.launcher.jvm.LibraryDownloader
import com.assassinlauncher.launcher.jvm.MinecraftVersionClient
import com.assassinlauncher.launcher.jvm.MinecraftVersionDetails
import com.assassinlauncher.launcher.nativebridge.NativeBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

sealed class LaunchStage {
    data object ResolvingAccount : LaunchStage()
    data object FetchingVersionManifest : LaunchStage()
    data object ProvisioningJvm : LaunchStage()
    data object DownloadingLibraries : LaunchStage()
    data object PreparingRenderer : LaunchStage()
    data object StartingJvm : LaunchStage()
}

sealed class LaunchOutcome {
    data class Progress(val stage: LaunchStage) : LaunchOutcome()
    data class Failed(val stage: LaunchStage, val message: String) : LaunchOutcome()
    // Deliberately no Success case - see launchEmbeddedJvm's own doc
    // comment. A launch that actually succeeds doesn't return control to
    // this flow at all; the process ends when Minecraft does. Reaching
    // LaunchStage.StartingJvm's Progress emission with no Failed after it
    // is, in practice, the closest thing to a success signal this can
    // give a caller before control leaves Kotlin entirely.
}

/**
 * Real classpath construction and process spawning - the piece
 * docs/PROGRESS.md's "Next action" pointed at. Ties together JVM
 * provisioning, version parsing, library downloading, the render path
 * decision, and accounts, none of which talked to each other before this.
 *
 * Two real, honestly-scoped gaps this does NOT cover, both noted in
 * docs/PROGRESS.md rather than silently glossed over:
 *  - No asset (textures/sounds/lang) downloading yet - Minecraft will
 *    start with an empty assets directory. Not addressed here; a real,
 *    separate piece of work, not a "next action" for the JVM/classpath
 *    problem this class exists to solve.
 *  - The Android LWJGL native .so files (GLFW replacement, OpenAL) aren't
 *    sourced yet - only the Java-side jars are. The renderer-loading and
 *    JVM library dlopen sequence below is written to be correct once
 *    they exist, and fails gracefully (not silently) without them.
 *
 * Modloader (Fabric/Forge/etc.) version-JSON inheritance/merging isn't
 * implemented either - this only launches GameProfile.loader ==
 * ModLoader.VANILLA for now, and says so rather than attempting a broken
 * modded launch.
 */
class GameLaunchOrchestrator(private val context: Context) {

    private val instanceDirs = InstanceDirectoryManager(context)
    private val jvmRuntimeManager = JvmRuntimeManager(context)
    private val libraryDownloader = LibraryDownloader(instanceDirs.librariesDir, instanceDirs.versionsDir)
    private val versionClient = MinecraftVersionClient()
    private val lwjglProvider = AndroidLwjglProvider(context)
    private val accountRepository = AccountRepository(context)
    private val turnipDriverManager = TurnipDriverManager(context)
    private val mobileGluesManager = MobileGluesManager(context)
    private val kryptonWrapperManager = KryptonWrapperManager(context)

    fun launch(profile: GameProfile, device: DeviceProfile, renderPath: RenderPath): Flow<LaunchOutcome> = flow {

        if (profile.loader != ModLoader.VANILLA) {
            emit(LaunchOutcome.Failed(
                LaunchStage.FetchingVersionManifest,
                "${profile.loader} isn't supported yet - only vanilla launches right now"
            ))
            return@flow
        }

        emit(LaunchOutcome.Progress(LaunchStage.ResolvingAccount))
        val account = accountRepository.activeAccount()
            ?: return@flow emit(LaunchOutcome.Failed(LaunchStage.ResolvingAccount, "No account signed in"))
        val session = accountRepository.sessionFor(account.id)
            ?: return@flow emit(
                LaunchOutcome.Failed(LaunchStage.ResolvingAccount, "Session expired - sign in again")
            )

        emit(LaunchOutcome.Progress(LaunchStage.FetchingVersionManifest))
        val summaries = versionClient.fetchVersionManifest().getOrElse {
            return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.FetchingVersionManifest,
                    it.message ?: "Couldn't reach Mojang's version manifest"
                )
            )
        }
        val summary = summaries.firstOrNull { it.id == profile.minecraftVersion }
            ?: return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.FetchingVersionManifest,
                    "Version ${profile.minecraftVersion} not found in the manifest"
                )
            )
        val details = versionClient.fetchVersionDetails(summary.url).getOrElse {
            return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.FetchingVersionManifest,
                    it.message ?: "Couldn't fetch version details"
                )
            )
        }

        if (lwjglProvider.isLwjgl2Version(details.libraries)) {
            return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.FetchingVersionManifest,
                    "${profile.minecraftVersion} uses LWJGL2 - only 1.13+ (LWJGL3) " +
                        "versions can launch right now"
                )
            )
        }

        emit(LaunchOutcome.Progress(LaunchStage.ProvisioningJvm))
        val javaRuntime = resolveJavaRuntime(profile, details)
        jvmRuntimeManager.ensureAvailable(javaRuntime).getOrElse {
            return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.ProvisioningJvm,
                    it.message ?: "Couldn't provision Java ${javaRuntime.majorVersion}"
                )
            )
        }
        val runtimeRoot = jvmRuntimeManager.runtimeRoot(javaRuntime)

        emit(LaunchOutcome.Progress(LaunchStage.DownloadingLibraries))
        val nonLwjglLibraries = lwjglProvider.withoutLwjgl(details.libraries)
        val libraryFiles = libraryDownloader.ensureLibraries(nonLwjglLibraries).getOrElse {
            return@flow emit(
                LaunchOutcome.Failed(LaunchStage.DownloadingLibraries, it.message ?: "Library download failed")
            )
        }
        val clientJarFile = libraryDownloader.ensureClientJar(details).getOrElse {
            return@flow emit(
                LaunchOutcome.Failed(LaunchStage.DownloadingLibraries, it.message ?: "Client jar download failed")
            )
        }
        val lwjglJarFiles = lwjglProvider.classpathJarPaths().map(::File)
        val classpath = libraryDownloader.buildClasspath(libraryFiles + lwjglJarFiles, clientJarFile)

        emit(LaunchOutcome.Progress(LaunchStage.PreparingRenderer))
        prepareRenderEnvironment(renderPath, device)
        setCoreEnvironment(runtimeRoot, profile)

        val instanceDir = instanceDirs.instanceDir(profile.id)
        val substitutions = mapOf(
            "auth_player_name" to account.username,
            "auth_uuid" to account.id.replace("-", ""),
            "auth_access_token" to session.accessToken,
            "auth_session" to session.accessToken, // pre-1.7 alias for the same value
            "auth_xuid" to (session.xuid ?: ""),
            "user_type" to "msa", // this project only ever does Microsoft-authenticated accounts
            "user_properties" to "{}", // vestigial pre-MSA field, always empty in modern practice
            "clientid" to "",
            "version_name" to details.id,
            "version_type" to summary.type,
            "game_directory" to instanceDir.absolutePath,
            "assets_root" to instanceDirs.assetsDir.absolutePath,
            "assets_index_name" to details.assets,
            "natives_directory" to lwjglProvider.nativesDir.absolutePath,
            "library_directory" to instanceDirs.librariesDir.absolutePath,
            "launcher_name" to "assassin-launcher",
            "launcher_version" to "0.1.0",
            "classpath" to classpath,
            "classpath_separator" to ":"
        )
        // No custom-resolution/demo/quick-play UI exists yet, so those
        // rule-gated arguments should resolve to "not included" rather
        // than silently reaching the JVM as a literal unsubstituted
        // "${resolution_width}" token.
        val activeFeatures = mapOf(
            "has_custom_resolution" to false,
            "is_demo_user" to false,
            "has_quick_plays_support" to false,
            "is_quick_play_singleplayer" to false,
            "is_quick_play_multiplayer" to false,
            "is_quick_play_realms" to false
        )

        val jvmArgs = GameArgumentBuilder.buildJvmArgs(details, substitutions, activeFeatures)
        val gameArgs = GameArgumentBuilder.buildGameArgs(details, substitutions, activeFeatures)
        val ramArgs = listOf("-Xms512M", "-Xmx${profile.ramAllocationMb ?: DEFAULT_RAM_MB}M")
        val userJvmArgs = profile.jvmArgsOverride?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

        val fullArgs = listOf("java") + ramArgs + userJvmArgs + jvmArgs +
            listOf(details.mainClass) + gameArgs

        emit(LaunchOutcome.Progress(LaunchStage.StartingJvm))
        if (!dlopenJdkLibrariesInOrder(runtimeRoot)) {
            return@flow emit(
                LaunchOutcome.Failed(
                    LaunchStage.StartingJvm,
                    "Failed loading one of the JVM's own shared libraries - see logcat " +
                        "tag AssassinJvmLauncher for which one"
                )
            )
        }

        val (fullVersion, dotVersion) = jdkVersionStrings(javaRuntime)
        NativeBridge.launchEmbeddedJvm(
            jliLibraryPath = File(runtimeRoot, "lib/libjli.so").absolutePath,
            args = fullArgs.toTypedArray(),
            fullVersion = fullVersion,
            dotVersion = dotVersion
        )
        // A normal return here (rather than the whole process ending, per
        // launchEmbeddedJvm's doc comment) means JLI_Launch bailed out
        // before the JVM ever really started - a malformed argument list,
        // not a game session that ran and closed.
        emit(
            LaunchOutcome.Failed(
                LaunchStage.StartingJvm,
                "JLI_Launch returned without the JVM starting - check the argument list"
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun resolveJavaRuntime(profile: GameProfile, details: MinecraftVersionDetails): JavaRuntimeVersion {
        profile.javaRuntimeOverride
            ?.let { override -> JavaRuntimeVersion.entries.firstOrNull { it.majorVersion.toString() == override } }
            ?.let { return it }

        val required = details.requiredJavaMajorVersion
        return JavaRuntimeVersion.entries.filter { it.majorVersion >= required }
            .minByOrNull { it.majorVersion }
            ?: JavaRuntimeVersion.entries.maxBy { it.majorVersion }
    }

    /** Loads the chosen renderer's native library and sets the
     * environment variables it reads at runtime. MG_DIR_PATH is a
     * confirmed real requirement (docs/PROGRESS.md, verified against
     * Amethyst's source). The Zink/Turnip variables below are the same
     * confirmed findings recorded in docs/PROGRESS.md's research notes;
     * everything here degrades to "renderer fails to load, launch fails
     * with a clear message" rather than a silent black screen, since the
     * underlying native .so gap (see this class's own doc comment) means
     * that failure is the expected outcome until that gap closes. */
    private fun prepareRenderEnvironment(renderPath: RenderPath, device: DeviceProfile) {
        // TurnipDriverManager currently only has one bundled variant
        // (710/720/722) to choose from - matching by supportedModels
        // here is what makes this correct once more variants exist,
        // rather than something that would need revisiting alongside
        // them. Falls back to whatever's bundled if this exact chip
        // isn't covered, rather than refusing to try.
        fun matchingTurnipVariant(): TurnipDriverVariant? {
            val variants = turnipDriverManager.listBundledVariants()
            val model = device.adrenoModel
            return variants.firstOrNull { model != null && model in it.supportedModels }
                ?: variants.firstOrNull()
        }

        when (renderPath) {
            is RenderPath.MobileGlues -> {
                mobileGluesManager.tryLoad()
                Os.setenv("MG_DIR_PATH", mobileGluesManager.dataDir.absolutePath, true)
            }
            is RenderPath.KryptonWrapper -> {
                kryptonWrapperManager.tryLoad()
            }
            is RenderPath.ZinkOverTurnip -> {
                matchingTurnipVariant()?.let { turnipDriverManager.tryLoad(it) }
                Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "zink", true)
                Os.setenv("MESA_GL_VERSION_OVERRIDE", "4.6COMPAT", true)
                Os.setenv("MESA_GLSL_VERSION_OVERRIDE", "460", true)
                Os.setenv("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1", true)
            }
            is RenderPath.VulkanViaTurnip, is RenderPath.VulkanViaPanfrost -> {
                matchingTurnipVariant()?.let { turnipDriverManager.tryLoad(it) }
            }
            is RenderPath.SystemVulkan -> Unit // nothing to load - the system driver is already there
            is RenderPath.BaseGl4es -> Unit // last resort, no extra loading step of its own
        }
    }

    private fun setCoreEnvironment(runtimeRoot: File, profile: GameProfile) {
        Os.setenv("JAVA_HOME", runtimeRoot.absolutePath, true)
        Os.setenv("HOME", instanceDirs.instanceDir(profile.id).absolutePath, true)
        Os.setenv("TMPDIR", context.cacheDir.absolutePath, true)
        Os.setenv(
            "LD_LIBRARY_PATH",
            listOf(
                File(runtimeRoot, "lib").absolutePath,
                File(runtimeRoot, "lib/server").absolutePath,
                context.applicationInfo.nativeLibraryDir
            ).joinToString(":"),
            true
        )
    }

    /** Order matters here - confirmed against a real downloaded JDK 17
     * build from our own AngelAuraMC source (not assumed from Amethyst's
     * multi-arch-oriented layout, which this single-arch build doesn't
     * match: no lib/aarch64 subdirectory, everything is flat under lib/).
     * libjli.so and libjvm.so have to load before anything that depends
     * on JNI/JVM symbols; the explicit list mirrors the real dependency
     * order, and the sweep at the end picks up whatever else a given JDK
     * build ships beyond this common set. */
    private fun dlopenJdkLibrariesInOrder(runtimeRoot: File): Boolean {
        val libDir = File(runtimeRoot, "lib")
        val orderedNames = listOf(
            "libjli.so",
            "server/libjvm.so",
            "libverify.so",
            "libjava.so",
            "libnet.so",
            "libnio.so",
            "libawt.so",
            "libawt_headless.so",
            "libfreetype.so",
            "libfontmanager.so"
        )
        val loaded = mutableSetOf<String>()
        for (name in orderedNames) {
            val file = File(libDir, name)
            if (!file.exists()) continue // not every JDK build ships every optional library
            if (!NativeBridge.dlopenJvmLibrary(file.absolutePath)) return false
            loaded += file.name
        }

        libDir.walkTopDown()
            .filter { it.isFile && it.extension == "so" && it.name !in loaded }
            .forEach { NativeBridge.dlopenJvmLibrary(it.absolutePath) }

        // OpenAL is the app's own bundled native, not the JDK's - LWJGL
        // needs it for audio. Not present yet (see this class's doc
        // comment); attempted anyway so this starts working the moment
        // it's bundled, rather than needing this function revisited too.
        val openAl = File(context.applicationInfo.nativeLibraryDir, "libopenal.so")
        if (openAl.exists()) NativeBridge.dlopenJvmLibrary(openAl.absolutePath)

        return true
    }

    /** JLI_Launch takes these as informational strings, not a control
     * value that gates behavior - low risk if not exactly right, but
     * computed per-runtime rather than copying Amethyst's hardcoded
     * "1.8.0-internal" (correct only for their single bundled Java 8;
     * wrong for three of this project's four bundled majors). */
    private fun jdkVersionStrings(runtime: JavaRuntimeVersion): Pair<String, String> =
        "${runtime.majorVersion}.0-internal" to "${runtime.majorVersion}"

    companion object {
        private const val DEFAULT_RAM_MB = 2048
    }
}
