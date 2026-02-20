package com.example.fyp.feature

import android.app.Application
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
import androidx.lifecycle.viewmodel.compose.viewModel
import android.media.MediaMetadataRetriever
import com.example.fyp.core.util.VideoSplicer
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel method
    val application = context.applicationContext as Application
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(application)
    )

    // get DB state
    val publishedVideos by viewModel.myVideos.collectAsState()

    // get local file (snippets) state
    var snippetFiles by remember { mutableStateOf(listOf<File>()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Dialog state
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }

    // Helper function --> get only unstitched snippets from DB
    fun refreshSnippets() {
        val directory = context.filesDir
        snippetFiles = directory.listFiles { file ->
            file.extension == "mp4" && !file.name.startsWith("final_")
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshSnippets()
    }

    Scaffold(
        floatingActionButton = {
            if (snippetFiles.size >= 2 && !isProcessing) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isProcessing = true
                        scope.launch {
                            val out = File(context.filesDir, "final_stitched_${System.currentTimeMillis()}.mp4")
                            // Pass ONLY the snippets to splicer
                            val success = VideoSplicer.appendVideos(snippetFiles, out)
                            isProcessing = false

                            if (success) {
                                Toast.makeText(context, "Video Stitched & Saved to DB!", Toast.LENGTH_SHORT).show()

                                // get cumulative duration of snippets
                                var actualDurationSeconds = 0
                                val retriever = MediaMetadataRetriever()
                                try {
                                    // Point the retriever at stitched video
                                    retriever.setDataSource(out.absolutePath)
                                    val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    val timeInMillis = timeString?.toLong() ?: 0L

                                    // Convert milliseconds to seconds
                                    actualDurationSeconds = (timeInMillis / 1000).toInt()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    retriever.release() // free memory
                                }

                                // save to DB
                                viewModel.saveVideoToDb(
                                    localPath = out.absolutePath,
                                    duration = actualDurationSeconds
                                )

                                refreshSnippets()
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

                    // local storage
                    Text(
                        text = "Video Snippets",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (snippetFiles.isEmpty()) {
                        Text("No snippets recorded. Go to 'Capture' to start!", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f), // half the screen
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(snippetFiles) { file ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(text = "${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                        }
                                        IconButton(onClick = { fileToDelete = file }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Room DB
                    Text(
                        text = "My Published Videos",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (publishedVideos.isEmpty()) {
                        Text("No final videos yet. Splice some snippets!", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f), // Takes up the other half
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(publishedVideos) { dbVideo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Video ID: ${dbVideo.videoId.take(8)}...",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(text = "Duration: ${dbVideo.duration}s", style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Path: ${dbVideo.localPath.takeLast(25)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Delete Confirmation Dialog
            fileToDelete?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    title = { Text("Delete Snippet?") },
                    text = { Text("Are you sure you want to delete ${file.name}?") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (file.delete()) refreshSnippets()
                            fileToDelete = null
                        }) { Text("Delete", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}