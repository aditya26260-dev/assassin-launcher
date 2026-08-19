package com.assassinlauncher.launcher.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * The real Microsoft -> Xbox Live -> XSTS -> Minecraft token chain every
 * Minecraft launcher implements against, not something invented for this
 * project. Written against the well-documented, stable flow (wiki.vg's
 * Microsoft authentication page, cross-referenced against Phase 0
 * research on this project's own account-type requirements). Genuinely
 * untested against a live call - no working network in this sandbox -
 * worth a real test the first chance it gets to run somewhere with
 * internet, same caveat as the Modrinth client.
 */
class MinecraftAuthClient {

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json".toMediaType()
    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    suspend fun exchangeCodeForMicrosoftToken(code: String): Result<MicrosoftTokens> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = listOf(
                    "client_id" to MicrosoftAuthConfig.CLIENT_ID,
                    "code" to code,
                    "grant_type" to "authorization_code",
                    "redirect_uri" to MicrosoftAuthConfig.REDIRECT_URI,
                    "scope" to MicrosoftAuthConfig.SCOPE
                ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }

                val request = Request.Builder()
                    .url(MicrosoftAuthConfig.TOKEN_URL)
                    .post(body.toRequestBody(formMediaType))
                    .build()

                val json = JSONObject(executeForBody(request))
                MicrosoftTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresInSeconds = json.optInt("expires_in", 3600)
                )
            }
        }

    suspend fun refreshMicrosoftToken(refreshToken: String): Result<MicrosoftTokens> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = listOf(
                    "client_id" to MicrosoftAuthConfig.CLIENT_ID,
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token",
                    "scope" to MicrosoftAuthConfig.SCOPE
                ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }

                val request = Request.Builder()
                    .url(MicrosoftAuthConfig.TOKEN_URL)
                    .post(body.toRequestBody(formMediaType))
                    .build()

                val json = JSONObject(executeForBody(request))
                MicrosoftTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresInSeconds = json.optInt("expires_in", 3600)
                )
            }
        }

    private suspend fun authenticateWithXboxLive(msAccessToken: String): Result<XblToken> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put(
                        "Properties",
                        JSONObject().apply {
                            put("AuthMethod", "RPS")
                            put("SiteName", "user.auth.xboxlive.com")
                            put("RpsTicket", "d=$msAccessToken")
                        }
                    )
                    put("RelyingParty", "http://auth.xboxlive.com")
                    put("TokenType", "JWT")
                }
                val request = Request.Builder()
                    .url("https://user.auth.xboxlive.com/user/authenticate")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                parseXblStyleToken(JSONObject(executeForBody(request)))
            }
        }

    private suspend fun authorizeWithXsts(xblToken: String): Result<XblToken> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put(
                        "Properties",
                        JSONObject().apply {
                            put("SandboxId", "RETAIL")
                            put("UserTokens", JSONArray().put(xblToken))
                        }
                    )
                    put("RelyingParty", "rp://api.minecraftservices.com/")
                    put("TokenType", "JWT")
                }
                val request = Request.Builder()
                    .url("https://xsts.auth.xboxlive.com/xsts/authorize")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                // A 401 here with an XErr code means something specific
                // (no Xbox account on this Microsoft account, needs adult
                // family group, banned, etc.) - not treated as a generic
                // failure so a real user sees why, once this has somewhere
                // to actually surface that message.
                parseXblStyleToken(JSONObject(executeForBody(request)))
            }
        }

    private fun parseXblStyleToken(json: JSONObject): XblToken {
        val token = json.getString("Token")
        val claim = json.getJSONObject("DisplayClaims")
            .getJSONArray("xui")
            .getJSONObject(0)
        val userHash = claim.getString("uhs")
        // xid (the actual XUID) is only present on some responses - the
        // XSTS-authorize step has it, the earlier XBL user-auth step may
        // not. Optional here, required nowhere - some Minecraft versions
        // don't need --xuid at all, and it's not worth failing sign-in
        // over a field that's a launch-argument nicety, not a credential.
        val xid = claim.optString("xid").takeIf { it.isNotBlank() }
        return XblToken(token = token, userHash = userHash, xid = xid)
    }

    private suspend fun loginWithXbox(xstsToken: String, userHash: String): Result<MinecraftSession> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("identityToken", "XBL3.0 x=$userHash;$xstsToken")
                }
                val request = Request.Builder()
                    .url("https://api.minecraftservices.com/authentication/login_with_xbox")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                val json = JSONObject(executeForBody(request))
                MinecraftSession(
                    accessToken = json.getString("access_token"),
                    expiresInSeconds = json.optInt("expires_in", 86400),
                    xuid = null // filled in by the caller, which has the XSTS claim this came from
                )
            }
        }

    suspend fun getProfile(minecraftAccessToken: String): Result<MinecraftProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.minecraftservices.com/minecraft/profile")
                    .header("Authorization", "Bearer $minecraftAccessToken")
                    .build()
                val json = JSONObject(executeForBody(request))
                val skins = json.optJSONArray("skins")
                val activeSkinUrl = (0 until (skins?.length() ?: 0))
                    .map { skins!!.getJSONObject(it) }
                    .firstOrNull { it.optString("state") == "ACTIVE" }
                    ?.optString("url")

                MinecraftProfile(
                    uuid = json.getString("id"),
                    username = json.getString("name"),
                    skinUrl = activeSkinUrl?.takeIf { it.isNotBlank() }
                )
            }
        }

    /** Runs the full chain from a Microsoft auth code through to a real
     * Minecraft profile - the actual end-to-end flow "sign in" needs,
     * not just the individual steps left for the caller to stitch
     * together. */
    suspend fun signInWithAuthorizationCode(code: String): Result<SignInResult> {
        val msTokens = exchangeCodeForMicrosoftToken(code).getOrElse { return Result.failure(it) }
        val xbl = authenticateWithXboxLive(msTokens.accessToken)
            .getOrElse { return Result.failure(it) }
        val xsts = authorizeWithXsts(xbl.token).getOrElse { return Result.failure(it) }
        val session = loginWithXbox(xsts.token, xsts.userHash)
            .getOrElse { return Result.failure(it) }
            .copy(xuid = xsts.xid)
        val profile = getProfile(session.accessToken).getOrElse { return Result.failure(it) }
        return Result.success(SignInResult(profile, msTokens, session))
    }

    /** Every failure so far has only ever surfaced the HTTP status code -
     * "HTTP 401" and nothing else - because the actual response body,
     * which is where Microsoft puts the real, specific reason
     * (an AADSTS code and a real description, confirmed against their
     * own current docs), was being discarded before ever being read.
     * Two real config mistakes already got found and fixed on guesses
     * this cost; reading the body is what turns the next attempt into a
     * real answer instead of a fourth guess. */
    private fun executeForBody(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val detail = body?.takeIf { it.isNotBlank() } ?: "(no response body)"
                throw IOException(
                    "Request to ${request.url} failed: HTTP ${response.code} - $detail"
                )
            }
            return body ?: throw IOException("Empty response body")
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
