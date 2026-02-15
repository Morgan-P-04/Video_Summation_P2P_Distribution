package com.example.fyp.feature

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp.core.util.VideoSplicer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State to hold our list of recorded snippets
    var videoFiles by remember { mutableStateOf(listOf<File>()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Helper function to refresh the file list
    fun refreshFiles() {
        val directory = context.filesDir
        videoFiles = directory.listFiles { file ->
            file.extension == "mp4"
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    // Load files when the screen opens
    LaunchedEffect(Unit) {
        refreshFiles()
    }

    Scaffold(
        floatingActionButton = {
            // Only show button if we have at least 2 snippets to join
            val snippets = videoFiles.filter { it.name.startsWith("snippet_") }
            if (snippets.size >= 2 && !isProcessing) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val out = File(context.filesDir, "final_stitched_${System.currentTimeMillis()}.mp4")
                            val success = VideoSplicer.appendVideos(snippets, out)
                            isProcessing = false

                            if (success) {
                                Toast.makeText(context, "Video Stitched!", Toast.LENGTH_SHORT).show()
                                refreshFiles()
                            } else {
                                Toast.makeText(context, "Stitching failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                    text = { Text("Stitch Snippets") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isProcessing) {
                // Show loading overlay while stitching
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Merging Videos... please wait")
                }
            } else {
                // Main List UI
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = "Your Snippets",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (videoFiles.isEmpty()) {
                        Text("No snippets recorded yet. Go to 'Capture' to start!")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(videoFiles) { file ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = if (file.name.endsWith(".mp4"))
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    else CardDefaults.cardColors()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (file.name.startsWith("final_")) "Final Video" else file.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "${file.length() / 1024} KB",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}