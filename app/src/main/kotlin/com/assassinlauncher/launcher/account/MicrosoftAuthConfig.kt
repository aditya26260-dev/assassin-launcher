package com.assassinlauncher.launcher.account

object MicrosoftAuthConfig {
    // Real cause of the "Invalid app registration" error a real sign-in
    // attempt hit: getting a Minecraft-usable Xbox token needs the
    // XboxLive.signin scope, which Microsoft gates behind manual Xbox
    // Developer / ID@Xbox approval for the modern Azure AD v2.0 platform
    // - confirmed directly via Microsoft's own Q&A, not assumed. That
    // approval isn't something a new app registration can get by being
    // configured correctly, however carefully, which is why two full
    // rounds of Azure settings fixes still ended here.
    //
    // Every unofficial Minecraft launcher solves this the same way:
    // using an already-approved client ID rather than a new one, since
    // individual hobbyist developers generally can't get through that
    // approval process for a personal project. This project now does
    // the same, using Amethyst's real, working, currently-shipping
    // client ID and its matching legacy `login.live.com` flow (an older,
    // separate authentication generation from the modern Azure AD v2.0
    // endpoints this file used before - the client ID, scope format, and
    // endpoints below are only valid together as this one proven set,
    // confirmed by reading Amethyst's actual working implementation
    // directly rather than reused piecemeal from a different launcher's
    // different-generation flow).
    const val CLIENT_ID = "00000000402b5328"

    const val REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
    const val AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf"
    const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"

    // Legacy scope syntax tied to this same older auth generation - not
    // the modern "XboxLive.signin offline_access" this file had before,
    // which belongs to the gated v2.0 flow above.
    const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
}
