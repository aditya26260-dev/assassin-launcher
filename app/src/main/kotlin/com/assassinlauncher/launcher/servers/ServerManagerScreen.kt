package com.assassinlauncher.launcher.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.assassinlauncher.launcher.instance.InstanceDirectoryManager
import java.io.File

@Composable
fun ServerManagerScreen(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(profileId) {
        val instanceDir = InstanceDirectoryManager(context).instanceDir(profileId)
        ServerRepository(File(instanceDir, "servers.dat"))
    }
    var servers by remember(profileId) { mutableStateOf(repository.list()) }
    var editingEntry by remember { mutableStateOf<ServerEntry?>(null) }
    var showingAddForm by remember { mutableStateOf(false) }

    fun refresh() {
        servers = repository.list()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Button(onClick = onBack) { Text("Close") }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            if (showingAddForm || editingEntry != null) {
                ServerForm(
                    existing = editingEntry,
                    onSave = { name, address ->
                        val current = editingEntry
                        if (current != null) {
                            repository.update(current.copy(name = name, address = address))
                        } else {
                            repository.add(name, address)
                        }
                        editingEntry = null
                        showingAddForm = false
                        refresh()
                    },
                    onCancel = {
                        editingEntry = null
                        showingAddForm = false
                    }
                )
            } else {
                Button(onClick = { showingAddForm = true }) { Text("Add server") }

                LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                    items(servers, key = { it.id }) { server ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(server.name, color = MaterialTheme.colorScheme.onBackground)
                                Text(
                                    server.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { editingEntry = server }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = {
                                    repository.remove(server.id)
                                    refresh()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerForm(
    existing: ServerEntry?,
    onSave: (name: String, address: String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var address by remember(existing) { mutableStateOf(existing?.address ?: "") }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Server name") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            placeholder = { Text("play.example.com or play.example.com:25565") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = { onSave(name, address) },
                modifier = Modifier.padding(end = 8.dp)
            ) { Text("Save") }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}
