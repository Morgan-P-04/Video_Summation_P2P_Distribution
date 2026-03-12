package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fyp.core.database.entities.PublishedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublishedVideoDao {
    @Query("SELECT * FROM published_videos ORDER BY createdAt DESC")
    fun getMyPublishedVideos(): Flow<List<PublishedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PublishedVideoEntity)

    @Delete
    suspend fun deleteVideo(video: PublishedVideoEntity)

    @Update
    suspend fun updatePublishedVideo(video: PublishedVideoEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM published_videos WHERE videoId = :videoId)")
    suspend fun doesVideoExist(videoId: String): Boolean
}