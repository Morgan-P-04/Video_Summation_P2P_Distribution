package com.example.fyp.feature

import android.Manifest
import android.os.Build
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

@Composable
fun ShareScreen() {
    val context = LocalContext.current

    // Create a network manager instance
    val p2pManager = remember { P2pNetworkManager(context) }

    var isSharing by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }

    // permissions based on the Android version
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
        val allGranted = permissionsMap.values.all { it == true }
        permissionsGranted = allGranted
        if (allGranted && !isSharing) {
            isSharing = true
            p2pManager.startP2p(username = "Node_${Build.MODEL}")
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
                        p2pManager.startP2p(username = "Node_${Build.MODEL}")
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