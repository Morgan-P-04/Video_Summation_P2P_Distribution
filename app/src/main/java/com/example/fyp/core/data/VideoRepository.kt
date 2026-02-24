package com.example.fyp.core.data


import com.example.fyp.core.database.dao.PublishedVideoDao
import com.example.fyp.core.database.dao.SubscribedVideoDao
import com.example.fyp.core.database.entities.PublishedVideoEntity
import com.example.fyp.core.database.entities.SubscribedVideoEntity
import kotlinx.coroutines.flow.Flow

class VideoRepository(
    private val publishedVideoDao: PublishedVideoDao,
    private val subscribedVideoDao: SubscribedVideoDao
) {
    // Get streams of data that update automatically
    val myVideos: Flow<List<PublishedVideoEntity>> = publishedVideoDao.getMyPublishedVideos()
    val subscribedVideos: Flow<List<SubscribedVideoEntity>> = subscribedVideoDao.getMySubscribedVideos()

    suspend fun savePublishedVideo(video: PublishedVideoEntity) {
        publishedVideoDao.insertVideo(video)
    }

    suspend fun deletePublishedVideo(video: PublishedVideoEntity) {
        publishedVideoDao.deleteVideo(video)
    }

    suspend fun deleteSubscribedVideo(video: SubscribedVideoEntity) {
        subscribedVideoDao.deleteSubscribedVideo(video)
    }

    suspend fun updatePublishedVideo(video: PublishedVideoEntity) {
        publishedVideoDao.updatePublishedVideo(video)
    }

}