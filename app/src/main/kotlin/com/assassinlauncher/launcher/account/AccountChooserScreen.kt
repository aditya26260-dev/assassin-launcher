package com.assassinlauncher.launcher.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * First stop when tapping the account icon - Microsoft or offline,
 * instead of jumping straight into the Microsoft WebView flow. Offline
 * exists for development, so testing doesn't require a real sign-in on
 * every reinstall - not a substitute for real auth in any build meant
 * for other people.
 */
@Composable
fun AccountChooserScreen(
    onChooseMicrosoft: () -> Unit,
    onChooseOffline: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sign in",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Microsoft is required to play online or on servers that check ownership. Offline accounts are for local testing only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
            )
            Button(
                onClick = onChooseMicrosoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Microsoft")
            }
            OutlinedButton(
                onClick = onChooseOffline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("Play Offline")
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}
