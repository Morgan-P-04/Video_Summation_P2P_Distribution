package com.example.fyp.feature

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)

    // Load the saved username, or give the user a random default one
    var username by remember {
        mutableStateOf(prefs.getString("username", "Node_${(1000..9999).random()}") ?: "")
    }

    // get hidden UUID to display for FYP demo
    val localNodeId = prefs.getString("node_id", "Unknown_UUID")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp, top = 16.dp)
        )

        // edit username
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Display Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "User Icon") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotBlank()) {
                    prefs.edit().putString("username", username).apply()
                    Toast.makeText(context, "Username saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Changes")
        }

        Spacer(modifier = Modifier.height(48.dp))
        Divider()
        Spacer(modifier = Modifier.height(48.dp))

        // technical details area
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Device Network Identity",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your permanent publisher ID used by the Epidemic Routing Echo Blocker. It does not change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$localNodeId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}