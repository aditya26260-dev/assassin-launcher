package com.assassinlauncher.launcher.account

object MicrosoftAuthConfig {
    // Real client ID from the actual Azure app registration (Personal
    // accounts only, confirmed correct for Xbox Live sign-in - see
    // PROGRESS.md for why organizational accounts don't work here at all).
    const val CLIENT_ID = "1bb23364-a504-4974-9e60-4c71dbbca67a"

    // The "no server needed" redirect Microsoft provides specifically for
    // native/public clients - confirmed current via Microsoft's own docs
    // and cross-checked against a real launcher's own working setup.
    // This needs to be registered in the Azure app under Authentication ->
    // Add a platform -> Web, with this exact URI - not done automatically,
    // needs the user to add it.
    const val REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient"

    // "consumers" specifically, not "common" or a tenant ID - confirmed in
    // Phase 0 research: Xbox Live's sign-in scope rejects organizational/
    // tenant accounts outright, "consumers" is the fixed endpoint for
    // personal Microsoft accounts only.
    const val AUTHORIZE_URL =
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
    const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"

    const val SCOPE = "XboxLive.signin offline_access"
}
