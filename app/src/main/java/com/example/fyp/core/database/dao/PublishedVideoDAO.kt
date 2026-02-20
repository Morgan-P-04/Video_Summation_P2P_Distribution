package com.example.fyp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.fyp.core.database.entities.PublishedVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PublishedVideoDao {
    @Query("SELECT * FROM published_videos ORDER BY createdAt DESC")
    fun getMyPublishedVideos(): Flow<List<PublishedVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PublishedVideoEntity)
}