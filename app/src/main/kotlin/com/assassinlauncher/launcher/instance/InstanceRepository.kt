package com.assassinlauncher.launcher.instance

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the profile list as JSON in app-private storage (org.json,
 * already part of Android - not pulling in a serialization library for
 * something this doesn't need). Read/write is mutex-guarded since profile
 * edits can plausibly come from more than one place (quick panel, full
 * manager) later.
 */
class InstanceRepository(private val context: Context) {

    private val mutex = Mutex()
    private val storeFile: File get() = File(context.filesDir, "game_profiles.json")

    suspend fun listProfiles(): List<GameProfile> = mutex.withLock {
        val existing = readProfilesLocked()
        if (existing.isEmpty()) {
            ensureDefaultProfileLocked(existing)
            readProfilesLocked()
        } else {
            existing
        }
    }

    suspend fun saveProfile(profile: GameProfile) = mutex.withLock {
        val current = readProfilesLocked().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == profile.id }
        if (existingIndex >= 0) {
            current[existingIndex] = profile
        } else {
            current.add(profile)
        }
        writeProfilesLocked(current)
    }

    suspend fun deleteProfile(profileId: String) = mutex.withLock {
        val current = readProfilesLocked().filterNot { it.id == profileId }
        writeProfilesLocked(current)
        InstanceDirectoryManager(context).deleteInstance(profileId)
    }

    private fun readProfilesLocked(): List<GameProfile> {
        if (!storeFile.exists()) return emptyList()
        val text = storeFile.readText()
        if (text.isBlank()) return emptyList()
        return runCatching { JSONArray(text).toGameProfileList() }.getOrDefault(emptyList())
    }

    private fun writeProfilesLocked(profiles: List<GameProfile>) {
        storeFile.writeText(profiles.toJsonArray().toString())
    }

    /**
     * A fresh install needs one real profile to actually be usable, so the
     * home screen's play button (6.3) has something to launch. "26.2" is
     * the confirmed-real latest release as of Phase 0's research - this
     * should eventually come from Mojang's live version manifest instead
     * of a fixed string, once that integration exists.
     */
    private fun ensureDefaultProfileLocked(existing: List<GameProfile>) {
        if (existing.isNotEmpty()) return
        val defaultProfile = GameProfile(
            id = UUID.randomUUID().toString(),
            name = "Latest Release",
            minecraftVersion = "26.2",
            loader = ModLoader.VANILLA
        )
        writeProfilesLocked(listOf(defaultProfile))
        InstanceDirectoryManager(context).instanceDir(defaultProfile.id)
    }
}
