package com.assassinlauncher.launcher.account

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private fun buildAuthorizeUrl(): String {
    fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
    // "redirect_url" here too, matching the same nonstandard parameter
    // name this legacy endpoint uses at the token step - confirmed
    // against Amethyst's real authorize URL, not assumed consistent.
    // No response_mode param either - a v2.0-endpoint concept this
    // older endpoint doesn't expect, dropped to match their proven URL
    // shape exactly rather than carrying over something that belonged
    // to the previous flow.
    return MicrosoftAuthConfig.AUTHORIZE_URL +
        "?client_id=${enc(MicrosoftAuthConfig.CLIENT_ID)}" +
        "&response_type=code" +
        "&redirect_url=${enc(MicrosoftAuthConfig.REDIRECT_URI)}" +
        "&scope=${enc(MicrosoftAuthConfig.SCOPE)}"
}

sealed class MicrosoftSignInState {
    data object LoadingPage : MicrosoftSignInState()
    data object ExchangingTokens : MicrosoftSignInState()
    data class Success(val result: SignInResult) : MicrosoftSignInState()
    data class Failed(val message: String) : MicrosoftSignInState()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MicrosoftSignInScreen(
    onResult: (MicrosoftSignInState.Success) -> Unit,
    onCancel: () -> Unit
) {
    var state by remember { mutableStateOf<MicrosoftSignInState>(MicrosoftSignInState.LoadingPage) }
    val authClient = remember { MinecraftAuthClient() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state) {
        val current = state
        if (current is MicrosoftSignInState.Success) {
            onResult(current)
        }
    }

    fun handleAuthCode(code: String) {
        state = MicrosoftSignInState.ExchangingTokens
        coroutineScope.launch {
            authClient.signInWithAuthorizationCode(code).fold(
                onSuccess = { result ->
                    state = MicrosoftSignInState.Success(result)
                },
                onFailure = { error ->
                    state = MicrosoftSignInState.Failed(error.message ?: "Sign-in failed")
                }
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val current = state) {
            is MicrosoftSignInState.ExchangingTokens -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is MicrosoftSignInState.Failed -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Sign-in failed: ${current.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith(MicrosoftAuthConfig.REDIRECT_URI)) {
                                    val uri = android.net.Uri.parse(url)
                                    val code = uri.getQueryParameter("code")
                                    val error = uri.getQueryParameter("error_description")
                                    if (code != null) {
                                        handleAuthCode(code)
                                    } else {
                                        state = MicrosoftSignInState.Failed(
                                            error ?: "No authorization code returned"
                                        )
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(buildAuthorizeUrl())
                    }
                }
            )
        }
    }
}
