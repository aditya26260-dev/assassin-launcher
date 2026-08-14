package com.assassinlauncher.launcher.account

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
 * project. Deliberately NOT persisting the Microsoft refresh token here -
 * that's a real credential (it can silently re-authenticate as the user
 * indefinitely until revoked), and storing it in plain preferences/JSON
 * would be a real security gap, not a shortcut worth taking. Signing in
 * again each app restart is the honest tradeoff until proper encrypted
 * storage (androidx.security's EncryptedSharedPreferences or equivalent)
 * gets added - stated here rather than silently storing it insecurely.
 */
class AccountRepository(context: Context) {

    private val storeFile = File(context.filesDir, "accounts.json")
    private var activeAccountId: String? = null

    // In-memory only, keyed by account id - same reasoning as the refresh
    // token above, extended to the Minecraft access token: it's what the
    // game actually launches with, and it doesn't survive app restart.
    // A launch attempted after a restart (no entry here) needs a fresh
    // sign-in, not a silently broken/offline launch.
    private val activeSessions = mutableMapOf<String, MinecraftSession>()

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
     * profile to disk and keeps the session (access token) in memory for
     * this app run - see the field comment above for why. */
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
        save(current)
    }

    /** The in-memory Minecraft session for an account, if it signed in
     * this app run. Null means "needs to sign in again" - there is
     * deliberately no persisted fallback (see field comment above). */
    fun sessionFor(accountId: String): MinecraftSession? = activeSessions[accountId]

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
