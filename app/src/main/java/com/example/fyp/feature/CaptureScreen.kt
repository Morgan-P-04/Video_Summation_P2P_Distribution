package com.example.fyp.feature

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. Permission Handling
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    if (permissionsState.allPermissionsGranted) {
        CameraContent(context, lifecycleOwner)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera and Audio permissions are needed to record snippets.")
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CameraContent(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
    // CameraX Variables
    val previewView = remember { PreviewView(context) }
    val videoCapture = remember {
        // force 480p
        val qualitySelector = QualitySelector.from(Quality.HD,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        VideoCapture.withOutput(recorder)
    }

    // State
    var isRecording by remember { mutableStateOf(false) }
    var currentRecording: Recording? by remember { mutableStateOf(null) }
    var progress by remember { mutableStateOf(0f) } // For the 10s progress bar

    // Setup Camera
    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                videoCapture
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Camera init failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // UI Layout
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Overlay Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRecording) {
                // Progress Bar for 10s limit
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    color = Color.Red
                )
                Text("Recording...", color = Color.White)
            }

            // Record Button
            IconButton(
                onClick = {
                    if (isRecording) {
                        // STOP Recording
                        currentRecording?.stop()
                        isRecording = false
                        progress = 0f
                    } else {
                        // START Recording
                        val videoFile = File(context.filesDir, "snippet_${System.currentTimeMillis()}.mp4")
                        val outputOptions = FileOutputOptions.Builder(videoFile).build()

                        currentRecording = videoCapture.output
                            .prepareRecording(context, outputOptions)
                            .withAudioEnabled()
                            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                when (recordEvent) {
                                    is VideoRecordEvent.Start -> {
                                        isRecording = true
                                    }
                                    is VideoRecordEvent.Status -> {
                                        // Update 10s Timer Logic
                                        val nanos = recordEvent.recordingStats.recordedDurationNanos
                                        val seconds = nanos / 1_000_000_000.0
                                        progress = (seconds / 10.0).toFloat()

                                        if (seconds >= 10.0) {
                                            currentRecording?.stop() // Auto-stop at 10s
                                        }
                                    }
                                    is VideoRecordEvent.Finalize -> {
                                        isRecording = false
                                        progress = 0f
                                        if (!recordEvent.hasError()) {
                                            Toast.makeText(context, "Saved: ${videoFile.absolutePath}", Toast.LENGTH_LONG).show()
                                        } else {
                                            currentRecording?.close()
                                            currentRecording = null
                                        }
                                    }
                                }
                            }
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(if (isRecording) Color.Red else Color.White, CircleShape)
            ) {
                Icon(Icons.Default.Videocam, contentDescription = "Record", tint = Color.Black)
            }
        }
    }
}