package com.assassinlauncher.launcher.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Local/offline account entry - just a username, no real authentication.
 * The UUID this produces (see AccountRepository.addOrUpdateLocalAccount)
 * is deterministic from the name, the same scheme vanilla offline-mode
 * servers use. Validation matches real Minecraft username rules (3-16
 * chars, letters/digits/underscore) mainly so the derived UUID behaves
 * consistently, not because offline mode itself requires it.
 */
@Composable
fun OfflineSignInScreen(
    onContinue: (username: String) -> Unit,
    onCancel: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    val trimmed = username.trim()
    val isValid = trimmed.length in 3..16 && trimmed.all { it.isLetterOrDigit() || it == '_' }

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
                text = "Play Offline",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "No sign-in, no ownership check - local testing only. 3-16 letters, numbers, or underscores.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onContinue(trimmed) },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text("Continue")
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
