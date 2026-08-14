package com.assassinlauncher.launcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class AssassinLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createGameSessionNotificationChannel()
    }

    private fun createGameSessionNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            GAME_SESSION_CHANNEL_ID,
            getString(R.string.game_session_notification_title),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val GAME_SESSION_CHANNEL_ID = "game_session"
    }
}
