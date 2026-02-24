package com.example.fyp.feature

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.fyp.core.database.entities.PublishedVideoEntity
import com.example.fyp.core.util.VideoSplicer
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fyp.core.database.entities.SubscribedVideoEntity

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
    val subscribedVideos by viewModel.subscribedVideos.collectAsState()

    // get local file (snippets) state
    var snippetFiles by remember { mutableStateOf(listOf<File>()) }
    var isProcessing by remember { mutableStateOf(false) }

    // Dialog state
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var dbVideoToDelete by remember { mutableStateOf<PublishedVideoEntity?>(null) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }
    var videoPathToPlay by remember { mutableStateOf<String?>(null) }
    var receivedVideoToDelete by remember { mutableStateOf<SubscribedVideoEntity?>(null) }

    // Track selected topic for splicing
    var selectedPublishTopic by remember { mutableStateOf(1) } // Default to General
    var showTopicDropdown by remember { mutableStateOf(false) }

    // Get active subscriptions from ViewModel
    val activeSubscriptions by viewModel.activeSubscriptions.collectAsState()

    // Helper function --> get only unstitched snippets from DB
    fun refreshSnippets() {
        val directory = context.filesDir
        snippetFiles = directory.listFiles { file ->
            file.extension == "mp4" && !file.name.startsWith("final_") && !file.name.startsWith("received_")
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshSnippets()
    }

    Scaffold(
        floatingActionButton = {
            if (snippetFiles.size >= 2 && !isProcessing) {
                // topic dropdown above action button
                Column(horizontalAlignment = Alignment.End) {
                    Box {
                        OutlinedButton(
                            onClick = { showTopicDropdown = true },
                            modifier = Modifier.padding(bottom = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Text("Topic: ${predefinedTopics[selectedPublishTopic]}")
                        }
                        DropdownMenu(
                            expanded = showTopicDropdown,
                            onDismissRequest = { showTopicDropdown = false }
                        ) {
                            predefinedTopics.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedPublishTopic = id
                                        showTopicDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    ExtendedFloatingActionButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val out = File(context.filesDir, "final_stitched_${System.currentTimeMillis()}.mp4")
                                // Pass ONLY the snippets to splicer, reversed so the oldest is the fist clip when spliced
                                val success = VideoSplicer.appendVideos(snippetFiles.reversed(), out)
                                isProcessing = false

                                if (success) {
                                    Toast.makeText(context, "Video Spliced & Saved to DB!", Toast.LENGTH_SHORT).show()

                                    // get cumulative duration of snippets
                                    var actualDurationSeconds = 0
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(out.absolutePath)
                                        val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                        val timeInMillis = timeString?.toLong() ?: 0L
                                        actualDurationSeconds = (timeInMillis / 1000).toInt()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        retriever.release()
                                    }

                                    // pass selected topic to the DB
                                    viewModel.saveVideoToDb(
                                        localPath = out.absolutePath,
                                        duration = actualDurationSeconds,
                                        topicId = selectedPublishTopic
                                    )

                                    refreshSnippets()
                                } else {
                                    Toast.makeText(context, "Splicing failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                        text = { Text("Splice Snippets") }
                    )
                }
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
                // single LazyColumn for the entire screen
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // Bottom padding clears splice button
                    verticalArrangement = Arrangement.spacedBy(12.dp) // even spacing
                ) {
                    // snippets
                    item {
                        // local storage
                        Text(
                            text = "Video Snippets",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                        )
                    }
                    if (snippetFiles.isEmpty()) {
                        item { Text("No snippets recorded. Go to 'Capture' to start!", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(snippetFiles) { file ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { videoPathToPlay = file.absolutePath }) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = file.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(text = "${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = {
                                        fileToRename = file
                                        renameTextFieldValue = file.nameWithoutExtension
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                                    }
                                    IconButton(onClick = { fileToDelete = file }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }

                    // published videos
                    item {
                        Text(
                            text = "My Published Videos",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    if (publishedVideos.isEmpty()) {
                        item { Text("No final videos yet. Splice some snippets!", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(publishedVideos) { dbVideo ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { videoPathToPlay = dbVideo.localPath },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Topic: ${predefinedTopics[dbVideo.topicId]}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(text = "Duration: ${dbVideo.duration}s", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    // trash can icon for DB videos
                                    IconButton(onClick = { dbVideoToDelete = dbVideo }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Video", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }

                    // divider
                    item { Divider(modifier = Modifier.padding(vertical = 8.dp)) }

                    // received videos
                    item {
                        Text(
                            text = "Received Videos (Pub-Sub)",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(predefinedTopics.toList()) { (id, name) ->
                                FilterChip(
                                    selected = activeSubscriptions.contains(id),
                                    onClick = { viewModel.toggleSubscription(id) },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }

                    if (subscribedVideos.isEmpty()) {
                        item { Text("No received videos yet or no matching subscriptions.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(subscribedVideos) { subVideo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { videoPathToPlay = context.filesDir.absolutePath + "/received_" + subVideo.videoId + ".mp4" },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Topic: ${predefinedTopics[subVideo.topicId]}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(text = "From Peer: ${subVideo.sourcePeerId.takeLast(6)}", style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Creator Peer ID: ${subVideo.subscriberId.take(7)}", style = MaterialTheme.typography.bodyMedium)
                                        Text(text = "Delivered: ${subVideo.deliveryState}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { receivedVideoToDelete = subVideo }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Received Video", tint = Color.Red)
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
            // Delete Confirmation Dialog for DB Videos
            dbVideoToDelete?.let { video ->
                AlertDialog(
                    onDismissRequest = { dbVideoToDelete = null },
                    title = { Text("Delete Daily Highlight?") },
                    text = { Text("Are you sure you want to permanently delete this spliced video?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteVideo(video)
                            dbVideoToDelete = null
                        }) { Text("Delete", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dbVideoToDelete = null }) { Text("Cancel") }
                    }
                )
            }
            // Rename Dialog for Snippets
            fileToRename?.let { file ->
                AlertDialog(
                    onDismissRequest = { fileToRename = null },
                    title = { Text("Rename Snippet") },
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
                            // Create a new File object with the updated name
                            val newFile = File(context.filesDir, "$renameTextFieldValue.mp4")

                            if (file.renameTo(newFile)) {
                                refreshSnippets() // Refresh the UI list
                                Toast.makeText(context, "Renamed!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                            fileToRename = null // Close dialog
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
                    }
                )
            }

            // Delete Confirmation Dialog for received videos
            receivedVideoToDelete?.let { video ->
                AlertDialog(
                    onDismissRequest = { receivedVideoToDelete = null },
                    title = { Text("Delete Received Video?") },
                    text = { Text("Are you sure you want to permanently delete this received video from your device?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteReceivedVideo(video)
                            receivedVideoToDelete = null
                        }) { Text("Delete", color = Color.Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { receivedVideoToDelete = null }) { Text("Cancel") }
                    }
                )
            }

            // video playback overlay
            videoPathToPlay?.let { path ->
                Dialog(
                    onDismissRequest = { videoPathToPlay = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false, // Allows full screen
                        dismissOnClickOutside = true
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        VideoPlayer(
                            videoPath = path,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Close Button in the top right
                        IconButton(
                            onClick = { videoPathToPlay = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}