package com.example.fyp.feature

import android.app.Application
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

val predefinedTopics = mapOf(
    1 to "Daily Highlight",
    2 to "Sports",
    3 to "Tech",
    4 to "Campus Life",
    5 to "Cooking"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as MainApplication).database
    private val repository = VideoRepository(db.publishedVideoDao(), db.subscribedVideoDao())

    // get persistent publisherID and shared preferences
    private val prefs = application.getSharedPreferences("p2p_prefs", Context.MODE_PRIVATE)
    private val localNodeId: String = prefs.getString("node_id", null) ?: run {
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString("node_id", newId).apply()
        newId
    }

    // track which topics the user is currently subscribed to (Default: All)
    private val savedSubsStrings = prefs.getStringSet("active_subs", setOf("1", "2", "3", "4", "5"))
    private val initialSubs = savedSubsStrings?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(1, 2, 3, 4, 5)

    private val _activeSubscriptions = MutableStateFlow(initialSubs)
    val activeSubscriptions: StateFlow<Set<Int>> = _activeSubscriptions.asStateFlow()

    fun toggleSubscription(topicId: Int) {
        val currentSubs = _activeSubscriptions.value
        val newSubs = if (currentSubs.contains(topicId)) {
            currentSubs - topicId // Unsubscribe
        } else {
            currentSubs + topicId // Subscribe
        }

        _activeSubscriptions.value = newSubs

        // Save the new set immediately to SharedPreferences
        prefs.edit().putStringSet("active_subs", newSubs.map { it.toString() }.toSet()).apply()
    }

    // Created Videos
    val myVideos: StateFlow<List<PublishedVideoEntity>> = repository.myVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Received / Subscribed Videos (filtered by active subscriptions)
    val subscribedVideos: StateFlow<List<SubscribedVideoEntity>> = repository.subscribedVideos
        .combine(_activeSubscriptions) { videos, subs ->
            videos.filter { subs.contains(it.topicId) } // subscribed topics shown
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Run the TTL cleanup automatically when the ViewModel is created (App Startup)
    init {
        purgeExpiredVideos()
    }

    //  24-hour TTL purge for Snippets and Received videos (DB & File System)
    private fun purgeExpiredVideos() {
        viewModelScope.launch {
            val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L
            val thresholdTime = System.currentTimeMillis() - twentyFourHoursInMillis

            try {
                // snippets (File System)
                val filesDir = getApplication<Application>().filesDir
                val snippetFiles = filesDir.listFiles { file ->
                    file.extension == "mp4" &&
                            !file.name.startsWith("final_") &&
                            !file.name.startsWith("received_")
                }

                snippetFiles?.forEach { file ->
                    if (file.lastModified() < thresholdTime) {
                        Log.d("TTL_PURGE", "Deleting expired snippet: ${file.name}")
                        file.delete()
                    }
                }

                // check received P2P videos (Database & File System)
                val received = repository.subscribedVideos.first()
                received.forEach { video ->
                    if (video.receivedAt < thresholdTime) {
                        Log.d("TTL_PURGE", "Deleting expired received video: ${video.videoId}")
                        deleteReceivedVideo(video) // deletes both the DB row and the file
                    }
                }
            } catch (e: Exception) {
                Log.e("TTL_PURGE", "Failed to run TTL cleanup: ${e.message}")
            }
        }
    }

    // Function to save a new spliced video (accepts topicID)
    fun saveVideoToDb(localPath: String, duration: Int, topicId: Int) {
        viewModelScope.launch {
            val newVideo = PublishedVideoEntity(
                videoId = UUID.randomUUID().toString(),
                title = "Untitled Highlight",
                topicId = topicId, // use selected topic
                userId = localNodeId, // persistent publisherID
                duration = duration,
                createdAt = System.currentTimeMillis(),
                shareCount = 0,
                localPath = localPath
            )
            repository.savePublishedVideo(newVideo)
        }
    }
    // Function to update the title of a spliced video
    fun updateVideoTitle(video: PublishedVideoEntity, newTitle: String) {
        viewModelScope.launch {
            // .copy() creates a duplicate of the object with just the title changed!
            repository.updatePublishedVideo(video.copy(title = newTitle))
        }
    }

    // Function to delete spliced video (both DB and file)
    fun deleteVideo(video: PublishedVideoEntity) {
        viewModelScope.launch {
            val file = java.io.File(video.localPath)
            if (file.exists()) file.delete()
            repository.deletePublishedVideo(video)
        }
    }

    // Function to remove a received video (both DB and file)
    fun deleteReceivedVideo(video: SubscribedVideoEntity) {
        viewModelScope.launch {
            val file = java.io.File(getApplication<Application>().filesDir, "received_${video.videoId}.mp4")
            if (file.exists()) file.delete()
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