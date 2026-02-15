package com.example.fyp.feature

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fyp.core.util.VideoSplicer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State management
    var videoFiles by remember { mutableStateOf(listOf<File>()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Dialog state for Delete and Rename
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }

    fun refreshFiles() {
        val directory = context.filesDir
        videoFiles = directory.listFiles { file ->
            file.extension == "mp4"
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshFiles()
    }

    Scaffold(
        floatingActionButton = {
            // Stitch anything that is NOT already a "final" video
            val snippets = videoFiles.filter { !it.name.startsWith("final_") }
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
                                    colors = if (file.name.startsWith("final_"))
                                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    else CardDefaults.cardColors()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (file.name.startsWith("final_")) "Final Video" else file.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "${file.length() / 1024} KB",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        // Rename Button
                                        IconButton(onClick = {
                                            fileToRename = file
                                            renameTextFieldValue = file.nameWithoutExtension
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                                        }

                                        // Delete Button
                                        IconButton(onClick = { fileToDelete = file }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Delete Confirmation Dialog ---
            fileToDelete?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    title = { Text("Confirm Deletion") },
                    text = { Text("Are you sure you want to delete ${file.name}?") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (file.delete()) {
                                refreshFiles()
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            }
                            fileToDelete = null
                        }) { Text("Delete", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                    }
                )
            }

            // --- Rename Dialog ---
            fileToRename?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToRename = null },
                    title = { Text("Rename File") },
                    text = {
                        OutlinedTextField(
                            value = renameTextFieldValue,
                            onValueChange = { renameTextFieldValue = it },
                            label = { Text("New file name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val newFile = File(context.filesDir, "$renameTextFieldValue.mp4")
                            if (file.renameTo(newFile)) {
                                refreshFiles()
                                Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                            }
                            fileToRename = null
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}