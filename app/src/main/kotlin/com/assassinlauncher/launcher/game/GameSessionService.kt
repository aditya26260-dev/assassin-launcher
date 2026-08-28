package com.assassinlauncher.launcher.game

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.assassinlauncher.launcher.AssassinLauncherApp
import com.assassinlauncher.launcher.MainActivity
import com.assassinlauncher.launcher.R
import com.assassinlauncher.launcher.hardware.DeviceProfileStore
import com.assassinlauncher.launcher.hardware.RenderPathRequest
import com.assassinlauncher.launcher.hardware.RenderPathSelector
import com.assassinlauncher.launcher.instance.InstanceRepository
import com.assassinlauncher.launcher.launch.GameLaunchOrchestrator
import com.assassinlauncher.launcher.launch.LaunchOutcome
import com.assassinlauncher.launcher.launch.LaunchStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps an active Minecraft session from being deprioritized by Android's
 * background process management when the player switches to another app,
 * and now actually starts that session via GameLaunchOrchestrator - this
 * was purely a notification shell before.
 *
 * Declared as foregroundServiceType="specialUse" in the manifest, per
 * architecture doc 5.5 - none of Android's predefined service types
 * (media playback, location, etc.) describe an active game session.
 *
 * Started only from an explicit foreground user action (tapping Play),
 * never from a background trigger, and stopped as soon as the session
 * actually ends rather than left running.
 *
 * One real architectural fact worth restating here, not just in
 * GameLaunchOrchestrator's own comments: a launch that actually succeeds
 * ends this process entirely when Minecraft closes (see
 * NativeBridge.launchEmbeddedJvm's doc comment for why) - this service,
 * the notification, the whole app, all of it. progress only ever reaches
 * observers for the lead-up to that point, or for a failure that stops
 * short of it. There is deliberately no "game closed, back to the menu"
 * codepath here yet - there isn't a process left to run it in.
 */
class GameSessionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId == null) {
            _launchState.value = LaunchOutcome.Failed(
                LaunchStage.ResolvingAccount,
                "No profile specified"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val profile = InstanceRepository(this@GameSessionService).listProfiles()
                .firstOrNull { it.id == profileId }
            val device = DeviceProfileStore.load(this@GameSessionService)

            if (profile == null || device == null) {
                _launchState.value = LaunchOutcome.Failed(
                    LaunchStage.ResolvingAccount,
                    if (profile == null) "Profile not found" else "Device not yet profiled - run first launch setup"
                )
                stopSelf()
                return@launch
            }

            val renderPath = RenderPathSelector.select(
                RenderPathRequest(
                    device = device,
                    minecraftSupportsNativeVulkan = profile.minecraftSupportsNativeVulkan(),
                    vulkanToggleEnabled = profile.vulkanEnabled,
                    minecraftAtMost1_16_5 = profile.isAtMost1_16_5(),
                    minecraftAtLeast1_17 = profile.isAtLeast1_17(),
                    turnipBuildAvailable = device.turnipBuildAvailable,
                    forceSystemVulkanDriver = profile.forceSystemVulkanDriver,
                    manualRendererOverride = profile.manualRendererOverride
                )
            ).path

            // Temporary - every input to this decision, made visible rather
            // than inferred, since reasoning about it from source alone
            // couldn't explain an observed Krypton Wrapper selection on
            // hardware that clearly qualifies for MobileGlues. Remove once
            // that's actually explained.
            android.util.Log.i(
                "RenderPathDecision",
                "minecraftVersion=${profile.minecraftVersion} " +
                    "isAtLeast1_17=${profile.isAtLeast1_17()} " +
                    "isAtMost1_16_5=${profile.isAtMost1_16_5()} " +
                    "mobileGluesLoadable=${device.mobileGluesLoadable} " +
                    "kryptonWrapperLoadable=${device.kryptonWrapperLoadable} " +
                    "glesVersion=${device.glesVersionMajor}.${device.glesVersionMinor} " +
                    "meetsMobileGluesFloor=${device.meetsMobileGluesFloor()} " +
                    "manualOverride=${profile.manualRendererOverride} " +
                    "selectedPath=$renderPath"
            )

            GameLaunchOrchestrator(this@GameSessionService)
                .launch(profile, device, renderPath)
                .collect { outcome ->
                    _launchState.value = outcome
                    if (outcome is LaunchOutcome.Failed) stopSelf()
                }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, pendingIntentFlags
        )

        return NotificationCompat.Builder(this, AssassinLauncherApp.GAME_SESSION_CHANNEL_ID)
            .setContentTitle(getString(R.string.game_session_notification_title))
            .setContentText(getString(R.string.game_session_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_PROFILE_ID = "profile_id"

        // Same-process state sharing between this service and the launch
        // UI - no need for a broadcast/IPC mechanism since both live in
        // this one process (see the class doc comment for why that's
        // true right up until it very much isn't). Null means "nothing
        // in progress"; the UI clears it back to null after showing a
        // terminal Failed state.
        private val _launchState = MutableStateFlow<LaunchOutcome?>(null)
        val launchState: StateFlow<LaunchOutcome?> = _launchState

        fun clearLaunchState() {
            _launchState.value = null
        }

        fun start(context: Context, profileId: String) {
            val intent = Intent(context, GameSessionService::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameSessionService::class.java))
        }
    }
}
