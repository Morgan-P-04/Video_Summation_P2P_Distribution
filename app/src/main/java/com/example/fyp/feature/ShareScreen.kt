package com.example.fyp.feature

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp.core.network.P2pNetworkManager
import com.example.fyp.core.network.P2pEvent

@Composable
fun ShareScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)

    // grab username from Settings, default to "Unknown_Node" if empty
    val savedUsername = prefs.getString("username", "Unknown_Node") ?: "Unknown_Node"

    // Create a network manager instance
    val p2pManager = remember { P2pNetworkManager(context) }

    var isSharing by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }

    // Collect P2P events and show toasts
    LaunchedEffect(p2pManager) {
        p2pManager.p2pEvents.collect { event ->
            val message = when (event) {
                P2pEvent.PEER_CONNECTED -> "Connected to a Peer"
                P2pEvent.VIDEO_SENT -> "Video sent to Peer"
                P2pEvent.VIDEO_RECEIVED -> "Video received from Peer"
                P2pEvent.ECHO_BLOCKED -> "Own video not received"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Permissions
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ (API 33+)
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12 (API 31 and 32)
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        // Android 11 and below
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    //  Android permission dialog launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        // check if permissions granted
        val allGranted = permissionsMap.values.all { it }
        permissionsGranted = allGranted
        if (allGranted && !isSharing) {
            isSharing = true
            p2pManager.startP2p(savedUsername)
        }
    }

    // stop advertising/discovering when not on screen
    DisposableEffect(Unit) {
        onDispose {
            p2pManager.stopP2p()
        }
    }

    // UI
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Optimised Pub-Sub Network",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isSharing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Advertising & Discovering Peers", color = MaterialTheme.colorScheme.primary)
                Text("Stay on this screen to transfer videos.")
            } else {
                Button(onClick = {
                    if (permissionsGranted) {
                        isSharing = true
                        p2pManager.startP2p(savedUsername)
                    } else {
                        // Launch the permission request
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }) {
                    Text(if (permissionsGranted) "Start Network" else "Grant Permissions & Start")
                }
            }
        }
    }
}