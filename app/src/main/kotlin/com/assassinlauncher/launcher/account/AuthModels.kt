package com.assassinlauncher.launcher.account

data class MicrosoftTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int
)

data class XblToken(val token: String, val userHash: String, val xid: String? = null)

data class MinecraftProfile(
    val uuid: String,
    val username: String,
    val skinUrl: String?
)

/**
 * The actual Minecraft session (from login_with_xbox), separate from the
 * Microsoft OAuth tokens. This is what the game itself needs at launch
 * (--accessToken, --uuid, --xuid) - previously fetched, used once for the
 * profile lookup, then discarded, which meant nothing could ever actually
 * launch authenticated. Session-lifetime only by design, same reasoning
 * as AccountRepository not persisting the Microsoft refresh token: a
 * short-lived Minecraft access token is lower-risk than a refresh token,
 * but there's still no reason to write it to plain-text disk when
 * "sign in again" is already the accepted tradeoff for this project.
 */
data class MinecraftSession(
    val accessToken: String,
    val expiresInSeconds: Int,
    val xuid: String?
)

/** Everything a real sign-in produces, together - the profile to store,
 * the Microsoft tokens (used only to refresh, never persisted), and the
 * Minecraft session (needed for the actual launch). */
data class SignInResult(
    val profile: MinecraftProfile,
    val microsoftTokens: MicrosoftTokens,
    val minecraftSession: MinecraftSession
)
