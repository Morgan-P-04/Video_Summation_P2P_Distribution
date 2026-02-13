package com.example.fyp.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Main : Screen("main", "Main", Icons.Default.Home)
    object Capture : Screen("capture", "Capture", Icons.Default.AddCircle)
    object Share : Screen("share", "Share", Icons.Default.Share)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}