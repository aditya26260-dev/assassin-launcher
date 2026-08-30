package com.assassinlauncher.launcher.account

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AccountType { MICROSOFT, LOCAL }

data class Account(
    val id: String,
    val username: String,
    val type: AccountType,
    val skinUrl: String? = null
)

/**
 * Persists account profiles (username, UUID, skin URL - none of it
 * sensitive) as plain JSON, same pattern as everything else in this
 * project.
 *
 * The Minecraft session (access token) is now ALSO persisted, encrypted
 * with Tink's AEAD primitive backed by the Android Keystore - not
 * androidx.security's EncryptedSharedPreferences, which Google deprecated
 * (April 2025, security-crypto 1.1.0-alpha07) in favor of using Tink
 * directly. Deliberately still NOT persisting the Microsoft refresh
 * token - that's a real credential that can silently re-authenticate as
 * the user indefinitely until revoked, a materially bigger risk than a
 * short-lived Minecraft access token, and not needed to solve the actual
 * problem (re-signing in on every single app restart during active
 * development). Plain SharedPreferences rather than DataStore for the
 * encrypted blob itself - DataStore's own official Tink integration
 * (androidx.datastore:datastore-tink) is still alpha as of this writing,
 * and sessionFor() needs to stay synchronous to avoid changing every call
 * site across the app; encrypting a value before a plain synchronous
 * SharedPreferences write sidesteps both problems.
 */
class AccountRepository(context: Context) {

    private val storeFile = File(context.filesDir, "accounts.json")
    private val sessionPrefs = context.getSharedPreferences("encrypted_sessions", Context.MODE_PRIVATE)

    // Null if key setup ever fails on some device - falls back to
    // in-memory-only behavior (the previous behavior) rather than
    // crashing the app over a persistence nice-to-have.
    private val sessionAead: Aead? = runCatching { buildSessionAead(context) }.getOrNull()

    companion object {
        // Moved from per-instance fields into a companion object -
        // GameLaunchOrchestrator constructs its own AccountRepository(context),
        // separate from whatever instance the sign-in screen used to call
        // addOrUpdateMicrosoftAccount(). As instance fields, that meant the
        // orchestrator's sessionFor() always looked at its own empty map,
        // reporting "Session expired" immediately after every real sign-in,
        // regardless of how recent it was. Confirmed directly from the
        // accountRepository.sessionFor() call site in GameLaunchOrchestrator,
        // not guessed.
        private var activeAccountId: String? = null

        // In-memory cache, keyed by account id - checked first since it's
        // free once populated. No longer the only copy: sessionFor() below
        // falls back to the encrypted persistent copy and repopulates this
        // on a hit, so a session survives app restart without every caller
        // needing to change.
        private val activeSessions = mutableMapOf<String, MinecraftSession>()
    }

    fun listAccounts(): List<Account> {
        if (!storeFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(storeFile.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Account(
                    id = obj.getString("id"),
                    username = obj.getString("username"),
                    type = runCatching { AccountType.valueOf(obj.getString("type")) }
                        .getOrDefault(AccountType.MICROSOFT),
                    skinUrl = obj.optString("skinUrl").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun activeAccount(): Account? =
        listAccounts().firstOrNull { it.id == activeAccountId } ?: listAccounts().firstOrNull()

    fun setActiveAccount(accountId: String) {
        if (listAccounts().any { it.id == accountId }) {
            activeAccountId = accountId
        }
    }

    /** Called after a real Microsoft sign-in completes. Stores the
     * profile to disk and the session both in memory and, encrypted, to
     * disk - see the class doc comment above for why this is safe to
     * persist now when it previously wasn't. */
    fun addOrUpdateMicrosoftAccount(profile: MinecraftProfile, session: MinecraftSession) {
        val current = listAccounts().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == profile.uuid }
        val account = Account(
            id = profile.uuid,
            username = profile.username,
            type = AccountType.MICROSOFT,
            skinUrl = profile.skinUrl
        )
        if (existingIndex >= 0) current[existingIndex] = account else current.add(account)
        activeAccountId = account.id
        activeSessions[account.id] = session
        persistSession(account.id, session)
        save(current)
    }

    /** Checks the in-memory cache first (free once populated), then falls
     * back to the encrypted persistent copy - populating the in-memory
     * cache on a hit so the disk read only happens once per app run per
     * account, not on every call. Null means genuinely no session
     * anywhere: never signed in, or the persisted copy failed to decrypt
     * (corrupted, tampered with, or a Keystore key that stopped being
     * valid - all treated the same as "needs to sign in again" rather
     * than crashing). */
    fun sessionFor(accountId: String): MinecraftSession? {
        activeSessions[accountId]?.let { return it }
        return loadPersistedSession(accountId)?.also { activeSessions[accountId] = it }
    }

    /** Local/offline account - no real authentication, for development so
     * reinstalling doesn't require a real Microsoft sign-in every time.
     * The UUID is deterministic from the username
     * (UUID.nameUUIDFromBytes("OfflinePlayer:$username")), the same
     * scheme vanilla offline-mode servers use, so the same name always
     * maps to the same UUID/world data. The access token is a placeholder
     * the game never actually validates in offline mode - not a real
     * credential, nothing sensitive to protect by keeping it in memory
     * only, but stored the same way as the Microsoft session regardless
     * for one consistent code path in GameLaunchOrchestrator. */
    fun addOrUpdateLocalAccount(username: String): Account {
        val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()
        val current = listAccounts().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == uuid }
        val account = Account(
            id = uuid,
            username = username,
            type = AccountType.LOCAL,
            skinUrl = null
        )
        if (existingIndex >= 0) current[existingIndex] = account else current.add(account)
        activeAccountId = account.id
        val session = MinecraftSession(
            accessToken = "offline",
            expiresInSeconds = Int.MAX_VALUE,
            xuid = null
        )
        activeSessions[account.id] = session
        persistSession(account.id, session)
        save(current)
        return account
    }

    private fun persistSession(accountId: String, session: MinecraftSession) {
        val aead = sessionAead ?: return
        runCatching {
            val json = JSONObject().apply {
                put("accessToken", session.accessToken)
                put("expiresInSeconds", session.expiresInSeconds)
                put("xuid", session.xuid ?: "")
            }
            val ciphertext = aead.encrypt(json.toString().toByteArray(Charsets.UTF_8), accountId.toByteArray())
            sessionPrefs.edit().putString(accountId, Base64.encodeToString(ciphertext, Base64.DEFAULT)).apply()
        }
        // A failure here just means this sign-in stays in-memory-only for
        // this run, same as before this feature existed - not worth
        // surfacing to the caller over what's already a best-effort
        // convenience layer on top of working in-memory session state.
    }

    private fun loadPersistedSession(accountId: String): MinecraftSession? {
        val aead = sessionAead ?: return null
        val encoded = sessionPrefs.getString(accountId, null) ?: return null
        return runCatching {
            val ciphertext = Base64.decode(encoded, Base64.DEFAULT)
            // accountId as associated data - the same value used to
            // encrypt must be supplied to decrypt, which also means a
            // ciphertext can't silently be swapped to answer for a
            // different account id than the one it was actually stored
            // under.
            val plaintext = aead.decrypt(ciphertext, accountId.toByteArray())
            val json = JSONObject(String(plaintext, Charsets.UTF_8))
            MinecraftSession(
                accessToken = json.getString("accessToken"),
                expiresInSeconds = json.getInt("expiresInSeconds"),
                xuid = json.optString("xuid").ifBlank { null }
            )
        }.getOrNull()
    }

    private fun save(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("id", account.id)
                    put("username", account.username)
                    put("type", account.type.name)
                    put("skinUrl", account.skinUrl)
                }
            )
        }
        storeFile.writeText(array.toString())
    }
}

/** One Keystore-backed key, generated on first use and reused after -
 * AndroidKeysetManager persists the (Keystore-wrapped, not plaintext)
 * keyset itself in its own small SharedPreferences file and handles
 * reading it back on subsequent calls. KeyTemplates.get("AES256_GCM") is
 * the current Tink API - not the older AeadKeyTemplates.AES256_GCM or
 * PredefinedAeadParameters.AES256_GCM forms of the same template seen in
 * older examples. */
private fun buildSessionAead(context: Context): Aead {
    AeadConfig.register()
    val keysetHandle = AndroidKeysetManager.Builder()
        .withSharedPref(context.applicationContext, "assassin_session_keyset", "assassin_session_keyset_prefs")
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri("android-keystore://assassin_launcher_session_master_key")
        .build()
        .keysetHandle
    return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
