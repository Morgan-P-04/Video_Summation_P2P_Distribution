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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

val predefinedTopics = mapOf(
    1 to "General", // daily highlight videos
    2 to "Sports",
    3 to "Tech",
    4 to "Campus Life"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as MainApplication).database
    private val repository = VideoRepository(db.publishedVideoDao(), db.subscribedVideoDao())

    // track which topics the user is currently subscribed to (Default: All)
    private val _activeSubscriptions = MutableStateFlow(setOf(1, 2, 3, 4))
    val activeSubscriptions: StateFlow<Set<Int>> = _activeSubscriptions.asStateFlow()

    fun toggleSubscription(topicId: Int) {
        _activeSubscriptions.value = if (_activeSubscriptions.value.contains(topicId)) {
            _activeSubscriptions.value - topicId // Unsubscribe
        } else {
            _activeSubscriptions.value + topicId // Subscribe
        }
    }

    // Created Videos
    val myVideos: StateFlow<List<PublishedVideoEntity>> = repository.myVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Received / Subscribed Videos
    val subscribedVideos: StateFlow<List<SubscribedVideoEntity>> = repository.subscribedVideos
        .combine(_activeSubscriptions) { videos, subs ->
            videos.filter { subs.contains(it.topicId) } // subscribed topics shown
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Function to save a new stitched video (accepts topicId)
    fun saveVideoToDb(localPath: String, duration: Int, topicId: Int) {
        viewModelScope.launch {
            val newVideo = PublishedVideoEntity(
                videoId = UUID.randomUUID().toString(),
                topicId = topicId, // use selected topic
                userId = "me", // TODO: update with actual UID
                duration = duration,
                createdAt = System.currentTimeMillis(),
                shareCount = 0,
                localPath = localPath
            )
            repository.savePublishedVideo(newVideo)
        }
    }
    // Function to delete stitched video
    fun deleteVideo(video: PublishedVideoEntity) {
        viewModelScope.launch {
            val file = java.io.File(video.localPath)
            if (file.exists()) file.delete()
            repository.deletePublishedVideo(video)
        }
    }
    // Function to remove a received video and its file
    fun deleteReceivedVideo(video: SubscribedVideoEntity) {
        viewModelScope.launch {
            val file = java.io.File(getApplication<Application>().filesDir, "received_${video.videoId}.mp4")
            if (file.exists()) file.delete()
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