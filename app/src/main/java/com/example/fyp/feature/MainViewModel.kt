package com.example.fyp.feature

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fyp.MainApplication
import com.example.fyp.core.data.VideoRepository
import com.example.fyp.core.database.entities.PublishedVideoEntity
import com.example.fyp.core.database.entities.SubscribedVideoEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as MainApplication).database
    private val repository = VideoRepository(db.publishedVideoDao(), db.subscribedVideoDao())

    // Created Videos
    val myVideos: StateFlow<List<PublishedVideoEntity>> = repository.myVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Received / Subscribed Videos
    val subscribedVideos: StateFlow<List<SubscribedVideoEntity>> = repository.subscribedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Function to save a new stitched video
    fun saveVideoToDb(localPath: String, duration: Int) {
        viewModelScope.launch {
            val newVideo = PublishedVideoEntity(
                videoId = UUID.randomUUID().toString(), // Generate unique ID
                topicId = 1, // Default topic TODO update with actual topic
                userId = "me", // Placeholder user ID TODO update with actual UID
                duration = duration,
                createdAt = System.currentTimeMillis(),
                shareCount = 0,
                localPath = localPath // store the PATH, not the video itself
            )
            repository.savePublishedVideo(newVideo)
        }
    }
    // Function to delete stitched video
    fun deleteVideo(video: PublishedVideoEntity) {
        viewModelScope.launch {
            // remove from internal storage
            val file = java.io.File(video.localPath)
            if (file.exists()) {
                file.delete()
            }
            // remove from DB
            repository.deletePublishedVideo(video)
        }
    }
    // Function to remove a received video and its file
    fun deleteReceivedVideo(video: SubscribedVideoEntity) {
        viewModelScope.launch {
            // reconstruct the file path and delete
            val file = java.io.File(getApplication<Application>().filesDir, "received_${video.videoId}.mp4")
            if (file.exists()) {
                file.delete()
            }

            // delete the record from Room DB
            repository.deleteSubscribedVideo(video)
        }
    }
}

// Factory to create the ViewModel with the Application context
class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}