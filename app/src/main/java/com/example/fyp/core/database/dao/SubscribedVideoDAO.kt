package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.fyp.core.database.entities.SubscribedVideoEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface SubscribedVideoDao {
    @Query("SELECT * FROM subscribed_videos WHERE subscriberId = :subscriberId")
    fun getSubscribedVideos(subscriberId: String): Flow<List<SubscribedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscribedVideo(video: SubscribedVideoEntity)

    @Query("SELECT * FROM subscribed_videos ORDER BY receivedAt DESC")
    fun getMySubscribedVideos(): Flow<List<SubscribedVideoEntity>>

    @Delete
    suspend fun deleteSubscribedVideo(video: SubscribedVideoEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM subscribed_videos WHERE videoId = :videoId)")
    suspend fun doesVideoExist(videoId: String): Boolean
}